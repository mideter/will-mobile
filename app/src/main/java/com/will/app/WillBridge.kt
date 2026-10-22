package com.will.app

import android.os.Handler
import android.os.Looper
import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
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
 * потоке; что они значат, решает [WillSession]. События прежних соединений отбрасываются.
 * С сертификатом сервера (PEM) — только TLS и доверие только ему, не удостоверяющим центрам
 * телефона: подменить сервер, не похитив его ключ, нельзя; имя проверяется как обычно. Без
 * сертификата — открытый текст. Debug ходит к локальному серверу (эмулятор видит хост как
 * 10.0.2.2) и говорит TLS по `-PwillTls=true`; release — к удалённому и всегда по TLS.
 */
class WillBridge(
    private val certificate: String?,
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private var channel: ManagedChannel? = null
    private var outbound: Channel<ClientEvent>? = null
    private var job: Job? = null

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

        // Сеть может пропасть молча: поток жив на вид, а сервер давно не слышит. Пинг раз в
        // минуту — без ответа поток рвётся, и сессия переподключается. Сервер такие пинги
        // разрешает (не чаще раза в 5 с) и пингует сам — так он замечает пропавших.
        val builder = OkHttpChannelBuilder.forAddress(host, port)
        if (certificate != null) builder.sslSocketFactory(trusting(certificate)) else builder.usePlaintext()
        val ch = builder
            .keepAliveTime(KEEPALIVE_SECONDS, TimeUnit.SECONDS)
            .keepAliveTimeout(KEEPALIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
            .build()
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
        val HOST: String = BuildConfig.WILL_HOST
        val PORT: Int = BuildConfig.WILL_PORT

        val TLS: Boolean = BuildConfig.WILL_TLS

        private fun trusting(pem: String): SSLSocketFactory {
            val certificate = CertificateFactory.getInstance("X.509").generateCertificate(pem.byteInputStream())
            val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null)
                setCertificateEntry("will", certificate)
            }
            val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
            return SSLContext.getInstance("TLS").apply { init(null, trust.trustManagers, null) }.socketFactory
        }

        private const val KEEPALIVE_SECONDS = 60L
        private const val KEEPALIVE_TIMEOUT_SECONDS = 15L
    }
}
