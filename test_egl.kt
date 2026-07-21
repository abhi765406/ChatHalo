import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import android.view.Surface

fun test(eglContext: EglBase.Context, surface: Surface) {
    val renderer = EglRenderer("Test")
    renderer.init(eglContext, EglBase.CONFIG_PLAIN, GlRectDrawer())
    renderer.createEglSurface(surface)
}
