package com.will.app

import android.os.Handler
import android.os.Looper
import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import will.v1.MessengerGrpcKt
import will.v1.MessengerOuterClass.ClientEvent
import will.v1.MessengerOuterClass.DwellerKind
import will.v1.MessengerOuterClass.ServerEvent
import will.v1.bindToken
import will.v1.chatMessage
import will.v1.clientEvent
import will.v1.historyRequest
import java.io.IOException

/**
 * gRPC к серверу Will: один двунаправленный поток `Messenger.Session` из `messenger.proto`.
 *
 * Вход (как `WillClient::authenticate_device`): `BindToken` → `AuthOk`, затем `HistoryRequest`.
 * Поток ответов открывается вместе с потоком запросов, поэтому `AuthOk`, пришедший сразу,
 * не теряется (гонка входа, исправленная в консольном клиенте 02.10).
 *
 * Слова (`Word`) до `HistoryEnd` — история, после — живые слова места, на которое смотрим.
 * Обрыв потока сразу сообщается слушателю; переподключение — забота [ChatSession].
 */
class WillChatBridge {

    interface Listener {
        fun onPeerMessage(authorName: String, text: String)
        /** Сервер подтвердил приём своего сообщения (не текст в чат). */
        fun onServerReceiptConfirmed()
        fun onHistoryItem(authorName: String, text: String, isMine: Boolean)
        fun onHistoryLoaded()
        fun onError(message: String)
        fun onConnectionChanged(connected: Boolean)
        fun onAuthenticating() {}
        /** Короткое уведомление сервера (отказ, подтверждение действия, обитатели). */
        fun onNotice(message: String) {}
        /** Взгляд перенесён: дальше придут слова нового места до `HistoryEnd`. */
        fun onTurned(where: String) {}
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private var channel: ManagedChannel? = null
    private var outbound: Channel<ClientEvent>? = null
    private var job: Job? = null

    /** Слушатель текущего connect; после [disconnectServer] — null, колбэки не идут. */
    @Volatile
    private var callbacks: Listener? = null

    @Volatile
    private var connected = false

    fun isConnected(): Boolean = connected

    fun connect(host: String, port: Int, deviceToken: String, listener: Listener) {
        if (!DeviceTokenStore.isValid(deviceToken)) {
            mainHandler.post {
                listener.onError(
                    "Некорректный device token " +
                        "(ожидается ${DeviceTokenStore.MIN_LENGTH}–${DeviceTokenStore.MAX_LENGTH} hex)",
                )
            }
            return
        }

        disconnectServer()
        callbacks = listener

        val ch = OkHttpChannelBuilder.forAddress(host, port).usePlaintext().build()
        val out = Channel<ClientEvent>(Channel.UNLIMITED)
        val stub = MessengerGrpcKt.MessengerCoroutineStub(ch)

        val started = scope.launch {
            var historyPending = false
            try {
                post(listener) { onAuthenticating() }
                out.send(clientEvent { bindToken = bindToken { token = deviceToken } })

                stub.session(out.consumeAsFlow()).collect { event ->
                    when (event.eventCase) {
                        ServerEvent.EventCase.AUTH_OK -> {
                            connected = true
                            post(listener) { onConnectionChanged(true) }
                            historyPending = true
                            out.send(
                                clientEvent {
                                    historyRequest = historyRequest { limit = HISTORY_LIMIT_ON_CONNECT }
                                },
                            )
                        }
                        ServerEvent.EventCase.AUTH_REQUIRED ->
                            throw IOException("Авторизация отклонена (AuthRequired)")
                        ServerEvent.EventCase.RECEIPT_ACK ->
                            post(listener) { onServerReceiptConfirmed() }
                        ServerEvent.EventCase.WORD -> {
                            val word = event.word
                            if (historyPending) {
                                post(listener) { onHistoryItem(word.name, word.body, word.isMine) }
                            } else {
                                post(listener) { onPeerMessage(word.name, word.body) }
                            }
                        }
                        ServerEvent.EventCase.HISTORY_END -> {
                            historyPending = false
                            post(listener) { onHistoryLoaded() }
                        }
                        ServerEvent.EventCase.PROTOCOL_NOTICE ->
                            post(listener) { onNotice(event.protocolNotice.message) }
                        ServerEvent.EventCase.TURNED -> {
                            // Сервер следом шлёт слова нового места и HistoryEnd.
                            historyPending = true
                            val turned = event.turned
                            val where = when {
                                turned.abodeOf.isNotEmpty() -> "Обитель ${turned.abodeOf}"
                                turned.tieWith.isNotEmpty() -> "Узы с ${turned.tieWith}"
                                else -> "Своя Обитель"
                            }
                            post(listener) { onTurned(where) }
                        }
                        ServerEvent.EventCase.DWELLING -> {
                            val dwelling = event.dwelling
                            post(listener) {
                                onNotice(
                                    "Вы обитаете у ${dwelling.hostName} как ${kindName(dwelling.kind)} — " +
                                        "/visit ${dwelling.hostName}",
                                )
                            }
                        }
                        ServerEvent.EventCase.DWELLERS -> {
                            val told = event.dwellers.dwellersList
                            val text = if (told.isEmpty()) {
                                "В вашей Обители никто не обитает."
                            } else {
                                "Обитатели: " + told.joinToString(", ") { "${it.name} (${kindName(it.kind)})" }
                            }
                            post(listener) { onNotice(text) }
                        }
                        ServerEvent.EventCase.SUPPLICATION_OFFER ->
                            post(listener) { onNotice("Прошение от ${event.supplicationOffer.suppliantName}") }
                        ServerEvent.EventCase.TIE_FORMED ->
                            post(listener) { onNotice("Узы с ${event.tieFormed.counterpartName}") }
                        ServerEvent.EventCase.STIRRED ->
                            post(listener) { onNotice("Новое слово в Узах с ${event.stirred.tieWith}") }
                        else -> {}
                    }
                }
                throw IOException("Сервер закрыл соединение")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                post(listener) { onError(e.message ?: e.toString()) }
            } finally {
                connected = false
                post(listener) { onConnectionChanged(false) }
                ch.shutdownNow()
            }
        }

        synchronized(lock) {
            channel = ch
            outbound = out
            job = started
        }
    }

    /** Отправить событие клиента в поток; `false`, если соединения нет. */
    fun send(event: ClientEvent): Boolean {
        val out = synchronized(lock) { outbound }
        return out != null && connected && out.trySend(event).isSuccess
    }

    /**
     * Отправка ChatMessage. [onComplete] вызывается на main: `true`, если сообщение
     * поставлено в поток к серверу.
     */
    fun sendLine(line: String, onComplete: (Boolean) -> Unit = {}) {
        val out = synchronized(lock) { outbound }
        val queued = out != null && connected &&
            out.trySend(clientEvent { chat = chatMessage { body = line } }).isSuccess
        mainHandler.post { onComplete(queued) }
    }

    fun disconnectServer() {
        callbacks = null
        connected = false
        val (ch, out, j) = synchronized(lock) {
            val taken = Triple(channel, outbound, job)
            channel = null
            outbound = null
            job = null
            taken
        }
        out?.close()
        j?.cancel()
        ch?.shutdownNow()
    }

    /** Колбэк на main, только если слушатель всё ещё текущий. */
    private fun post(listener: Listener, block: Listener.() -> Unit) {
        mainHandler.post {
            if (callbacks === listener) {
                listener.block()
            }
        }
    }

    companion object {
        /** Debug — локальный сервер (эмулятор видит хост как 10.0.2.2), release — удалённый. */
        val DEFAULT_HOST: String = BuildConfig.WILL_HOST
        const val DEFAULT_PORT = 7770

        fun kindName(kind: DwellerKind): String = when (kind) {
            DwellerKind.NEIGHBOUR -> "ближний"
            DwellerKind.FRIEND -> "друг"
            else -> "знакомый"
        }

        /** Как `--history N` в will-client. */
        private const val HISTORY_LIMIT_ON_CONNECT = 200
    }
}
