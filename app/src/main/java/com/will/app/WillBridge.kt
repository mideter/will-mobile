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
import will.v1.MessengerOuterClass.ServerEvent
import will.v1.bindToken
import will.v1.clientEvent

/**
 * Транспорт к серверу Will: один двунаправленный gRPC-поток `Messenger.Session`.
 *
 * Первым уходит `BindToken`; поток ответов открывается вместе с потоком запросов, поэтому
 * `AuthOk`, пришедший сразу, не теряется. События сервера отдаются как есть, на главном
 * потоке; что они значат, решает [WillSession].
 */
class WillBridge {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private var channel: ManagedChannel? = null
    private var outbound: Channel<ClientEvent>? = null
    private var job: Job? = null

    /** Номер текущего соединения: события прежних отбрасываются. */
    @Volatile
    private var generation = 0

    fun connect(
        host: String,
        port: Int,
        deviceToken: String,
        onEvent: (ServerEvent) -> Unit,
        onClosed: (String) -> Unit,
    ) {
        disconnect()
        val gen = ++generation

        val ch = OkHttpChannelBuilder.forAddress(host, port).usePlaintext().build()
        val out = Channel<ClientEvent>(Channel.UNLIMITED)
        val stub = MessengerGrpcKt.MessengerCoroutineStub(ch)

        val started = scope.launch {
            var reason = "Сервер закрыл соединение"
            try {
                out.send(clientEvent { bindToken = bindToken { token = deviceToken } })
                stub.session(out.consumeAsFlow()).collect { event ->
                    mainHandler.post { if (gen == generation) onEvent(event) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reason = e.message ?: e.toString()
            } finally {
                ch.shutdownNow()
                mainHandler.post { if (gen == generation) onClosed(reason) }
            }
        }

        synchronized(lock) {
            channel = ch
            outbound = out
            job = started
        }
    }

    /** Отправить событие клиента; `false`, если соединения нет. */
    fun send(event: ClientEvent): Boolean {
        val out = synchronized(lock) { outbound } ?: return false
        return out.trySend(event).isSuccess
    }

    fun disconnect() {
        generation++
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

    companion object {
        /** Debug — локальный сервер (эмулятор видит хост как 10.0.2.2), release — удалённый. */
        val HOST: String = BuildConfig.WILL_HOST
        val PORT: Int = BuildConfig.WILL_PORT
    }
}
