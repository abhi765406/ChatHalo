sed -i '/val segmentedFrame = segmentationHelper.processFrame(frame)/,/segmentedFrame.release()/c\
                val segmentedFrame = segmentationHelper.processFrame(frame)\
                try {\
                    originalObserver.onFrameCaptured(segmentedFrame)\
                } finally {\
                    if (segmentedFrame != frame) {\
                        segmentedFrame.release()\
                    }\
                }' app/src/main/java/com/example/webrtc/WebRTCManager.kt
