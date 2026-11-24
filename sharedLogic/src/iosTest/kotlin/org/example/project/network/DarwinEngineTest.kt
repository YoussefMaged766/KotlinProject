package org.example.project.network

import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import org.example.project.core.config.AppEnvironment
import org.example.project.core.config.AppInfo
import org.example.project.core.config.NetworkConfig
import org.example.project.core.result.ApiResult
import org.example.project.core.result.DataError
import org.example.project.core.result.TimeoutKind
import org.example.project.di.createDarwinEngine
import platform.posix.usleep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The iOS twin of OkHttpEngineTest: the real NSURLSession engine against a real socket. */
class DarwinEngineTest {

    private val config = NetworkConfig(
        environment = AppEnvironment.Dev,
        appInfo = AppInfo(platform = "ios", appVersion = "1.0", languageTag = "en"),
        connectTimeoutMillis = 2_000,
        socketTimeoutMillis = 5_000,
        requestTimeoutMillis = 5_000,
    )

    private fun executor(config: NetworkConfig = this.config) = testExecutor(createDarwinEngine(), config)

    private fun errorOf(result: ApiResult<*>) = assertIs<ApiResult.Failure>(result, "$result").error

    @Test
    fun aPostIsSentOnlyOnceWhenItsReusedConnectionDrops() {
        // Connection 1 answers a GET, then drops the POST that reuses it. Any resend would be answered.
        withServer({ request, socket ->
            if (request == "POST" && requests.count { it == "POST" } == 1) socket.close() else socket.answerOk()
        }) { server ->
            val executor = executor()
            assertIs<ApiResult.Success<*>>(executor.get<List<String>>(server.url))

            val result = executor.execute<List<String>>(HttpMethod.Post, server.url) { setBody("order") }

            assertEquals(1, server.requests.count { it == "POST" }, "the order reached the server more than once")
            assertEquals(1, server.connections, "the POST should have reused the connection of the GET")
            assertIs<DataError.ConnectionLost>(errorOf(result))
        }
    }

    @Test
    fun aSilentServerIsASocketTimeout() {
        withServer({ _, _ -> usleep(3_000_000u) }) { server ->
            val error = errorOf(executor(config.copy(socketTimeoutMillis = 1_000)).get<List<String>>(server.url))
            assertEquals(TimeoutKind.Socket, assertIs<DataError.Timeout>(error).kind)
        }
    }

    @Test
    fun aServerThatKeepsDrippingIsARequestTimeout() {
        // Never silent for long, so only the whole-request limit can stop it.
        withServer({ _, socket ->
            socket.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 1000\r\n\r\n")
            repeat(40) { socket.write(" "); usleep(50_000u) }
        }) { server ->
            val error = errorOf(executor(config.copy(requestTimeoutMillis = 400)).get<List<String>>(server.url))
            assertEquals(TimeoutKind.Request, assertIs<DataError.Timeout>(error).kind)
        }
    }

    @Test
    fun aRefusedConnectionIsConnectionLost() {
        val refusedUrl = RawServer { _, _ -> }.also { it.close() }.url
        runBlocking {
            assertIs<DataError.ConnectionLost>(errorOf(executor().get<List<String>>(refusedUrl)))
        }
    }

    @Test
    fun aBodyCutOffHalfwayIsConnectionLost() {
        withServer({ _, socket ->
            socket.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 1000\r\n\r\n{\"code\":200,")
            socket.close()
        }) { server ->
            assertIs<DataError.ConnectionLost>(errorOf(executor().get<List<String>>(server.url)))
        }
    }

    private fun withServer(
        onRequest: RawServer.(method: String, socket: RawSocket) -> Unit,
        test: suspend (RawServer) -> Unit,
    ) {
        val server = RawServer(onRequest)
        try {
            runBlocking { test(server) }
        } finally {
            server.close()
        }
    }
}
