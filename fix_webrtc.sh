sed -i 's/videoCapturer!!.isScreencast/videoCapturer?.isScreencast ?: false/g' app/src/main/java/com/example/webrtc/WebRTCManager.kt
sed -i 's/videoCapturer?.startCapture(1280, 720, 30)/try { videoCapturer?.startCapture(1280, 720, 30) } catch (e: Exception) { Log.e("WebRTCManager", "Failed to start camera", e) }/g' app/src/main/java/com/example/webrtc/WebRTCManager.kt
