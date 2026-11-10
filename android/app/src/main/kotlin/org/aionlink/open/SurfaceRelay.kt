package org.aionlink.open

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong

/**
 * GL relay between an external producer (Open Headunit's decoder) and AionLink's encoder input surface.
 * A decoder rendering 1920x1080 frames straight into a 1920x960 encoder surface produced no encoded output on the S25
 * (6 Nov 2026), so frames are received on our own SurfaceTexture and redrawn, scaled to the encoder size, with
 * monotonic presentation times. The producer's frame fills the whole output (independent X/Y scale), matching the
 * proportional touch mapping used by the bridge.
 */
class SurfaceRelay(private val output: Surface, private val width: Int, private val height: Int) : AutoCloseable {
    private val thread = HandlerThread("AionLink-relay").apply { start() }
    private val handler = Handler(thread.looper)
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var texture = 0
    private var program = 0
    private var texture2: SurfaceTexture? = null
    private val matrix = FloatArray(16)
    private val drawn = AtomicLong()
    @Volatile private var closed = false

    /** Surface to hand to the producer. Valid after construction. */
    lateinit var input: Surface
        private set
    val framesDrawn get() = drawn.get()

    /** Draw one of every [frameDivisor] producer frames (1 = all); the others are consumed and dropped. Thermal relief. */
    @Volatile var frameDivisor = 1
    private var received = 0L

    init {
        val ready = CountDownLatch(1)
        var failure: Throwable? = null
        handler.post {
            try { setUp() } catch (error: Throwable) { failure = error } finally { ready.countDown() }
        }
        ready.await()
        failure?.let { close(); throw IllegalStateException("No se pudo preparar el relé GL", it) }
    }

    private fun setUp() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize" }
        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL_RECORDABLE_ANDROID, 1, EGL14.EGL_NONE)
        val configs = arrayOfNulls<EGLConfig>(1); val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) { "eglChooseConfig" }
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        eglSurface = EGL14.eglCreateWindowSurface(display, configs[0], output, intArrayOf(EGL14.EGL_NONE), 0)
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "eglMakeCurrent" }
        program = buildProgram()
        val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0); texture = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        val surfaceTexture = SurfaceTexture(texture)
        // The producer may choose its own buffer size; this is only the default before it does.
        surfaceTexture.setDefaultBufferSize(width, height)
        surfaceTexture.setOnFrameAvailableListener({ draw() }, handler)
        texture2 = surfaceTexture
        input = Surface(surfaceTexture)
    }

    private fun draw() {
        if (closed) return
        val surfaceTexture = texture2 ?: return
        surfaceTexture.updateTexImage()
        if (received++ % frameDivisor.coerceAtLeast(1) != 0L) return
        surfaceTexture.getTransformMatrix(matrix)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTex"), 1, false, matrix, 0)
        val position = GLES20.glGetAttribLocation(program, "aPos")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, QUAD)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        // Monotonic encoder timestamps, independent of the producer's stream clock.
        EGLExt.eglPresentationTimeANDROID(display, eglSurface, System.nanoTime())
        EGL14.eglSwapBuffers(display, eglSurface)
        drawn.incrementAndGet()
    }

    private fun buildProgram(): Int {
        fun shader(type: Int, source: String) = GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, source); GLES20.glCompileShader(it)
        }
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, shader(GLES20.GL_VERTEX_SHADER, VERTEX))
            GLES20.glAttachShader(it, shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT))
            GLES20.glLinkProgram(it)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        val done = CountDownLatch(1)
        handler.post {
            try {
                if (::input.isInitialized) input.release()
                texture2?.release(); texture2 = null
                if (display != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
                    if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                    EGL14.eglTerminate(display)
                }
            } finally { done.countDown() }
        }
        done.await(); thread.quitSafely()
    }

    private companion object {
        const val EGL_RECORDABLE_ANDROID = 0x3142
        val QUAD = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            .put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)).apply { position(0) }
        const val VERTEX = "attribute vec2 aPos; uniform mat4 uTex; varying vec2 vTex;" +
            "void main() { gl_Position = vec4(aPos, 0.0, 1.0); vTex = (uTex * vec4((aPos + 1.0) * 0.5, 0.0, 1.0)).xy; }"
        const val FRAGMENT = "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float; varying vec2 vTex; uniform samplerExternalOES sTex;" +
            "void main() { gl_FragColor = texture2D(sTex, vTex); }"
    }
}
