package com.example.webrtc

import android.graphics.ImageFormat
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import org.webrtc.JavaI420Buffer
import org.webrtc.VideoFrame

class SegmentationHelper {

    private val options = SelfieSegmenterOptions.Builder()
        .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
        .build()
        
    private val segmenter: Segmenter = Segmentation.getClient(options)
    private var nv21Buffer: ByteArray? = null

    @Volatile
    private var isProcessing = false
    
    @Volatile
    private var lastMask: FloatArray? = null
    @Volatile
    private var lastMaskWidth = 0
    @Volatile
    private var lastMaskHeight = 0

    fun processFrame(frame: VideoFrame): VideoFrame {
        return frame
        
        val i420 = frame.buffer.toI420() ?: return frame
        val width = i420.width
        val height = i420.height

        if (!isProcessing) {
            isProcessing = true
            
            try {
                val nv21 = getOrCreateNv21Buffer(width, height)
                i420ToNv21(i420, nv21)
                
                val inputImage = InputImage.fromByteArray(
                    nv21,
                    width,
                    height,
                    frame.rotation,
                    ImageFormat.NV21
                )

                segmenter.process(inputImage)
                    .addOnSuccessListener { mask ->
                        try {
                            val maskBuffer = mask.buffer
                            maskBuffer.order(java.nio.ByteOrder.nativeOrder())
                            val floatBuffer = maskBuffer.asFloatBuffer()
                            floatBuffer.rewind()
                            
                            val floats = FloatArray(floatBuffer.remaining())
                            floatBuffer.get(floats)

                            lastMask = floats
                            lastMaskWidth = mask.width
                            lastMaskHeight = mask.height
                        } catch (e: Exception) {
                            Log.e("SegmentationHelper", "Error copying mask", e)
                        } finally {
                            isProcessing = false
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("SegmentationHelper", "Segmentation failed", e)
                        isProcessing = false
                    }
            } catch (e: Exception) {
                Log.e("SegmentationHelper", "Error starting segmentation", e)
                isProcessing = false
            }
        }

        val currentMask = lastMask
        val currentMaskWidth = lastMaskWidth
        val currentMaskHeight = lastMaskHeight

        if (currentMask != null && currentMaskWidth > 0 && currentMaskHeight > 0) {
            var outI420: JavaI420Buffer? = null
            try {
                outI420 = JavaI420Buffer.allocate(width, height)
                applyMask(i420, outI420, currentMask, currentMaskWidth, currentMaskHeight)

                i420.release()
                return VideoFrame(outI420, frame.rotation, frame.timestampNs)
            } catch (e: Exception) {
                Log.e("SegmentationHelper", "Error applying mask", e)
                outI420?.release()
                // Fallback to original frame if mask application fails
            }
        }

        i420.release()
        return frame
    }

    private fun getOrCreateNv21Buffer(width: Int, height: Int): ByteArray {
        val size = width * height * 3 / 2
        if (nv21Buffer == null || nv21Buffer!!.size != size) {
            nv21Buffer = ByteArray(size)
        }
        return nv21Buffer!!
    }

    private fun i420ToNv21(i420: VideoFrame.I420Buffer, nv21: ByteArray) {
        val width = i420.width
        val height = i420.height
        
        val yPlane = i420.dataY
        val uPlane = i420.dataU
        val vPlane = i420.dataV
        
        val yStride = i420.strideY
        val uStride = i420.strideU
        val vStride = i420.strideV
        
        var pos = 0
        for (row in 0 until height) {
            yPlane.position(row * yStride)
            yPlane.get(nv21, pos, width)
            pos += width
        }
        
        val halfWidth = width / 2
        val halfHeight = height / 2
        var uvPos = width * height
        for (row in 0 until halfHeight) {
            vPlane.position(row * vStride)
            uPlane.position(row * uStride)
            for (col in 0 until halfWidth) {
                nv21[uvPos++] = vPlane.get()
                nv21[uvPos++] = uPlane.get()
            }
        }
    }

    private fun applyMask(
        inI420: VideoFrame.I420Buffer,
        outI420: JavaI420Buffer,
        mask: FloatArray,
        maskWidth: Int,
        maskHeight: Int
    ) {
        val width = inI420.width
        val height = inI420.height
        
        val inY = inI420.dataY
        val inU = inI420.dataU
        val inV = inI420.dataV
        
        val outY = outI420.dataY
        val outU = outI420.dataU
        val outV = outI420.dataV
        
        // Background color (Black in YUV)
        val bgY = 0.toByte()
        val bgU = 128.toByte()
        val bgV = 128.toByte()
        
        for (y in 0 until height) {
            val maskY = (y * maskHeight) / height
            
            // Hologram scanline effect (modulate brightness)
            val isScanline = (y % 6) < 2
            val scanlineMult = if (isScanline) 1.2f else 0.8f
            
            for (x in 0 until width) {
                val maskX = (x * maskWidth) / width
                val confidence = mask[maskY * maskWidth + maskX]
                
                val yIdx = y * inI420.strideY + x
                val outYIdx = y * outI420.strideY + x
                
                if (confidence > 0.3f) {
                    val origY = inY.get(yIdx).toInt() and 0xFF
                    
                    // Edge glow: boost brightness near the edges
                    val isEdge = confidence < 0.7f
                    val edgeBoost = if (isEdge) 1.5f else 1.0f
                    
                    var newY = (origY * scanlineMult * edgeBoost).toInt()
                    if (newY > 255) newY = 255
                    
                    outY.put(outYIdx, newY.toByte())
                } else {
                    outY.put(outYIdx, bgY)
                }
            }
        }
        
        val halfWidth = width / 2
        val halfHeight = height / 2
        
        for (y in 0 until halfHeight) {
            val maskY = (y * 2 * maskHeight) / height
            
            for (x in 0 until halfWidth) {
                val maskX = (x * 2 * maskWidth) / width
                val confidence = mask[maskY * maskWidth + maskX]
                
                val inUIdx = y * inI420.strideU + x
                val inVIdx = y * inI420.strideV + x
                val outUIdx = y * outI420.strideU + x
                val outVIdx = y * outI420.strideV + x
                
                if (confidence > 0.3f) {
                    val origU = inU.get(inUIdx).toInt() and 0xFF
                    val origV = inV.get(inVIdx).toInt() and 0xFF
                    
                    val newU = ((origU * 0.2f) + (200 * 0.8f)).toInt().coerceIn(0, 255)
                    val newV = ((origV * 0.2f) + (50 * 0.8f)).toInt().coerceIn(0, 255)
                    
                    outU.put(outUIdx, newU.toByte())
                    outV.put(outVIdx, newV.toByte())
                } else {
                    outU.put(outUIdx, bgU)
                    outV.put(outVIdx, bgV)
                }
            }
        }
    }
}
