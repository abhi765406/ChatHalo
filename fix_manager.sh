sed -i '/val segmentedFrame = segmentationHelper.processFrame(frame)/,/originalObserver.onFrameCaptured(segmentedFrame)/c\
                val segmentedFrame = segmentationHelper.processFrame(frame)\
                originalObserver.onFrameCaptured(segmentedFrame)\
                if (segmentedFrame != frame) {\
                    segmentedFrame.release()\
                }' app/src/main/java/com/example/webrtc/WebRTCManager.kt
