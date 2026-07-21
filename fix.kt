    fun getEglContext(): EglBase.Context = eglBase.eglBaseContext

    private var customRemoteVideoSink: VideoSink? = null

    fun setRemoteVideoSink(sink: VideoSink) {
        customRemoteVideoSink = sink
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
