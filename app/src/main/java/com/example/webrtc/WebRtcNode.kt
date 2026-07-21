package com.example.webrtc

import android.graphics.Color
import android.graphics.SurfaceTexture
import android.view.Surface
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Stream
import com.google.android.filament.Texture
import io.github.sceneview.geometries.Plane
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.node.PlaneNode
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.VideoSink
import org.webrtc.VideoFrame

class WebRtcNode(
    engine: com.google.android.filament.Engine,
    materialLoader: MaterialLoader,
    eglContext: EglBase.Context
) : PlaneNode(engine = engine, size = io.github.sceneview.math.Size(1.0f, 1.77f)) { // assuming 16:9

    private val surfaceTexture = SurfaceTexture(0).also {
        it.detachFromGLContext()
    }
    private val surface = Surface(surfaceTexture)
    
    private val stream: Stream = Stream.Builder()
        .stream(surfaceTexture)
        .build(engine)
        
    private val texture: Texture = Texture.Builder()
        .sampler(Texture.Sampler.SAMPLER_EXTERNAL)
        .format(Texture.InternalFormat.RGB8)
        .build(engine)
        .apply { setExternalStream(engine, stream) }
        
    init {
        materialInstance = materialLoader.createVideoInstance(texture, Color.BLACK)
            .also { setMaterialInstanceAt(0, it) }
    }
    
    private val eglRenderer = EglRenderer("WebRtcNodeRenderer")
    
    val videoSink: VideoSink = VideoSink { frame ->
        eglRenderer.onFrame(frame)
    }

    init {
        eglRenderer.init(eglContext, EglBase.CONFIG_PLAIN, GlRectDrawer())
        eglRenderer.createEglSurface(surface)
    }

    override fun destroy() {
        super.destroy()
        eglRenderer.release()
        surface.release()
        surfaceTexture.release()
        // texture and stream might need to be released
    }
}
