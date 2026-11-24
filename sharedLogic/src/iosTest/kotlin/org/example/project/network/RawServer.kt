@file:OptIn(ExperimentalForeignApi::class)

package org.example.project.network

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import platform.posix.AF_INET
import platform.posix.SHUT_RDWR
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_NOSIGPIPE
import platform.posix.accept
import platform.posix.bind
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.shutdown
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import platform.posix.close as closeDescriptor

/** A keep-alive HTTP/1.1 server that does exactly what [onRequest] says after reading each request. */
internal class RawServer(private val onRequest: RawServer.(method: String, socket: RawSocket) -> Unit) {
    /** [connection] counts from 1, in the order the server accepted them. */
    data class Request(val method: String, val connection: Int)

    private val received = MutableStateFlow(emptyList<Request>())
    val requests: List<Request> get() = received.value

    private val scope = CoroutineScope(Dispatchers.IO)
    private val serverFd = socket(AF_INET, SOCK_STREAM, 0)
    val url: String = "http://127.0.0.1:${bindToFreePort()}/v1/things"

    init {
        check(listen(serverFd, 50) == 0) { "listen failed" }
        scope.launch {
            var connection = 0
            while (true) {
                val fd = accept(serverFd, null, null)
                if (fd < 0) break                       // the server was closed
                val number = ++connection
                launch { serve(RawSocket(fd), number) }
            }
        }
    }

    private fun bindToFreePort(): Int = memScoped {
        val address = alloc<sockaddr_in>()
        address.sin_len = sizeOf<sockaddr_in>().convert()
        address.sin_family = AF_INET.convert()
        address.sin_port = 0u                           // the system picks a free port
        address.sin_addr.s_addr = 0x0100007Fu           // 127.0.0.1 in network byte order
        check(bind(serverFd, address.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == 0) { "bind failed" }

        val length = alloc<socklen_tVar>().apply { value = sizeOf<sockaddr_in>().convert() }
        check(getsockname(serverFd, address.ptr.reinterpret(), length.ptr) == 0) { "getsockname failed" }
        val port = address.sin_port.toInt()
        ((port and 0xFF) shl 8) or (port shr 8)         // network byte order -> Int
    }

    private fun serve(socket: RawSocket, connection: Int) {
        while (!socket.isClosed) {
            val method = socket.readLine()?.substringBefore(' ') ?: break
            var contentLength = 0
            while (true) {
                val header = socket.readLine()
                if (header.isNullOrEmpty()) break
                if (header.startsWith("Content-Length:", ignoreCase = true)) contentLength = header.substringAfter(':').trim().toInt()
            }
            repeat(contentLength) { socket.read() }
            received.update { it + Request(method, connection) }
            onRequest(method, socket)
        }
        socket.close()
    }

    fun close() {
        shutdown(serverFd, SHUT_RDWR)
        closeDescriptor(serverFd)
    }
}

/** One accepted connection, used only by the coroutine that serves it. */
internal class RawSocket(private val fd: Int) {
    var isClosed = false
        private set

    init {
        // Writing to a connection the client has left must be an error code, not a signal that kills the tests.
        memScoped {
            val on = alloc<IntVar>().apply { value = 1 }
            setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, on.ptr, sizeOf<IntVar>().convert())
        }
    }

    /** The next byte, or -1 when the client has gone. */
    fun read(): Int {
        val byte = ByteArray(1)
        val count = byte.usePinned { recv(fd, it.addressOf(0), 1u.convert(), 0) }
        return if (count.toInt() == 1) byte[0].toInt() and 0xFF else -1
    }

    fun readLine(): String? {
        val line = StringBuilder()
        while (true) {
            val byte = read()
            if (byte == -1) return if (line.isEmpty()) null else line.toString()
            if (byte == '\n'.code) return line.toString().trimEnd('\r')
            line.append(byte.toChar())
        }
    }

    fun write(text: String) {
        val bytes = text.encodeToByteArray()
        bytes.usePinned { send(fd, it.addressOf(0), bytes.size.convert(), 0) }
    }

    fun answerOk() {
        val body = """{"code":200,"message":"","data":["a"]}"""
        write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\n\r\n$body")
    }

    fun close() {
        if (isClosed) return
        isClosed = true
        closeDescriptor(fd)
    }
}
