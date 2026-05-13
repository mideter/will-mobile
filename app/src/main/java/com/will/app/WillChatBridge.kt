package com.will.app

import android.os.Handler
import android.os.Looper
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TCP к серверу Will: тот же кадровый протокол, что у `TcpFrame` в проекте will —
 * **4 байта длины пейлоада (big-endian, uint32)** и **сырой UTF-8** без завершающего `\n`.
 * Одна строка в UI чата отправляется и приходит как **один кадр**; приём — в потоке `will-recv`, колбэки — на главном потоке.
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
    private var dataOut: DataOutputStream? = null
    private var dataIn: DataInputStream? = null
    private var recvThread: Thread? = null
    private val stopping = AtomicBoolean(false)
    private val sendExecutor = Executors.newSingleThreadExecutor { Thread(it, "will-send") }

    /** Тот же [Listener], что передали в последний успешный/ожидающий connect — для ошибок отправки с фона. */
    private var callbacks: Listener? = null

    private val headerScratch = ByteArray(4)

    fun isConnected(): Boolean = synchronized(lock) {
        socket?.isConnected == true && !stopping.get()
    }

    fun connectDefaultServer(listener: Listener) {
        disconnectServer()
        stopping.set(false)
        callbacks = listener

        val thread = Thread({
            try {
                val s = Socket()
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(DEFAULT_HOST, DEFAULT_PORT), CONNECT_TIMEOUT_MS)
                s.keepAlive = true

                val socketIn = DataInputStream(s.getInputStream())
                val socketOut = DataOutputStream(s.getOutputStream())

                synchronized(lock) {
                    if (stopping.get()) {
                        try {
                            s.close()
                        } catch (_: Exception) {
                        }
                        return@Thread
                    }
                    socket = s
                    dataIn = socketIn
                    dataOut = socketOut
                }

                post(listener) { onConnectionChanged(true) }
                recvLoop(socketIn, listener)
            } catch (e: Exception) {
                if (!stopping.get()) {
                    post(listener) { onError(e.message ?: e.toString()) }
                    post(listener) { onConnectionChanged(false) }
                }
                cleanupLocked()
                callbacks = null
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
        // Сеть на UI-потоке даёт StrictMode → NetworkOnMainThreadException → сокет считают «сломанным».
        sendExecutor.execute {
            try {
                val bytes = line.toByteArray(Charsets.UTF_8)
                if (bytes.size > MAX_PAYLOAD_BYTES) {
                    val cb = callbacks
                    if (cb != null && !stopping.get()) {
                        post(cb) {
                            onError("Сообщение длиннее допустимого для протокола ($MAX_PAYLOAD_BYTES байт)")
                        }
                    }
                    return@execute
                }
                synchronized(lock) {
                    val out = dataOut ?: return@execute
                    out.writeInt(bytes.size)
                    if (bytes.isNotEmpty()) {
                        out.write(bytes)
                    }
                    out.flush()
                }
            } catch (e: Exception) {
                val cb = callbacks
                if (cb != null && !stopping.get()) {
                    post(cb) { onError(e.message ?: e.toString()) }
                }
                disconnectServer()
            }
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
            dataOut = null
            dataIn = null
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
        callbacks = null
    }

    private fun recvLoop(input: DataInputStream, listener: Listener) {
        try {
            while (!stopping.get()) {
                try {
                    input.readFully(headerScratch)
                } catch (_: EOFException) {
                    break
                }
                val payloadLenUnsigned =
                    ByteBuffer.wrap(headerScratch).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFF_FFFFL
                if (payloadLenUnsigned > MAX_PAYLOAD_BYTES) {
                    if (!stopping.get()) {
                        post(listener) {
                            onError("Некорректный заголовок кадра (длина вне допустимого диапазона)")
                        }
                    }
                    break
                }
                val payloadLen = payloadLenUnsigned.toInt()
                val body = if (payloadLen == 0) {
                    ByteArray(0)
                } else {
                    try {
                        ByteArray(payloadLen).also { input.readFully(it) }
                    } catch (_: EOFException) {
                        if (!stopping.get()) {
                            post(listener) { onError("Соединение разорвано во время получения сообщения") }
                        }
                        break
                    }
                }
                val text = String(body, Charsets.UTF_8)
                post(listener) { onPeerMessage(text) }
            }
        } catch (_: IOException) {
            if (!stopping.get()) {
                post(listener) { onError("Соединение разорвано") }
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
            dataOut = null
            dataIn = null
        }
    }

    private fun post(listener: Listener, block: Listener.() -> Unit) {
        mainHandler.post { listener.block() }
    }

    companion object {
        private const val DEFAULT_HOST = "83.217.202.145"
        private const val DEFAULT_PORT = 7770
        private const val CONNECT_TIMEOUT_MS = 15_000

        /** Как `TcpFrame::max_payload_bytes` в will: 2^20 байт. */
        private const val MAX_PAYLOAD_BYTES = 1 shl 20
    }
}
