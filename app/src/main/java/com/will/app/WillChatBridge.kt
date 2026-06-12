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
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TCP к серверу Will: кадр как у `TcpFrame` (**4 байта длины пейлоада BE uint32** + пейлоад),
 * внутри пейлоада — **`WireMessage` из проекта will**: первый байт типа, дальше тело.
 *
 * Handshake (как `WillClient::authenticate_phone`): `OtpPhoneRequest` → `OtpSent` →
 * `OtpCodeSubmit` → `OtpVerifyResponse` → `BindToken`, затем `HistoryRequest` и приём истории/чата.
 *
 * - клиент → сервер: `0x01` UserChat, `0x03` HistoryRequest, `0x06` OtpPhoneRequest,
 *   `0x08` BindToken, `0x0A` OtpCodeSubmit;
 * - сервер → клиент: `0x02` ack, `0x01` peer chat, `0x04`/`0x05` история, `0x07` OtpSent,
 *   `0x09` AuthRequired, `0x0B` OtpVerifyResponse.
 */
class WillChatBridge {

    interface Listener {
        fun onPeerMessage(text: String)
        /** Отдельный кадр-подтверждение от сервера (не текст в чат). */
        fun onServerReceiptConfirmed()
        fun onHistoryItem(text: String, isMine: Boolean)
        fun onHistoryLoaded()
        fun onError(message: String)
        fun onConnectionChanged(connected: Boolean)
        fun onRequestingOtp() {}
        fun onOtpSent() {}
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

    private val awaitingOtpCode = AtomicBoolean(false)
    private var otpCodeQueue: ArrayBlockingQueue<String>? = null

    private val headerScratch = ByteArray(4)

    fun isConnected(): Boolean = synchronized(lock) {
        socket?.isConnected == true && !stopping.get() && !awaitingOtpCode.get()
    }

    fun isAwaitingOtpCode(): Boolean = awaitingOtpCode.get()

    fun requestOtp(host: String, port: Int, phoneE164: String, listener: Listener) {
        disconnectServer()
        stopping.set(false)
        callbacks = listener
        awaitingOtpCode.set(false)
        otpCodeQueue = ArrayBlockingQueue(1)

        val thread = Thread({
            try {
                val s = Socket()
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
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

                post(listener) { onRequestingOtp() }
                synchronized(lock) {
                    authenticateOtpBlocking(phoneE164, socketIn, listener)
                }
                post(listener) { onConnectionChanged(true) }
                requestHistoryOnConnectThread(HISTORY_LIMIT_ON_CONNECT)
                recvLoop(socketIn, listener)
            } catch (e: Exception) {
                if (!stopping.get()) {
                    post(listener) { onError(e.message ?: e.toString()) }
                    post(listener) { onConnectionChanged(false) }
                }
                cleanupLocked()
                callbacks = null
            } finally {
                awaitingOtpCode.set(false)
                otpCodeQueue = null
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

    fun submitOtpCode(code: String) {
        if (!isValidOtpCode(code)) {
            val cb = callbacks
            if (cb != null) {
                post(cb) { onError("Код должен содержать от 4 до 8 цифр") }
            }
            return
        }
        val queue = otpCodeQueue
        if (queue == null || !awaitingOtpCode.get()) {
            val cb = callbacks
            if (cb != null) {
                post(cb) { onError("Нет ожидающего запроса OTP") }
            }
            return
        }
        queue.offer(code)
    }

    fun sendLine(line: String) {
        // Сеть на UI-потоке даёт StrictMode → NetworkOnMainThreadException → сокет считают «сломанным».
        sendExecutor.execute {
            try {
                val utf8 = line.toByteArray(Charsets.UTF_8)
                val payloadLen = 1 + utf8.size
                if (payloadLen > MAX_PAYLOAD_BYTES) {
                    val cb = callbacks
                    if (cb != null && !stopping.get()) {
                        post(cb) {
                            onError(
                                "Сообщение длиннее допустимого для протокола " +
                                    "(пейлоад не более $MAX_PAYLOAD_BYTES байт, с учётом типа)",
                            )
                        }
                    }
                    return@execute
                }
                val payload = ByteArray(payloadLen).also {
                    it[0] = USER_CHAT_TYPE
                    if (utf8.isNotEmpty()) {
                        System.arraycopy(utf8, 0, it, 1, utf8.size)
                    }
                }
                sendPayloadLocked(payload)
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
        awaitingOtpCode.set(false)
        otpCodeQueue?.offer(OTP_CODE_CANCELLED)
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
        otpCodeQueue = null
        callbacks = null
    }

    private fun authenticateOtpBlocking(phoneE164: String, input: DataInputStream, listener: Listener) {
        sendPayloadLocked(encodeOtpPhoneRequest(phoneE164))

        val phoneResponse = readPayloadBlocking(input)
        when (val phoneResult = parseOtpPhoneResponse(phoneResponse)) {
            is OtpPhoneResponseResult.Sent -> { /* ok */ }
            is OtpPhoneResponseResult.Failure -> throw IOException(phoneResult.message)
            OtpPhoneResponseResult.Unexpected -> throw IOException("Неожиданный ответ на запрос OTP")
        }

        awaitingOtpCode.set(true)
        post(listener) { onOtpSent() }

        val queue = otpCodeQueue ?: throw IOException("Нет соединения")
        val code = queue.poll(OTP_CODE_WAIT_MS, TimeUnit.MILLISECONDS)
            ?: throw IOException("Время ожидания кода истекло")
        if (code === OTP_CODE_CANCELLED) {
            throw IOException("Отключено")
        }

        awaitingOtpCode.set(false)
        sendPayloadLocked(encodeOtpCodeSubmit(code))

        val verifyResponse = readPayloadBlocking(input)
        val token = parseOtpVerifyResponseToken(verifyResponse)
            ?: throw IOException(otpVerifyFailureMessage(verifyResponse))
        sendPayloadLocked(encodeBindToken(token))
    }

    /** На потоке connect, до [recvLoop]. */
    private fun requestHistoryOnConnectThread(limit: Int) {
        if (limit !in 1..MAX_HISTORY_REQUEST_LIMIT) {
            throw IllegalArgumentException(
                "Лимит истории должен быть от 1 до $MAX_HISTORY_REQUEST_LIMIT",
            )
        }
        val payload = ByteArray(5).also {
            it[0] = HISTORY_REQUEST_TYPE
            ByteBuffer.wrap(it, 1, 4).order(ByteOrder.BIG_ENDIAN).putInt(limit)
        }
        synchronized(lock) {
            sendPayloadLocked(payload)
        }
    }

    private fun sendPayloadLocked(payload: ByteArray) {
        if (payload.size > MAX_PAYLOAD_BYTES) {
            throw IOException("Пейлоад превышает лимит протокола")
        }
        val out = dataOut ?: throw IOException("Нет соединения")
        out.writeInt(payload.size)
        out.write(payload)
        out.flush()
    }

    private fun readPayloadBlocking(input: DataInputStream): ByteArray {
        try {
            input.readFully(headerScratch)
        } catch (_: EOFException) {
            throw IOException("Соединение разорвано")
        }
        val payloadLenUnsigned =
            ByteBuffer.wrap(headerScratch).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFF_FFFFL
        if (payloadLenUnsigned > MAX_PAYLOAD_BYTES) {
            throw IOException("Некорректный заголовок кадра (длина вне допустимого диапазона)")
        }
        val payloadLen = payloadLenUnsigned.toInt()
        if (payloadLen == 0) {
            throw IOException("Пустой пейлоад кадра (протокол WireMessage)")
        }
        return try {
            ByteArray(payloadLen).also { input.readFully(it) }
        } catch (_: EOFException) {
            throw IOException("Соединение разорвано во время получения сообщения")
        }
    }

    private fun recvLoop(input: DataInputStream, listener: Listener) {
        try {
            while (!stopping.get()) {
                val body = try {
                    readPayloadBlocking(input)
                } catch (e: IOException) {
                    if (!stopping.get()) {
                        post(listener) { onError(e.message ?: e.toString()) }
                    }
                    break
                }
                when (val action = decodeInboundPayload(body)) {
                    is InboundDecodeAction.PeerText -> post(listener) {
                        onPeerMessage(action.text)
                    }
                    InboundDecodeAction.ServerAck -> post(listener) { onServerReceiptConfirmed() }
                    is InboundDecodeAction.HistoryItem -> post(listener) {
                        onHistoryItem(action.text, action.isMine)
                    }
                    InboundDecodeAction.HistoryEnd -> post(listener) { onHistoryLoaded() }
                    is InboundDecodeAction.ProtocolError -> {
                        if (!stopping.get()) {
                            post(listener) { onError(action.message) }
                        }
                        break
                    }
                }
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

    private sealed class InboundDecodeAction {
        data class PeerText(val text: String) : InboundDecodeAction()
        data object ServerAck : InboundDecodeAction()
        data class HistoryItem(val text: String, val isMine: Boolean) : InboundDecodeAction()
        data object HistoryEnd : InboundDecodeAction()
        data class ProtocolError(val message: String) : InboundDecodeAction()
    }

    private sealed class OtpPhoneResponseResult {
        data object Sent : OtpPhoneResponseResult()
        data class Failure(val message: String) : OtpPhoneResponseResult()
        data object Unexpected : OtpPhoneResponseResult()
    }

    /** Соответствует `WillClient::receiveMessage` / `WireMessage` в will. */
    private fun decodeInboundPayload(body: ByteArray): InboundDecodeAction {
        if (body.isEmpty()) {
            return InboundDecodeAction.ProtocolError("Пустой пейлоад кадра (протокол WireMessage)")
        }
        val t = body[0].toInt() and 0xFF
        if (body.size == 1 && t == SERVER_RECEIPT_ACK_TYPE) {
            return InboundDecodeAction.ServerAck
        }
        if (body.size == 1 && t == HISTORY_END_TYPE) {
            return InboundDecodeAction.HistoryEnd
        }
        if (body.size == 1 && t == AUTH_REQUIRED_TYPE) {
            return InboundDecodeAction.ProtocolError("Требуется авторизация (BindToken на сессии)")
        }
        if (body[0] == USER_CHAT_TYPE) {
            val text = if (body.size == 1) {
                ""
            } else {
                body.decodeToString(1, body.size)
            }
            return InboundDecodeAction.PeerText(text)
        }
        if (t == HISTORY_ITEM_TYPE) {
            if (body.size < 14) {
                return InboundDecodeAction.ProtocolError("Некорректный HistoryItem (слишком короткий)")
            }
            val bodyLen = ByteBuffer.wrap(body, 10, 4).order(ByteOrder.BIG_ENDIAN).int
            if (bodyLen < 0 || 14 + bodyLen != body.size) {
                return InboundDecodeAction.ProtocolError("Некорректный HistoryItem (длина тела)")
            }
            val isMine = body[9] != 0.toByte()
            val text = if (bodyLen == 0) {
                ""
            } else {
                body.decodeToString(14, body.size)
            }
            return InboundDecodeAction.HistoryItem(text, isMine)
        }
        if (t == OTP_SENT_TYPE || t == OTP_VERIFY_RESPONSE_TYPE) {
            return InboundDecodeAction.ProtocolError(
                "Неожиданный OTP-кадр после авторизации (0x${t.toString(16)})",
            )
        }
        return InboundDecodeAction.ProtocolError(
            "Неизвестный тип сообщения (0x${t.toString(16)}), длина ${body.size}",
        )
    }

    companion object {
        const val DEFAULT_HOST = "83.217.202.145"
        const val DEFAULT_PORT = 7770
        const val DEFAULT_PHONE = "+15551234567"

        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val OTP_CODE_WAIT_MS = 300_000L

        /** Sentinel для разблокировки ожидания кода при disconnect. */
        private val OTP_CODE_CANCELLED = ""

        /** Как `TcpFrame::max_payload_bytes` в will: 2^20 байт. */
        const val MAX_PAYLOAD_BYTES = 1 shl 20

        /** Максимальная длина токена в BindToken (length-prefixed string). */
        private const val MAX_TOKEN_BYTES = 4096

        /** `WillMessage::MaxHistoryRequestLimit` */
        private const val MAX_HISTORY_REQUEST_LIMIT = 1000

        /** Как `--history N` в will-client. */
        private const val HISTORY_LIMIT_ON_CONNECT = 50

        /** `WireMessage::Type::UserChat` */
        private const val USER_CHAT_TYPE: Byte = 1

        /** `WireMessage::Type::ServerReceiptAck` */
        private const val SERVER_RECEIPT_ACK_TYPE = 2

        /** `WireMessage::Type::HistoryRequest` */
        private const val HISTORY_REQUEST_TYPE: Byte = 3

        /** `WireMessage::Type::HistoryItem` */
        private const val HISTORY_ITEM_TYPE = 4

        /** `WireMessage::Type::HistoryEnd` */
        private const val HISTORY_END_TYPE = 5

        /** `WireMessage::Type::OtpPhoneRequest` */
        private const val OTP_PHONE_REQUEST_TYPE: Byte = 6

        /** `WireMessage::Type::OtpSent` */
        private const val OTP_SENT_TYPE = 7

        /** `WireMessage::Type::BindToken` */
        private const val BIND_TOKEN_TYPE: Byte = 8

        /** `WireMessage::Type::AuthRequired` */
        private const val AUTH_REQUIRED_TYPE = 9

        /** `WireMessage::Type::OtpCodeSubmit` */
        private const val OTP_CODE_SUBMIT_TYPE: Byte = 10

        /** `WireMessage::Type::OtpVerifyResponse` */
        private const val OTP_VERIFY_RESPONSE_TYPE = 11

        /** `OtpVerifyResponseMessage::Error::InvalidPhone` */
        private const val OTP_ERROR_INVALID_PHONE = 1

        /** `OtpVerifyResponseMessage::Error::RateLimited` */
        private const val OTP_ERROR_RATE_LIMITED = 2

        /** `OtpVerifyResponseMessage::Error::InvalidCode` */
        private const val OTP_ERROR_INVALID_CODE = 3

        /** `OtpVerifyResponseMessage::Error::Expired` */
        private const val OTP_ERROR_EXPIRED = 4

        /** `OtpVerifyResponseMessage::Error::Internal` */
        private const val OTP_ERROR_INTERNAL = 5

        fun normalizePhoneE164(phone: String): String? {
            val stripped = buildString {
                for (c in phone) {
                    if (c == ' ' || c == '-' || c == '(' || c == ')') continue
                    append(c)
                }
            }
            if (stripped.isEmpty()) return null

            var normalized = stripped
            if (normalized.startsWith("00")) {
                normalized = "+" + normalized.substring(2)
            }
            if (!normalized.startsWith("+")) {
                if (!normalized.all { it in '0'..'9' }) return null
                normalized = "+$normalized"
            }
            if (normalized.length < 2 || normalized[0] != '+') return null

            val digits = normalized.substring(1)
            if (digits.length !in 8..15) return null
            if (digits[0] !in '1'..'9') return null
            if (!digits.all { it in '0'..'9' }) return null
            return normalized
        }

        fun isValidOtpCode(code: String): Boolean {
            if (code.length !in 4..8) return false
            return code.all { it in '0'..'9' }
        }

        fun encodeOtpPhoneRequest(phoneE164: String): ByteArray {
            val phoneBytes = phoneE164.toByteArray(Charsets.UTF_8)
            if (normalizePhoneE164(phoneE164) == null) {
                throw IllegalArgumentException("Некорректный номер телефона E.164")
            }
            val total = 1 + phoneBytes.size
            if (total > MAX_PAYLOAD_BYTES) {
                throw IllegalArgumentException("Номер телефона слишком длинный для протокола")
            }
            return ByteArray(total).also {
                it[0] = OTP_PHONE_REQUEST_TYPE
                if (phoneBytes.isNotEmpty()) {
                    System.arraycopy(phoneBytes, 0, it, 1, phoneBytes.size)
                }
            }
        }

        fun encodeOtpCodeSubmit(code: String): ByteArray {
            if (!isValidOtpCode(code)) {
                throw IllegalArgumentException("Код должен содержать от 4 до 8 цифр")
            }
            val codeBytes = code.toByteArray(Charsets.US_ASCII)
            return ByteArray(1 + codeBytes.size).also {
                it[0] = OTP_CODE_SUBMIT_TYPE
                System.arraycopy(codeBytes, 0, it, 1, codeBytes.size)
            }
        }

        fun encodeBindToken(token: String): ByteArray {
            val tokenBytes = token.toByteArray(Charsets.UTF_8)
            if (token.isEmpty()) {
                throw IllegalArgumentException("Токен не может быть пустым")
            }
            if (tokenBytes.size > MAX_TOKEN_BYTES) {
                throw IllegalArgumentException("Токен длиннее $MAX_TOKEN_BYTES байт UTF-8")
            }
            val out = ByteArray(1 + 4 + tokenBytes.size)
            var offset = 0
            out[offset++] = BIND_TOKEN_TYPE
            appendLengthPrefixedField(out, offset, tokenBytes)
            return out
        }

        private fun appendLengthPrefixedField(buf: ByteArray, offset: Int, field: ByteArray): Int {
            ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.BIG_ENDIAN).putInt(field.size)
            var next = offset + 4
            if (field.isNotEmpty()) {
                System.arraycopy(field, 0, buf, next, field.size)
                next += field.size
            }
            return next
        }

        private fun parseOtpPhoneResponse(payload: ByteArray): OtpPhoneResponseResult {
            if (payload.size == 1 && payload[0].toInt() and 0xFF == OTP_SENT_TYPE) {
                return OtpPhoneResponseResult.Sent
            }
            if (payload.size == 3 &&
                payload[0].toInt() and 0xFF == OTP_VERIFY_RESPONSE_TYPE &&
                payload[1] == 0.toByte()
            ) {
                return OtpPhoneResponseResult.Failure(otpVerifyFailureMessage(payload))
            }
            return OtpPhoneResponseResult.Unexpected
        }

        private fun parseOtpVerifyResponseToken(payload: ByteArray): String? {
            if (payload.isEmpty() || payload[0].toInt() and 0xFF != OTP_VERIFY_RESPONSE_TYPE) {
                return null
            }
            if (payload.size < 2) {
                return null
            }
            val success = payload[1] != 0.toByte()
            if (!success) {
                return null
            }
            if (payload.size < 6) {
                return null
            }
            val tokenLen = ByteBuffer.wrap(payload, 2, 4).order(ByteOrder.BIG_ENDIAN).int
            if (tokenLen <= 0 || tokenLen > MAX_TOKEN_BYTES || 6 + tokenLen != payload.size) {
                return null
            }
            return payload.decodeToString(6, payload.size)
        }

        private fun otpVerifyFailureMessage(payload: ByteArray): String {
            if (payload.size == 3 &&
                payload[0].toInt() and 0xFF == OTP_VERIFY_RESPONSE_TYPE &&
                payload[1] == 0.toByte()
            ) {
                return when (payload[2].toInt() and 0xFF) {
                    OTP_ERROR_INVALID_PHONE -> "Некорректный номер телефона"
                    OTP_ERROR_RATE_LIMITED -> "Слишком много запросов, попробуйте позже"
                    OTP_ERROR_INVALID_CODE -> "Неверный код"
                    OTP_ERROR_EXPIRED -> "Код истёк, запросите новый"
                    OTP_ERROR_INTERNAL -> "Внутренняя ошибка сервера"
                    else -> "Ошибка OTP (код ${payload[2].toInt() and 0xFF})"
                }
            }
            return "Ошибка OTP"
        }
    }
}
