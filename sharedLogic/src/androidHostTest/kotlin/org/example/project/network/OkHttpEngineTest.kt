package org.example.project.network

import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import okhttp3.ConnectionPool
import okhttp3.Dns
import org.example.project.core.config.AppEnvironment
import org.example.project.core.config.AppInfo
import org.example.project.core.config.NetworkConfig
import org.example.project.core.result.ApiResult
import org.example.project.core.result.DataError
import org.example.project.core.result.TimeoutKind
import org.example.project.di.createOkHttpEngine
import java.io.BufferedInputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The real OkHttp engine against a real socket: what MockEngine cannot show.
 * OkHttp has its own retry logic below ours, and its own exceptions.
 */
class OkHttpEngineTest {

    private val config = NetworkConfig(
        environment = AppEnvironment.Dev,
        appInfo = AppInfo(platform = "android", appVersion = "1.0", languageTag = "en"),
        connectTimeoutMillis = 2_000,
        socketTimeoutMillis = 5_000,
        requestTimeoutMillis = 5_000,
    )

    private fun executor(config: NetworkConfig = this.config) = testExecutor(createOkHttpEngine(), config)

    private fun errorOf(result: ApiResult<*>) = assertIs<ApiResult.Failure>(result).error

    @Test
    fun aPostIsSentOnlyOnceWhenItsReusedConnectionDrops() {
        // Connection 1 answers a GET, then drops the POST that reuses it. Any resend would be answered.
        RawServer { request, socket ->
            if (request == "POST" && requests.count { it == "POST" } == 1) socket.close() else socket.answerOk()
        }.use { server ->
            runBlocking {
                val executor = executor()
                assertIs<ApiResult.Success<*>>(executor.get<List<String>>(server.url))

                val result = executor.execute<List<String>>(HttpMethod.Post, server.url) { setBody("order") }

                assertEquals(1, server.requests.count { it == "POST" }, "the order reached the server more than once")
                assertIs<DataError.ConnectionLost>(errorOf(result))
            }
        }
    }

    @Test
    fun aHostWithOneDeadAddressStillConnects() {
        // The cost of turning OkHttp's retry off would be losing this fallback.
        RawServer { _, socket -> socket.answerOk() }.use { server ->
            val deadThenAlive = listOf("127.0.0.2", "127.0.0.1").map(InetAddress::getByName)
            val engine = createOkHttpEngine { dns(object : Dns { override fun lookup(hostname: String) = deadThenAlive }) }
            runBlocking {
                val result = testExecutor(engine, config).get<List<String>>(server.url.replace("127.0.0.1", "two-addresses.test"))
                assertIs<ApiResult.Success<*>>(result, "$result")
            }
        }
    }

    @Test
    fun droppingIdleConnectionsStopsADeadOneBeingReused() {
        // After a network change the old connection looks open but never answers again.
        val connectionOfTheGet = CopyOnWriteArrayList<Socket>()
        RawServer { _, socket ->
            if (connectionOfTheGet.isEmpty() || socket !in connectionOfTheGet) {
                connectionOfTheGet += socket
                socket.answerOk()
            }
        }.use { server ->
            runBlocking {
                val pool = ConnectionPool()
                val executor = testExecutor(createOkHttpEngine(pool), config.copy(socketTimeoutMillis = 500))
                assertIs<ApiResult.Success<*>>(executor.get<List<String>>(server.url))
                assertEquals(1, pool.connectionCount())

                pool.evictAll()

                assertIs<ApiResult.Success<*>>(executor.get<List<String>>(server.url))
            }
        }
    }

    @Test
    fun aSilentServerIsASocketTimeout() {
        RawServer { _, _ -> Thread.sleep(3_000) }.use { server ->
            runBlocking {
                val error = errorOf(executor(config.copy(socketTimeoutMillis = 300)).get<List<String>>(server.url))
                assertEquals(TimeoutKind.Socket, assertIs<DataError.Timeout>(error).kind)
            }
        }
    }

    @Test
    fun aServerThatKeepsDrippingIsARequestTimeout() {
        // Never silent for long, so only the whole-request limit can stop it.
        RawServer { _, socket ->
            socket.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 1000\r\n\r\n")
            repeat(40) { socket.write(" "); Thread.sleep(50) }
        }.use { server ->
            runBlocking {
                val error = errorOf(executor(config.copy(requestTimeoutMillis = 400)).get<List<String>>(server.url))
                assertEquals(TimeoutKind.Request, assertIs<DataError.Timeout>(error).kind)
            }
        }
    }

    @Test
    fun aRefusedConnectionIsConnectionLost() {
        val freePort = ServerSocket(0).use { it.localPort }
        runBlocking {
            assertIs<DataError.ConnectionLost>(errorOf(executor().get<List<String>>("http://127.0.0.1:$freePort/")))
        }
    }

    @Test
    fun aBodyCutOffHalfwayIsConnectionLost() {
        RawServer { _, socket ->
            socket.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 1000\r\n\r\n{\"code\":200,")
            socket.close()
        }.use { server ->
            runBlocking {
                assertIs<DataError.ConnectionLost>(errorOf(executor().get<List<String>>(server.url)))
            }
        }
    }
}

private fun Socket.write(text: String) {
    runCatching { getOutputStream().apply { write(text.toByteArray()); flush() } }
}

private fun Socket.answerOk() {
    val body = """{"code":200,"message":"","data":["a"]}"""
    write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\n\r\n$body")
}

/** A keep-alive HTTP/1.1 server that does exactly what [onRequest] says after reading each request. */
private class RawServer(private val onRequest: RawServer.(method: String, socket: Socket) -> Unit) : Closeable {
    val requests = CopyOnWriteArrayList<String>()
    private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val url = "http://127.0.0.1:${serverSocket.localPort}/v1/things"

    init {
        thread(isDaemon = true) {
            while (!serverSocket.isClosed) {
                val socket = runCatching { serverSocket.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { serve(socket) }
            }
        }
    }

    private fun serve(socket: Socket) = runCatching {
        val input = BufferedInputStream(socket.getInputStream())
        while (!socket.isClosed) {
            val method = input.readLine()?.substringBefore(' ') ?: break
            var contentLength = 0
            while (true) {
                val header = input.readLine()
                if (header.isNullOrEmpty()) break
                if (header.startsWith("Content-Length:", ignoreCase = true)) contentLength = header.substringAfter(':').trim().toInt()
            }
            repeat(contentLength) { input.read() }
            requests += method
            onRequest(method, socket)
        }
    }

    private fun BufferedInputStream.readLine(): String? {
        val line = StringBuilder()
        while (true) {
            val byte = read()
            if (byte == -1) return if (line.isEmpty()) null else line.toString()
            if (byte == '\n'.code) return line.toString().trimEnd('\r')
            line.append(byte.toChar())
        }
    }

    override fun close() = serverSocket.close()
}
