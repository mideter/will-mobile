package com.example.helloworld

import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TCP к серверу: отправка строк с \\n, приём входящих сообщений в фоне и колбэки в главный поток.
 */
class WillChatBridge {

    interface Listener {
        fun onPeerMessage(text: String)
        fun onError(message: String)
        fun onConnectionChanged(connected: Boolean)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var recvThread: Thread? = null
    private val stopping = AtomicBoolean(false)

    fun isConnected(): Boolean = synchronized(lock) {
        socket?.isConnected == true && !stopping.get()
    }

    fun connectDefaultServer(listener: Listener) {
        disconnectServer()
        stopping.set(false)

        val thread = Thread({
            try {
                val s = Socket()
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(DEFAULT_HOST, DEFAULT_PORT), CONNECT_TIMEOUT_MS)

                val w = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
                val r = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))

                synchronized(lock) {
                    if (stopping.get()) {
                        try {
                            s.close()
                        } catch (_: Exception) {
                        }
                        return@Thread
                    }
                    socket = s
                    writer = w
                    reader = r
                }

                post(listener) { onConnectionChanged(true) }
                recvLoop(r, listener)
            } catch (e: Exception) {
                if (!stopping.get()) {
                    post(listener) { onError(e.message ?: e.toString()) }
                    post(listener) { onConnectionChanged(false) }
                }
                cleanupLocked()
            } finally {
                synchronized(lock) {
                    if (recvThread === Thread.currentThread()) {
                        recvThread = null
                    }
                }
            }
        }, "will-recv")
        synchronized(lock) {
            recvThread = thread
        }
        thread.start()
    }

    fun sendLine(line: String) {
        try {
            synchronized(lock) {
                val w = writer ?: return
                w.write(line)
                w.write("\n")
                w.flush()
            }
        } catch (_: Exception) {
            disconnectServer()
        }
    }

    fun disconnectServer() {
        stopping.set(true)
        val t = synchronized(lock) {
            try {
                socket?.shutdownInput()
            } catch (_: Exception) {
            }
            try {
                socket?.close()
            } catch (_: Exception) {
            }
            socket = null
            writer = null
            reader = null
            recvThread
        }
        try {
            t?.join(8000)
        } catch (_: InterruptedException) {
        }
        synchronized(lock) {
            recvThread = null
        }
        stopping.set(false)
    }

    private fun recvLoop(r: BufferedReader, listener: Listener) {
        try {
            while (!stopping.get()) {
                val line = r.readLine() ?: break
                if (line.isEmpty()) continue
                post(listener) { onPeerMessage(line) }
            }
        } catch (_: Exception) {
            if (!stopping.get()) {
                post(listener) { onError("Соединение разорвано") }
            }
        } finally {
            cleanupLocked()
            post(listener) { onConnectionChanged(false) }
        }
    }

    private fun cleanupLocked() {
        synchronized(lock) {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
            socket = null
            writer = null
            reader = null
        }
    }

    private fun post(listener: Listener, block: Listener.() -> Unit) {
        mainHandler.post { listener.block() }
    }

    companion object {
        private const val DEFAULT_HOST = "83.217.202.145"
        private const val DEFAULT_PORT = 8080
        private const val CONNECT_TIMEOUT_MS = 15_000
    }
}
