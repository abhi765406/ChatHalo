package com.example.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*

class WebRTCManager(
    private val context: Context,
    private val signalingClient: SignalingClient,
    private val localSurfaceView: SurfaceViewRenderer,
    private val remoteSurfaceView: SurfaceViewRenderer,
    private val onConnectionStateChange: (PeerConnection.PeerConnectionState) -> Unit
) {
    private val eglBase: EglBase = EglBase.create()
    private val peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    
    private var videoCapturer: VideoCapturer? = null
    private var localVideoSource: VideoSource? = null
    private var localAudioSource: AudioSource? = null
    
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null

    init {
        // Initialize WebRTC
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        val options = PeerConnectionFactory.Options()
        val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .createPeerConnectionFactory()

        // Init SurfaceViews
        localSurfaceView.init(eglBase.eglBaseContext, null)
        localSurfaceView.setMirror(true)
        remoteSurfaceView.init(eglBase.eglBaseContext, null)
        remoteSurfaceView.setMirror(false)
        
        createLocalMedia()
    }

    private fun createLocalMedia() {
        // Audio
        val audioConstraints = MediaConstraints()
        localAudioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", localAudioSource)

        // Video
        videoCapturer = createVideoCapturer()
        localVideoSource = peerConnectionFactory.createVideoSource(videoCapturer?.isScreencast ?: false)
        
        val segmentationHelper = SegmentationHelper()
        val originalObserver = localVideoSource!!.capturerObserver
        val segmentedObserver = object : CapturerObserver {
            override fun onCapturerStarted(success: Boolean) {
                originalObserver.onCapturerStarted(success)
            }
            override fun onCapturerStopped() {
                originalObserver.onCapturerStopped()
            }
            override fun onFrameCaptured(frame: VideoFrame) {
                val segmentedFrame = segmentationHelper.processFrame(frame)
                try {
                    originalObserver.onFrameCaptured(segmentedFrame)
                } finally {
                    if (segmentedFrame != frame) {
                        segmentedFrame.release()
                    }
                }
            }
        }
        
        val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        videoCapturer?.initialize(surfaceTextureHelper, context, segmentedObserver)
        
        try { videoCapturer?.startCapture(1280, 720, 30) } catch (e: Exception) { Log.e("WebRTCManager", "Failed to start camera", e) }
        
        localVideoTrack = peerConnectionFactory.createVideoTrack("ARDAMSv0", localVideoSource)
        localVideoTrack?.addSink(localSurfaceView)
    }

    fun startCall() {
        createPeerConnection()
        signalingClient.initAsCaller()
        
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                sessionDescription?.let {
                    peerConnection?.setLocalDescription(SimpleSdpObserver(), it)
                    signalingClient.sendOffer(it)
                }
            }
        }, constraints)
    }

    fun joinCall() {
        createPeerConnection()
        signalingClient.initAsCallee()
    }

    private fun createPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            
            override fun onIceCandidate(candidate: IceCandidate) {
                signalingClient.sendIceCandidate(candidate)
            }
            
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            
            override fun onAddStream(stream: MediaStream) {
                if (stream.videoTracks.isNotEmpty()) {
                    stream.videoTracks[0].addSink(remoteSurfaceView)
                    customRemoteVideoSink?.let {
                        stream.videoTracks[0].addSink(it)
                    }
                }
            }
            
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {}

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                onConnectionStateChange(newState)
            }
        })

        localVideoTrack?.let { peerConnection?.addTrack(it, listOf("ARDAMS")) }
        localAudioTrack?.let { peerConnection?.addTrack(it, listOf("ARDAMS")) }
    }

    fun onOfferReceived(description: SessionDescription) {
        peerConnection?.setRemoteDescription(SimpleSdpObserver(), description)
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                sessionDescription?.let {
                    peerConnection?.setLocalDescription(SimpleSdpObserver(), it)
                    signalingClient.sendAnswer(it)
                }
            }
        }, constraints)
    }

    fun onAnswerReceived(description: SessionDescription) {
        peerConnection?.setRemoteDescription(SimpleSdpObserver(), description)
    }

    fun onIceCandidateReceived(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }
    
    fun switchCamera() {
        val cameraVideoCapturer = videoCapturer as? CameraVideoCapturer
        cameraVideoCapturer?.switchCamera(null)
    }
    
    fun toggleMute(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun getEglContext(): EglBase.Context = eglBase.eglBaseContext

    private var customRemoteVideoSink: VideoSink? = null

    fun setRemoteVideoSink(sink: VideoSink) {
        customRemoteVideoSink = sink
        
        // Local preview fallback (so user can see their own hologram without a peer)
        localVideoTrack?.addSink(sink)
        
        peerConnection?.transceivers?.forEach { transceiver ->
            val track = transceiver.receiver.track()
            if (track != null && track.kind() == "video") {
                (track as VideoTrack).addSink(sink)
            }
        }
    }

    fun destroy() {
        try {
            videoCapturer?.stopCapture()
            localSurfaceView.release()
            remoteSurfaceView.release()
            peerConnection?.close()
            peerConnectionFactory.dispose()
            eglBase.release()
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Error during cleanup", e)
        }
    }


    private fun createVideoCapturer(): VideoCapturer? {
        var enumerator: org.webrtc.CameraEnumerator = org.webrtc.Camera2Enumerator(context)
        var deviceNames = enumerator.deviceNames
        if (deviceNames.isEmpty()) {
            enumerator = org.webrtc.Camera1Enumerator(true)
            deviceNames = enumerator.deviceNames
        }
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        for (deviceName in deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }
}
