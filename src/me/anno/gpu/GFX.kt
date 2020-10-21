package me.anno.gpu

import me.anno.config.DefaultConfig
import me.anno.config.DefaultStyle.black
import me.anno.fonts.FontManager
import me.anno.gpu.ShaderLib.copyShader
import me.anno.gpu.ShaderLib.flatShader
import me.anno.gpu.ShaderLib.flatShaderGradient
import me.anno.gpu.ShaderLib.flatShaderTexture
import me.anno.gpu.ShaderLib.shader3D
import me.anno.gpu.ShaderLib.shader3DBlur
import me.anno.gpu.ShaderLib.shader3DCircle
import me.anno.gpu.ShaderLib.shader3DMasked
import me.anno.gpu.ShaderLib.shader3DPolygon
import me.anno.gpu.ShaderLib.shader3DRGBA
import me.anno.gpu.ShaderLib.shader3DSVG
import me.anno.gpu.ShaderLib.shader3DYUV
import me.anno.gpu.ShaderLib.subpixelCorrectTextShader
import me.anno.gpu.blending.BlendDepth
import me.anno.gpu.blending.BlendMode
import me.anno.gpu.buffer.SimpleBuffer
import me.anno.gpu.buffer.StaticBuffer
import me.anno.gpu.framebuffer.Frame
import me.anno.gpu.framebuffer.Framebuffer
import me.anno.gpu.shader.Shader
import me.anno.gpu.shader.ShaderPlus
import me.anno.gpu.texture.ClampMode
import me.anno.gpu.texture.FilteringMode
import me.anno.gpu.texture.NearestMode
import me.anno.gpu.texture.Texture2D
import me.anno.input.Input
import me.anno.input.Input.mouseX
import me.anno.input.Input.mouseY
import me.anno.input.MouseButton
import me.anno.objects.Camera
import me.anno.objects.Transform
import me.anno.objects.Video
import me.anno.objects.effects.MaskType
import me.anno.objects.geometric.Circle
import me.anno.objects.geometric.Polygon
import me.anno.objects.modes.UVProjection
import me.anno.studio.Build.isDebug
import me.anno.studio.RemsStudio
import me.anno.studio.RemsStudio.editorTime
import me.anno.studio.RemsStudio.editorTimeDilation
import me.anno.studio.RemsStudio.nullCamera
import me.anno.studio.RemsStudio.root
import me.anno.studio.RemsStudio.selectedInspectable
import me.anno.studio.RemsStudio.selectedTransform
import me.anno.studio.RemsStudio.targetHeight
import me.anno.studio.RemsStudio.targetWidth
import me.anno.studio.StudioBase.Companion.eventTasks
import me.anno.ui.base.Panel
import me.anno.ui.base.SpacePanel
import me.anno.ui.base.TextPanel
import me.anno.ui.base.components.Padding
import me.anno.ui.base.constraints.AxisAlignment
import me.anno.ui.base.constraints.WrapAlign
import me.anno.ui.base.groups.PanelGroup
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.scrolling.ScrollPanelY
import me.anno.ui.debug.FrameTimes
import me.anno.utils.clamp
import me.anno.utils.f1
import me.anno.utils.minus
import me.anno.video.VFrame
import org.apache.logging.log4j.LogManager
import org.joml.*
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose
import org.lwjgl.opengl.EXTTextureFilterAnisotropic
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30.*
import java.lang.Math
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*

// todo split the rendering in two parts:
// todo - without blending (no alpha, video or polygons)
// todo - with blending
// todo enqueue all objects for rendering
// todo sort blended objects by depth, if rendering with depth

// todo ffmpeg requires 100MB RAM per instance -> do we really need multiple instances, or does one work fine?
// todo or keep only a certain amount of ffmpeg instances running?

// todo gpu task priority? (low=destroy,rendering/medium=playback/high=ui)

object GFX : GFXBase1() {

    private val LOGGER = LogManager.getLogger(GFX::class)!!

    // for final rendering we need to use the GPU anyways;
    // so just use a static variable
    var isFinalRendering = false
    var drawMode = ShaderPlus.DrawMode.COLOR_SQUARED
    var supportsAnisotropicFiltering = false
    var anisotropy = 1f

    var maxFragmentUniforms = 0
    var maxVertexUniforms = 0

    var currentCamera = nullCamera

    var hoveredPanel: Panel? = null
    var hoveredWindow: Window? = null

    fun select(transform: Transform?) {
        if(selectedTransform != transform || selectedInspectable != transform){
            selectedInspectable = transform
            selectedTransform = transform
            RemsStudio.updateSceneViews()
        }
    }

    val gpuTasks = ConcurrentLinkedQueue<Task>()
    val audioTasks = ConcurrentLinkedQueue<Task>()

    fun addAudioTask(weight: Int, task: () -> Unit) {
        // could be optimized for release...
        audioTasks += weight to task
    }

    fun addGPUTask(w: Int, h: Int, task: () -> Unit) {
        gpuTasks += (w * h / 1e5).toInt() to task
    }

    fun addGPUTask(weight: Int, task: () -> Unit) {
        gpuTasks += weight to task
    }

    lateinit var gameInit: () -> Unit
    lateinit var gameLoop: (w: Int, h: Int) -> Boolean
    lateinit var onShutdown: () -> Unit

    val loadTexturesSync = Stack<Boolean>()

    init {
        loadTexturesSync.push(false)
    }

    var deltaX = 0
    var deltaY = 0

    var windowX = 0
    var windowY = 0
    var windowWidth = 0
    var windowHeight = 0

    val flat01 = SimpleBuffer.flat01

    val matrixBuffer = BufferUtils.createFloatBuffer(16)
    val matrixBufferFBX = BufferUtils.createFloatBuffer(16 * 256)

    var rawDeltaTime = 0f
    var deltaTime = 0f

    var currentEditorFPS = 60f

    var lastTime = System.nanoTime()

    var editorHoverTime = 0.0

    var smoothSin = 0.0
    var smoothCos = 0.0

    var drawnTransform: Transform? = null

    val menuSeparator = "-----"

    val inFocus = HashSet<Panel>()
    val inFocus0 get() = inFocus.firstOrNull()

    fun requestFocus(panel: Panel?, exclusive: Boolean) {
        if (exclusive) inFocus.clear()
        if (panel != null) inFocus += panel
    }

    fun clip(x: Int, y: Int, w: Int, h: Int, render: () -> Unit) {
        // from the bottom to the top
        check()
        if (w < 1 || h < 1) throw java.lang.RuntimeException("w < 1 || h < 1 not allowed, got $w x $h")
        val realY = height - (y + h)
        Frame(x, realY, w, h, false){
           render()
        }
    }

    fun clip(size: me.anno.gpu.size.WindowSize, render: () -> Unit) = clip(size.x, size.y, size.w, size.h, render)

    fun clip2(x0: Int, y0: Int, x1: Int, y1: Int, render: () -> Unit) = clip(x0, y0, x1 - x0, y1 - y0, render)

    lateinit var windowStack: Stack<Window>

    fun getPanelAndWindowAt(x: Float, y: Float) = getPanelAndWindowAt(x.toInt(), y.toInt())
    fun getPanelAndWindowAt(x: Int, y: Int): Pair<Panel, Window>? {
        for (root in windowStack.reversed()) {
            val panel = getPanelAt(root.panel, x, y)
            if (panel != null) return panel to root
        }
        return null
    }

    fun getPanelAt(x: Float, y: Float) = getPanelAt(x.toInt(), y.toInt())
    fun getPanelAt(x: Int, y: Int): Panel? {
        for (root in windowStack.reversed()) {
            val panel = getPanelAt(root.panel, x, y)
            if (panel != null) return panel
        }
        return null
    }

    fun getPanelAt(panel: Panel, x: Int, y: Int): Panel? {
        return if (panel.canBeSeen && (x - panel.x) in 0 until panel.w && (y - panel.y) in 0 until panel.h) {
            if (panel is PanelGroup) {
                for (child in panel.children.reversed()) {
                    val clickedByChild = getPanelAt(child, x, y)
                    if (clickedByChild != null) {
                        return clickedByChild
                    }
                }
            }
            panel
        } else null
    }

    fun requestExit() {
        glfwSetWindowShouldClose(window, true)
    }

    override fun addCallbacks() {
        super.addCallbacks()
        Input.initForGLFW()
    }

    fun drawRectGradient(x: Int, y: Int, w: Int, h: Int, lColor: Vector4f, rColor: Vector4f) {
        if (w == 0 || h == 0) return
        check()
        val shader = flatShaderGradient
        shader.use()
        posSize(shader, x, y, w, h)
        shader.v4("lColor", lColor)
        shader.v4("rColor", rColor)
        flat01.draw(shader)
        check()
    }

    fun drawRect(x: Int, y: Int, w: Int, h: Int, color: Vector4f) {
        if (w == 0 || h == 0) return
        check()
        val shader = flatShader
        shader.use()
        posSize(shader, x, y, w, h)
        shader.v4("color", color.x, color.y, color.z, color.w)
        flat01.draw(shader)
        check()
    }

    fun drawRect(x: Int, y: Int, w: Int, h: Int, color: Int) {
        if (w == 0 || h == 0) return
        check()
        val shader = flatShader
        shader.use()
        posSize(shader, x, y, w, h)
        shader.v4("color", color.r() / 255f, color.g() / 255f, color.b() / 255f, color.a() / 255f)
        flat01.draw(shader)
        check()
    }

    fun drawBorder(x: Int, y: Int, w: Int, h: Int, color: Int, size: Int){
        flatColor(color)
        drawRect(x, y, w, size)
        drawRect(x, y+h-size, w, size)
        drawRect(x, y+size, size, h-2*size)
        drawRect(x+w-size, y+size, size, h-2*size)
    }

    fun flatColor(color: Int) {
        val shader = flatShader
        shader.use()
        shader.v4("color", color.r() / 255f, color.g() / 255f, color.b() / 255f, color.a() / 255f)
    }

    fun drawRect(x: Int, y: Int, w: Int, h: Int) {
        if (w == 0 || h == 0) return
        val shader = flatShader
        shader.use()
        posSize(shader, x, y, w, h)
        flat01.draw(shader)
    }

    fun drawRect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        check()
        val shader = flatShader
        shader.use()
        posSize(shader, x, y, w, h)
        shader.v4("color", color.r() / 255f, color.g() / 255f, color.b() / 255f, color.a() / 255f)
        flat01.draw(shader)
        check()
    }

    // the background color is important for correct subpixel rendering, because we can't blend per channel
    fun drawText(
        x: Int, y: Int, font: String, fontSize: Int, bold: Boolean, italic: Boolean, text: String,
        color: Int, backgroundColor: Int, widthLimit: Int, centerX: Boolean = false
    ) =
        writeText(x, y, font, fontSize, bold, italic, text, color, backgroundColor, widthLimit, centerX)

    fun writeText(
        x: Int, y: Int,
        font: String, fontSize: Int,
        bold: Boolean, italic: Boolean,
        text: String,
        color: Int,
        backgroundColor: Int,
        widthLimit: Int,
        centerX: Boolean = false
    ): Pair<Int, Int> {

        check()
        val texture =
            FontManager.getString(font, fontSize.toFloat(), text, italic, bold, widthLimit) ?: return 0 to fontSize
        // check()
        val w = texture.w
        val h = texture.h
        if (text.isNotBlank()) {
            texture.bind(NearestMode.TRULY_NEAREST, ClampMode.CLAMP)
            val shader = subpixelCorrectTextShader
            // check()
            shader.use()
            var x2 = x
            if (centerX) x2 -= w / 2
            shader.v2("pos", (x2 - windowX).toFloat() / windowWidth, 1f - (y - windowY).toFloat() / windowHeight)
            shader.v2("size", w.toFloat() / windowWidth, -h.toFloat() / windowHeight)
            shader.v4("textColor", color.r() / 255f, color.g() / 255f, color.b() / 255f, color.a() / 255f)
            shader.v3(
                "backgroundColor",
                backgroundColor.r() / 255f,
                backgroundColor.g() / 255f,
                backgroundColor.b() / 255f
            )
            flat01.draw(shader)
            check()
        } else {
            drawRect(x, y, w, h, backgroundColor or black)
        }
        return w to h
    }

    // fun getTextSize(fontSize: Int, bold: Boolean, italic: Boolean, text: String) = getTextSize(defaultFont, fontSize, bold, italic, text)
    fun getTextSize(
        font: String,
        fontSize: Int,
        bold: Boolean,
        italic: Boolean,
        text: String,
        widthLimit: Int
    ): Pair<Int, Int> {
        // count how many spaces there are at the end
        // get accurate space and tab widths
        val spaceWidth = 0//text.endSpaceCount() * fontSize / 4
        val texture = FontManager.getString(font, fontSize.toFloat(), text, bold, italic, widthLimit)
            ?: return spaceWidth to fontSize
        return (texture.w + spaceWidth) to texture.h
    }

    fun drawTexture(x: Int, y: Int, w: Int, h: Int, texture: Texture2D, color: Int, tiling: Vector4f?) {
        check()
        val shader = flatShaderTexture
        shader.use()
        posSize(shader, x, y, w, h)
        shader.v4("color", color.r() / 255f, color.g() / 255f, color.b() / 255f, color.a() / 255f)
        if (tiling != null) shader.v4("tiling", tiling)
        else shader.v4("tiling", 1f, 1f, 0f, 0f)
        texture.bind(0, texture.nearest, texture.clampMode)
        flat01.draw(shader)
        check()
    }

    fun drawTexture(matrix: Matrix4fArrayList, w: Int, h: Int, texture: Texture2D, color: Int, tiling: Vector4f?) {
        matrix.scale(w.toFloat() / windowWidth, h.toFloat() / windowHeight, 1f)
        drawMode = ShaderPlus.DrawMode.COLOR
        draw3D(
            matrix, texture, color.v4(),
            FilteringMode.LINEAR, ClampMode.CLAMP, tiling, UVProjection.Planar
        )
    }

    fun drawTexture(w: Int, h: Int, texture: VFrame, color: Int, tiling: Vector4f?) {
        val matrix = Matrix4fArrayList()
        matrix.scale(w.toFloat() / windowWidth, h.toFloat() / windowHeight, 1f)
        drawMode = ShaderPlus.DrawMode.COLOR
        draw3D(
            matrix, texture, color.v4(),
            FilteringMode.LINEAR, ClampMode.CLAMP, tiling, UVProjection.Planar
        )
    }

    fun drawCircle(
        w: Int, h: Int, innerRadius: Float, startDegrees: Float, endDegrees: Float, color: Vector4f
    ) {
        // not perfect, but pretty good
        // anti-aliasing for the rough edges
        // not very economical, could be improved
        val matrix = Matrix4fArrayList()
        matrix.scale(w.toFloat() / windowWidth, h.toFloat() / windowHeight, 1f)
        drawMode = ShaderPlus.DrawMode.COLOR
        color.w /= 25f
        for (dx in 0 until 5) {
            for (dy in 0 until 5) {
                draw3DCircle(matrix, innerRadius, startDegrees, endDegrees, color)
            }
        }
    }

    fun posSize(shader: Shader, x: Int, y: Int, w: Int, h: Int) {
        shader.v2("pos", (x - windowX).toFloat() / windowWidth, 1f - (y - windowY).toFloat() / windowHeight)
        shader.v2("size", w.toFloat() / windowWidth, -h.toFloat() / windowHeight)
    }

    fun posSize(shader: Shader, x: Float, y: Float, w: Float, h: Float) {
        shader.v2("pos", (x - windowX) / windowWidth, 1f - (y - windowY) / windowHeight)
        shader.v2("size", w / windowWidth, -h / windowHeight)
    }

    fun applyCameraTransform(camera: Camera, time: Double, cameraTransform: Matrix4f, stack: Matrix4fArrayList) {
        val offset = camera.getEffectiveOffset(time)
        val cameraTransform2 = if (offset != 0f) {
            Matrix4f(cameraTransform).translate(0f, 0f, offset)
        } else cameraTransform
        val fov = camera.getEffectiveFOV(time, offset)
        val near = camera.getEffectiveNear(time, offset)
        val far = camera.getEffectiveFar(time, offset)
        val position = cameraTransform2.transformProject(Vector3f(0f, 0f, 0f))
        val up = cameraTransform2.transformProject(Vector3f(0f, 1f, 0f)) - position
        val lookAt = cameraTransform2.transformProject(Vector3f(0f, 0f, -1f))
        stack
            .perspective(
                Math.toRadians(fov.toDouble()).toFloat(),
                windowWidth * 1f / windowHeight, near, far
            )
            .lookAt(position, lookAt, up.normalize())
    }

    fun shader3DUniforms(
        shader: Shader, stack: Matrix4fArrayList,
        w: Int, h: Int, color: Vector4f,
        tiling: Vector4f?, filtering: FilteringMode,
        uvProjection: UVProjection?
    ) {
        check()

        shader.use()
        stack.pushMatrix()

        val doScale2 = (uvProjection?.doScale ?: true) && w != h

        shader.v1("filtering", filtering.id)
        shader.v2("textureDeltaUV", 1f / w, 1f / h)

        // val avgSize = sqrt(w * h.toFloat())
        if (doScale2) {
            val avgSize =
                if (w * targetHeight > h * targetWidth) w.toFloat() * targetHeight / targetWidth else h.toFloat()
            val sx = w / avgSize
            val sy = h / avgSize
            stack.scale(sx, -sy, 1f)
        } else {
            stack.scale(1f, -1f, 1f)
        }

        stack.get(matrixBuffer)
        GL20.glUniformMatrix4fv(shader["transform"], false, matrixBuffer)
        stack.popMatrix()

        shaderColor(shader, "tint", color)
        if (tiling != null) shader.v4("tiling", tiling)
        else shader.v4("tiling", 1f, 1f, 0f, 0f)
        shader.v1("drawMode", drawMode.id)
        shader.v1("uvProjection", uvProjection?.id ?: UVProjection.Planar.id)

    }


    fun shader3DUniforms(shader: Shader, stack: Matrix4f, color: Vector4f) {
        check()
        shader.use()
        stack.get(matrixBuffer)
        glUniformMatrix4fv(shader["transform"], false, matrixBuffer)
        shaderColor(shader, "tint", color)
        shader.v4("tiling", 1f, 1f, 0f, 0f)
        shader.v1("drawMode", drawMode.id)
    }

    fun transformUniform(shader: Shader, stack: Matrix4f) {
        check()
        shader.use()
        stack.get(matrixBuffer)
        glUniformMatrix4fv(shader["transform"], false, matrixBuffer)
    }

    fun shaderColor(shader: Shader, name: String, color: Vector4f) {
        if (drawMode == ShaderPlus.DrawMode.ID) {
            val id = drawnTransform!!.clickId
            shader.v4(name, id.b() / 255f, id.g() / 255f, id.r() / 255f, 1f)
        } else {
            shader.v4(name, color.x, color.y, color.z, color.w)
        }
    }

    fun toRadians(f: Float) = Math.toRadians(f.toDouble()).toFloat()
    fun toRadians(f: Double) = Math.toRadians(f)

    fun draw3DCircle(
        stack: Matrix4fArrayList,
        innerRadius: Float,
        startDegrees: Float,
        endDegrees: Float,
        color: Vector4f
    ) {
        val shader = shader3DCircle.shader
        shader3DUniforms(shader, stack, 1, 1, color, null, FilteringMode.NEAREST, null)
        var a0 = startDegrees
        var a1 = endDegrees
        // if the two arrows switch sides, flip the circle
        if (a0 > a1) {// first start for checker pattern
            val tmp = a0
            a0 = a1
            a1 = tmp - 360f
        }
        // fix edge resolution loss
        if (a0 > a1 + 360) {
            a0 = a1 + 360
        } else if (a1 > a0 + 360) {
            a1 = a0 + 360
        }
        val angle0 = toRadians(a0)
        val angle1 = toRadians(a1)
        shader.v3("circleParams", 1f - innerRadius, angle0, angle1)
        Circle.drawBuffer(shader)
        check()
    }

    fun draw3DBlur(
        stack: Matrix4fArrayList,
        size: Float, w: Int, h: Int, isFirst: Boolean
    ) {
        val shader = shader3DBlur
        transformUniform(shader, stack)
        if(isFirst) shader.v2("stepSize", 0f, 1f / h)
        else shader.v2("stepSize", 1f / w, 0f)
        shader.v1("steps", size * h)
        flat01.draw(shader)
        check()
    }

    fun copy(){
        check()
        val shader = copyShader
        flat01.draw(shader)
        check()
    }

    fun copyNoAlpha(){
        check()
        BlendDepth(BlendMode.DST_ALPHA, false).use {
            val shader = copyShader
            flat01.draw(shader)
        }
        check()
    }

    fun draw3DMasked(
        stack: Matrix4fArrayList, color: Vector4f,
        maskType: MaskType,
        useMaskColor: Float,
        pixelSize: Float,
        isInverted: Float
    ) {
        val shader = shader3DMasked.shader
        shader3DUniforms(shader, stack, color)
        shader.v1("useMaskColor", useMaskColor)
        shader.v1("invertMask", isInverted)
        shader.v1("maskType", maskType.id)
        shader.v2("pixelating", pixelSize * windowHeight / windowWidth, pixelSize)
        flat01.draw(shader)
        check()
    }

    fun draw3D(
        stack: Matrix4fArrayList, buffer: StaticBuffer, texture: Texture2D, w: Int, h: Int, color: Vector4f,
        filtering: FilteringMode, clampMode: ClampMode, tiling: Vector4f?
    ) {
        val shader = shader3D.shader
        shader3DUniforms(shader, stack, w, h, color, tiling, filtering, null)
        texture.bind(0, filtering, clampMode)
        buffer.draw(shader)
        check()
    }

    fun draw3D(
        stack: Matrix4fArrayList, buffer: StaticBuffer, texture: Texture2D, color: Vector4f,
        filtering: FilteringMode, clampMode: ClampMode, tiling: Vector4f?
    ) {
        draw3D(stack, buffer, texture, texture.w, texture.h, color, filtering, clampMode, tiling)
    }

    fun colorGradingUniforms(video: Video, time: Double, shader: Shader) {
        shader.v3("cgOffset", video.cgOffset[time])
        shader.v3X("cgSlope", video.cgSlope[time])
        shader.v3X("cgPower", video.cgPower[time])
        shader.v1("cgSaturation", video.cgSaturation[time])
    }

    fun draw3DPolygon(
        polygon: Polygon, time: Double,
        stack: Matrix4fArrayList, buffer: StaticBuffer,
        texture: Texture2D, color: Vector4f,
        inset: Float,
        filtering: FilteringMode, clampMode: ClampMode
    ) {
        val shader = shader3DPolygon.shader
        shader.use()
        polygon.uploadAttractors(shader, time)
        shader3DUniforms(shader, stack, texture.w, texture.h, color, null, filtering, null)
        shader.v1("inset", inset)
        texture.bind(0, filtering, clampMode)
        buffer.draw(shader)
        check()
    }

    fun draw3D(
        stack: Matrix4fArrayList, texture: VFrame, color: Vector4f,
        filtering: FilteringMode, clampMode: ClampMode, tiling: Vector4f?, uvProjection: UVProjection
    ) {
        if (!texture.isLoaded) throw RuntimeException("Frame must be loaded to be rendered!")
        val shader = texture.get3DShader().shader
        shader3DUniforms(shader, stack, texture.w, texture.h, color, tiling, filtering, uvProjection)
        texture.bind(0, filtering, clampMode)
        if (shader == shader3DYUV.shader) {
            val w = texture.w
            val h = texture.h
            shader.v2("uvCorrection", w.toFloat() / ((w + 1) / 2 * 2), h.toFloat() / ((h + 1) / 2 * 2))
        }
        uvProjection.getBuffer().draw(shader)
        check()
    }

    fun draw3DVideo(
        video: Video, time: Double,
        stack: Matrix4fArrayList, texture: VFrame, color: Vector4f,
        filtering: FilteringMode, clampMode: ClampMode, tiling: Vector4f?, uvProjection: UVProjection
    ) {
        if (!texture.isLoaded) throw RuntimeException("Frame must be loaded to be rendered!")
        val shader = texture.get3DShader().shader
        shader.use()
        video.uploadAttractors(shader, time)
        shader3DUniforms(shader, stack, texture.w, texture.h, color, tiling, filtering, uvProjection)
        colorGradingUniforms(video, time, shader)
        texture.bind(0, filtering, clampMode)
        if (shader == shader3DYUV.shader) {
            val w = texture.w
            val h = texture.h
            shader.v2("uvCorrection", w.toFloat() / ((w + 1) / 2 * 2), h.toFloat() / ((h + 1) / 2 * 2))
        }
        uvProjection.getBuffer().draw(shader)
        check()
    }

    fun draw2D(texture: VFrame) {

        if (!texture.isLoaded) throw RuntimeException("Frame must be loaded to be rendered!")
        val shader = texture.get3DShader().shader

        check()

        shader.use()
        shader.v1("filtering", FilteringMode.LINEAR.id)
        shader.v2("textureDeltaUV", 1f / texture.w, 1f / texture.h)
        Matrix4f().get(matrixBuffer)
        GL20.glUniformMatrix4fv(shader["transform"], false, matrixBuffer)
        shader.v4("tint", 1f, 1f, 1f, 1f)
        shader.v4("tiling", 1f, 1f, 0f, 0f)
        shader.v1("drawMode", ShaderPlus.DrawMode.COLOR.id)
        shader.v1("uvProjection", UVProjection.Planar.id)

        texture.bind(0, FilteringMode.LINEAR, ClampMode.CLAMP)
        if (shader == shader3DYUV.shader) {
            val w = texture.w
            val h = texture.h
            shader.v2("uvCorrection", w.toFloat() / ((w + 1) / 2 * 2), h.toFloat() / ((h + 1) / 2 * 2))
        }

        UVProjection.Planar.getBuffer().draw(shader)
        check()

    }

    fun draw3D(
        stack: Matrix4fArrayList, texture: Texture2D, color: Vector4f,
        filtering: FilteringMode, clampMode: ClampMode, tiling: Vector4f?, uvProjection: UVProjection
    ) {
        draw3D(stack, texture, texture.w, texture.h, color, filtering, clampMode, tiling, uvProjection)
    }

    fun draw3D(
        stack: Matrix4fArrayList, texture: Texture2D, w: Int, h: Int, color: Vector4f,
        filtering: FilteringMode, clampMode: ClampMode, tiling: Vector4f?, uvProjection: UVProjection
    ) {
        val shader = shader3D.shader
        shader3DUniforms(shader, stack, w, h, color, tiling, filtering, uvProjection)
        texture.bind(0, filtering, clampMode)
        uvProjection.getBuffer().draw(shader)
        check()
    }

    fun draw3DVideo(
        video: Video, time: Double,
        stack: Matrix4fArrayList, texture: Texture2D, color: Vector4f,
        filtering: FilteringMode, clampMode: ClampMode, tiling: Vector4f?, uvProjection: UVProjection
    ) {
        val shader = shader3DRGBA.shader
        shader.use()
        video.uploadAttractors(shader, time)
        shader3DUniforms(shader, stack, texture.w, texture.h, color, tiling, filtering, uvProjection)
        colorGradingUniforms(video, time, shader)
        texture.bind(0, filtering, clampMode)
        uvProjection.getBuffer().draw(shader)
        check()
    }

    fun draw3DSVG(
        stack: Matrix4fArrayList, buffer: StaticBuffer, texture: Texture2D, color: Vector4f,
        filtering: FilteringMode, clampMode: ClampMode
    ) {
        val shader = shader3DSVG.shader
        shader3DUniforms(shader, stack, texture.w, texture.h, color, null, filtering, null)
        texture.bind(0, filtering, clampMode)
        buffer.draw(shader)
        check()
    }

    override fun renderStep0() {
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1) // opengl is evil ;), for optimizations, we might set it back
        supportsAnisotropicFiltering = GL.getCapabilities().GL_EXT_texture_filter_anisotropic
        LOGGER.info("OpenGL supports Anisotropic Filtering? $supportsAnisotropicFiltering")
        if (supportsAnisotropicFiltering) {
            val max = glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT)
            anisotropy = min(max, DefaultConfig["gpu.filtering.anisotropic.max", 16f])
        }
        maxVertexUniforms = glGetInteger(GL_MAX_VERTEX_UNIFORM_COMPONENTS)
        maxFragmentUniforms = glGetInteger(GL20.GL_MAX_FRAGMENT_UNIFORM_COMPONENTS)
        LOGGER.info("Max Uniform Components: [Vertex: $maxVertexUniforms, Fragment: $maxFragmentUniforms]")
        TextureLib.init()
        ShaderLib.init()
        setIcon()
    }

    fun workQueue(queue: ConcurrentLinkedQueue<Task>) {
        // async work section

        // work 1/5th of the tasks by weight...

        // changing to 10 doesn't make the frame rate smoother :/
        val framesForWork = 5

        val workTodo = max(1000, queue.sumBy { it.first } / framesForWork)
        var workDone = 0
        val workTime0 = System.nanoTime()
        while (true) {
            val nextTask = queue.poll() ?: break
            nextTask.second()
            workDone += nextTask.first
            if (workDone >= workTodo) break
            val workTime1 = System.nanoTime()
            val workTime = abs(workTime1 - workTime0) * 1e-9f
            if (workTime * 60f > 1f) break // too much work
        }

    }

    fun clearStack() {
        Framebuffer.stack.clear()
    }

    fun ensureEmptyStack() {
        if (Framebuffer.stack.size > 0) {
            /*Framebuffer.stack.forEach {
                println(it)
            }
            throw RuntimeException("Catched ${Framebuffer.stack.size} items on the Framebuffer.stack")
            exitProcess(1)*/
        }
        Framebuffer.stack.clear()
    }

    fun workGPUTasks() {
        workQueue(gpuTasks)
    }

    override fun renderStep() {

        ensureEmptyStack()

        // Framebuffer.bindNull()

        workGPUTasks()

        // Framebuffer.stack.pop()

        ensureEmptyStack()

        // rendering and editor section

        updateTime()

        // updating the local times must be done before the events, because
        // the worker thread might have invalidated those
        updateLastLocalTime(root, editorTime)

        while (eventTasks.isNotEmpty()) {
            try {
                eventTasks.poll()!!.invoke()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        Texture2D.textureBudgetUsed = 0

        check()

        glBindTexture(GL_TEXTURE_2D, 0)

        // glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        BlendDepth.reset()
        BlendDepth(BlendMode.DEFAULT, false).bind()

        glDisable(GL_CULL_FACE)
        glDisable(GL_ALPHA_TEST)

        check()

        ensureEmptyStack()

        gameLoop(width, height)

        ensureEmptyStack()

        check()

    }

    fun updateLastLocalTime(parent: Transform, time: Double) {
        val localTime = parent.getLocalTime(time)
        parent.lastLocalTime = localTime
        parent.children.forEach { child ->
            updateLastLocalTime(child, localTime)
        }
    }

    fun updateTime() {

        val thisTime = System.nanoTime()
        rawDeltaTime = (thisTime - lastTime) * 1e-9f
        deltaTime = min(rawDeltaTime, 0.1f)
        FrameTimes.putValue(rawDeltaTime)

        val newFPS = 1f / rawDeltaTime
        currentEditorFPS = min(currentEditorFPS + (newFPS - currentEditorFPS) * 0.05f, newFPS)
        lastTime = thisTime

        editorTime = max(editorTime + deltaTime * editorTimeDilation, 0.0)
        if (editorTime == 0.0 && editorTimeDilation < 0.0) {
            editorTimeDilation = 0.0
        }

        smoothSin = sin(editorTime)
        smoothCos = cos(editorTime)

    }

    fun openMenuComplex(
        x: Int,
        y: Int,
        title: String,
        options: List<Pair<String, (button: MouseButton, isLong: Boolean) -> Boolean>>
    ) {
        loadTexturesSync.push(true) // to calculate the correct size, which is needed for correct placement
        if (options.isEmpty()) return
        val style = DefaultConfig.style.getChild("menu")
        val list = PanelListY(style)
        list += WrapAlign.LeftTop
        val container = ScrollPanelY(list, Padding(1), style, AxisAlignment.MIN)
        container += WrapAlign.LeftTop
        lateinit var window: Window
        fun close() {
            windowStack.remove(window)
        }

        val padding = 4
        if (title.isNotEmpty()) {
            val titlePanel = TextPanel(title, style)
            titlePanel.padding.left = padding
            titlePanel.padding.right = padding
            list += titlePanel
            list += SpacePanel(0, 1, style)
        }
        for ((index, element) in options.withIndex()) {
            val (name, action) = element
            if (name == menuSeparator) {
                if (index != 0) {
                    list += SpacePanel(0, 1, style)
                }
            } else {
                val buttonView = TextPanel(name, style)
                buttonView.setOnClickListener { _, _, button, long ->
                    if (action(button, long)) {
                        close()
                    }
                }
                buttonView.enableHoverColor = true
                buttonView.padding.left = padding
                buttonView.padding.right = padding
                list += buttonView
            }
        }
        val maxWidth = max(300, GFX.width)
        val maxHeight = max(300, GFX.height)
        container.calculateSize(maxWidth, maxHeight)
        container.applyPlacement(min(container.minW, maxWidth), min(container.minH, maxHeight))
        // ("size for window: ${container.w} ${container.h}")
        val wx = clamp(x, 0, max(GFX.width - container.w, 0))
        val wy = clamp(y, 0, max(GFX.height - container.h, 0))

        // LOGGER.debug(container.listOfAll.joinToString { "${it.style.prefix}/.../${it.style.suffix}" })

        window = Window(container, false, wx, wy)
        windowStack.add(window)
        loadTexturesSync.pop()
    }

    fun openMenuComplex(
        x: Float,
        y: Float,
        title: String,
        options: List<Pair<String, (button: MouseButton, isLong: Boolean) -> Boolean>>,
        delta: Int = 10
    ) {
        openMenuComplex(x.roundToInt() - delta, y.roundToInt() - delta, title, options)
    }

    fun openMenu(options: List<Pair<String, () -> Any>>) {
        openMenu(mouseX, mouseY, "", options)
    }

    fun openMenu(title: String, options: List<Pair<String, () -> Any>>) {
        openMenu(mouseX, mouseY, title, options)
    }

    fun openMenu(x: Int, y: Int, title: String, options: List<Pair<String, () -> Any>>, delta: Int = 10) {
        return openMenu(x.toFloat(), y.toFloat(), title, options, delta)
    }

    fun openMenu(x: Float, y: Float, title: String, options: List<Pair<String, () -> Any>>, delta: Int = 10) {
        openMenuComplex(x.roundToInt() - delta, y.roundToInt() - delta, title, options.map { (key, value) ->
            Pair(key, { b: MouseButton, _: Boolean ->
                if (b.isLeft) {
                    value(); true
                } else false
            })
        })
    }

    var glThread: Thread? = null
    fun check() {
        if (isDebug) {
            val currentThread = Thread.currentThread()
            if (currentThread != glThread) {
                if (glThread == null) {
                    glThread = currentThread
                    currentThread.name = "OpenGL"
                } else {
                    throw RuntimeException("GFX.check() called from wrong thread! Always use GFX.addGPUTask { ... }")
                }
            }
            val error = glGetError()
            if (error != 0) {
                Framebuffer.stack.forEach {
                    LOGGER.info(it.toString())
                }
                throw RuntimeException(
                    "GLException: ${when (error) {
                        1280 -> "invalid enum"
                        1281 -> "invalid value"
                        1282 -> "invalid operation"
                        1283 -> "stack overflow"
                        1284 -> "stack underflow"
                        1285 -> "out of memory"
                        1286 -> "invalid framebuffer operation"
                        else -> "$error"
                    }}"
                )
            }
        }
    }

    fun msg(title: String) {
        openMenu(listOf(title to {}))
    }

    fun ask(question: String, onYes: () -> Unit) {
        openMenu(mouseX, mouseY, question, listOf(
            "Yes" to onYes,
            "No" to {}
        ))
    }

    fun ask(question: String, onYes: () -> Unit, onNo: () -> Unit) {
        openMenu(
            mouseX, mouseY, question, listOf(
                "Yes" to onYes,
                "No" to onNo
            )
        )
    }

    fun showFPS() {
        val x0 = max(0, GFX.width - FrameTimes.width)
        val y0 = max(0, GFX.height - FrameTimes.height)
        FrameTimes.place(x0, y0, FrameTimes.width, FrameTimes.height)
        FrameTimes.draw()
        loadTexturesSync.push(true)
        drawText(x0 + 1, y0 + 1, "Consolas", 12, false, false, "${currentEditorFPS.f1()}, min: ${(1f/FrameTimes.maxValue).f1()}",
            FrameTimes.textColor, FrameTimes.backgroundColor, -1)
        loadTexturesSync.pop()
    }

}