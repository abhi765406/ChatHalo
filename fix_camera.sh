sed -i '/private fun createVideoCapturer(): VideoCapturer? {/,/return null/c\
    private fun createVideoCapturer(): VideoCapturer? {\
        var enumerator: org.webrtc.CameraEnumerator = org.webrtc.Camera2Enumerator(context)\
        var deviceNames = enumerator.deviceNames\
        if (deviceNames.isEmpty()) {\
            enumerator = org.webrtc.Camera1Enumerator(true)\
            deviceNames = enumerator.deviceNames\
        }\
        for (deviceName in deviceNames) {\
            if (enumerator.isFrontFacing(deviceName)) {\
                return enumerator.createCapturer(deviceName, null)\
            }\
        }\
        for (deviceName in deviceNames) {\
            if (enumerator.isBackFacing(deviceName)) {\
                return enumerator.createCapturer(deviceName, null)\
            }\
        }\
        return null\
    }' app/src/main/java/com/example/webrtc/WebRTCManager.kt
