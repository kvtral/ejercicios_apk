package com.kvtral.joystick.stream

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.kvtral.joystick.xhome.XHomeApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.kvtral.joystick.xhome.IceCandidate as XboxIceCandidate

sealed interface StreamStatus {
    data object Idle : StreamStatus
    data class Connecting(val step: String) : StreamStatus
    data object Connected : StreamStatus
    data class Failed(val message: String) : StreamStatus
}

/**
 * Conexión WebRTC con la consola. Abre los cuatro canales de datos que espera Xbox
 * (input, control, message, chat) y manda el estado del mando 60 veces por segundo.
 */
class StreamClient(
    context: Context,
    private val scope: CoroutineScope,
    private val onVibration: (InputPacket.Vibration) -> Unit,
) {
    private val appContext = context.applicationContext
    val eglBase: EglBase = EglBase.create()

    private val _status = MutableStateFlow<StreamStatus>(StreamStatus.Idle)
    val status: StateFlow<StreamStatus> = _status

    private val gamepad = AtomicReference(GamepadState())
    private var factory: PeerConnectionFactory? = null
    private var pc: PeerConnection? = null
    private val channels = mutableMapOf<String, DataChannel>()
    private val localCandidates = mutableListOf<IceCandidate>()
    private var gatheringDone = false
    private val protocol = MessageProtocol()
    private var api: XHomeApi? = null
    private val jobs = CopyOnWriteArrayList<Job>()
    private var inputStarted = false

    private var videoTrack: VideoTrack? = null
    private var audioTrack: AudioTrack? = null
    private var videoSink: VideoSink? = null

    /** Con el vídeo desactivado no se pinta nada y se silencia el audio: solo mando. */
    var mediaEnabled = false
        set(value) {
            field = value
            applyMedia()
        }

    fun setVideoSink(sink: VideoSink?) {
        videoSink?.let { videoTrack?.removeSink(it) }
        videoSink = sink
        applyMedia()
    }

    private fun applyMedia() {
        audioTrack?.setEnabled(mediaEnabled)
        val sink = videoSink ?: return
        val track = videoTrack ?: return
        if (mediaEnabled) {
            track.addSink(sink)
            sendControl(MessageProtocol.keyframeRequest())
        } else {
            track.removeSink(sink)
        }
    }

    fun updateGamepad(state: GamepadState) = gamepad.set(state)

    suspend fun connect(api: XHomeApi, serverId: String, transferToken: suspend () -> String) {
        this.api = api
        try {
            _status.value = StreamStatus.Connecting("Abriendo sesión en la consola…")
            api.startSession(serverId, transferToken) { state ->
                _status.value = StreamStatus.Connecting(
                    when (state) {
                        "WaitingForResources", "Provisioning" -> "La consola está preparando Remote Play…"
                        "ReadyToConnect" -> "Autorizando…"
                        else -> "Estado: $state"
                    },
                )
            }

            _status.value = StreamStatus.Connecting("Negociando conexión WebRTC…")
            val peer = createPeerConnection()
            val offer = peer.createOfferAsync()
            peer.setLocalAsync(offer)
            val answer = api.exchangeSdp(offer.description)
            peer.setRemoteAsync(SessionDescription(SessionDescription.Type.ANSWER, answer))

            _status.value = StreamStatus.Connecting("Buscando la consola en la red…")
            withTimeoutOrNull(5_000) { while (!gatheringDone) delay(100) }
            val local = synchronized(localCandidates) { localCandidates.toList() }.map {
                XboxIceCandidate(it.sdp, it.sdpMid, it.sdpMLineIndex)
            }
            val remote = Teredo.expand(api.exchangeIce(local))
            for (c in remote) {
                val sdp = c.candidate.removePrefix("a=")
                if (sdp == "end-of-candidates" || (sdp.contains("UDP") && sdp.contains("tcptype"))) continue
                peer.addIceCandidate(IceCandidate(c.sdpMid ?: "0", c.sdpMLineIndex, sdp))
            }

            jobs += scope.launch {
                while (isActive) {
                    delay(api.keepAliveSeconds * 1000L)
                    runCatching { api.keepAlive() }.onFailure { Log.w(TAG, "keepalive", it) }
                }
            }
            val connected = withTimeoutOrNull(20_000) {
                while (_status.value != StreamStatus.Connected) delay(100)
            }
            if (connected == null) throw IllegalStateException(
                "No se pudo conectar con la consola. Comprueba que el móvil y la Xbox están en la misma red.",
            )
        } catch (e: Exception) {
            Log.e(TAG, "connect", e)
            _status.value = StreamStatus.Failed(e.message ?: e.javaClass.simpleName)
            close()
        }
    }

    private fun createPeerConnection(): PeerConnection {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions(),
        )
        val f = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setAudioDeviceModule(JavaAudioDeviceModule.builder(appContext).createAudioDeviceModule())
            .createPeerConnectionFactory()
        factory = f

        val iceServers = listOf("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302").map {
            PeerConnection.IceServer.builder(it).createIceServer()
        }
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val peer = f.createPeerConnection(config, observer) ?: error("No se pudo crear WebRTC")
        pc = peer

        peer.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
        )
        peer.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV),
        )

        openChannel(peer, "input", "1.0", ordered = true)
        openChannel(peer, "chat", "chatV1")
        openChannel(peer, "control", "controlV1")
        openChannel(peer, "message", "messageV1")
        return peer
    }

    private fun openChannel(peer: PeerConnection, name: String, protocol: String, ordered: Boolean = true) {
        val init = DataChannel.Init().apply {
            this.ordered = ordered
            this.protocol = protocol
        }
        val channel = peer.createDataChannel(name, init) ?: return
        channels[name] = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit
            override fun onStateChange() {
                if (name == "message" && channel.state() == DataChannel.State.OPEN) {
                    sendText("message", this@StreamClient.protocol.handshake())
                }
            }
            override fun onMessage(buffer: DataChannel.Buffer) = onChannelMessage(name, buffer.data)
        })
    }

    private fun onChannelMessage(name: String, data: ByteBuffer) {
        when (name) {
            "input" -> InputPacket.parseVibration(data)?.let(onVibration)
            "message" -> {
                val bytes = ByteArray(data.remaining()).also { data.get(it) }
                when (val msg = protocol.parse(String(bytes))) {
                    MessageProtocol.Incoming.HandshakeAck -> onHandshakeAck()
                    is MessageProtocol.Incoming.NeedsReply -> sendText("message", msg.reply)
                    MessageProtocol.Incoming.Ignored -> Unit
                }
            }
        }
    }

    private fun onHandshakeAck() {
        if (inputStarted) return
        inputStarted = true
        jobs += scope.launch {
            withTimeoutOrNull(5_000) {
                while (listOf("control", "input").any { channels[it]?.state() != DataChannel.State.OPEN }) delay(50)
            }
            sendControl(MessageProtocol.authorizationRequest())
            sendControl(MessageProtocol.gamepadChanged(0, added = false))
            protocol.sessionSetup().forEach { sendText("message", it) }

            var sequence = 0
            sendBinary("input", InputPacket.clientMetadata(sequence, now()))
            delay(500)
            sendControl(MessageProtocol.gamepadChanged(0, added = true))
            _status.value = StreamStatus.Connected

            while (isActive) {
                sequence++
                sendBinary("input", InputPacket.gamepad(sequence, now(), listOf(gamepad.get())))
                delay(16)
            }
        }
    }

    private fun now() = SystemClock.elapsedRealtimeNanos() / 1_000_000.0

    private fun sendControl(json: String) = sendText("control", json)

    // El cliente web también manda el JSON como binario (UTF-8).
    private fun sendText(name: String, text: String) = sendBinary(name, ByteBuffer.wrap(text.toByteArray()))

    private fun sendBinary(name: String, data: ByteBuffer) {
        val channel = channels[name] ?: return
        if (channel.state() == DataChannel.State.OPEN) channel.send(DataChannel.Buffer(data, true))
    }

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            Log.i(TAG, "ICE $state")
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
            if (state == PeerConnection.IceGatheringState.COMPLETE) gatheringDone = true
        }
        override fun onIceCandidate(candidate: IceCandidate) {
            synchronized(localCandidates) { localCandidates += candidate }
        }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(channel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
            when (val track = receiver.track()) {
                is VideoTrack -> videoTrack = track
                is AudioTrack -> audioTrack = track
            }
            applyMedia()
        }
        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
            Log.i(TAG, "Conexión $state")
            if (state == PeerConnection.PeerConnectionState.FAILED ||
                (state == PeerConnection.PeerConnectionState.DISCONNECTED && _status.value == StreamStatus.Connected)
            ) {
                _status.value = StreamStatus.Failed("Se perdió la conexión con la consola.")
            }
        }
    }

    fun close() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        if (channels["control"]?.state() == DataChannel.State.OPEN) {
            sendControl(MessageProtocol.gamepadChanged(0, added = false))
        }
        val oldApi = api
        api = null
        scope.launch { oldApi?.stop() }
        channels.values.forEach { it.unregisterObserver(); it.dispose() }
        channels.clear()
        videoTrack = null
        audioTrack = null
        pc?.dispose()
        pc = null
        factory?.dispose()
        factory = null
        inputStarted = false
        gatheringDone = false
        synchronized(localCandidates) { localCandidates.clear() }
        if (_status.value !is StreamStatus.Failed) _status.value = StreamStatus.Idle
    }

    fun release() {
        close()
        eglBase.release()
    }

    companion object {
        private const val TAG = "StreamClient"
    }
}

private suspend fun PeerConnection.createOfferAsync(): SessionDescription =
    suspendCancellableCoroutine { cont ->
        val constraints = MediaConstraints().apply {
            mandatory += MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
            mandatory += MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true")
        }
        createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) = cont.resume(sdp)
            override fun onCreateFailure(error: String?) = cont.resumeWithException(IllegalStateException("Oferta SDP: $error"))
            override fun onSetSuccess() = Unit
            override fun onSetFailure(error: String?) = Unit
        }, constraints)
    }

private suspend fun PeerConnection.setLocalAsync(sdp: SessionDescription) = setAsync { setLocalDescription(it, sdp) }

private suspend fun PeerConnection.setRemoteAsync(sdp: SessionDescription) = setAsync { setRemoteDescription(it, sdp) }

private suspend fun setAsync(call: (SdpObserver) -> Unit): Unit = suspendCancellableCoroutine { cont ->
    call(object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetSuccess() = cont.resume(Unit)
        override fun onSetFailure(error: String?) = cont.resumeWithException(IllegalStateException("SDP: $error"))
    })
}
