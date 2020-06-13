package me.anno.gpu

import me.anno.config.DefaultConfig
import me.anno.config.DefaultStyle.black
import me.anno.fonts.FontManager
import me.anno.gpu.buffer.SimpleBuffer
import me.anno.gpu.buffer.StaticFloatBuffer
import me.anno.gpu.framebuffer.Framebuffer
import me.anno.gpu.size.WindowSize
import me.anno.gpu.texture.Texture2D
import me.anno.input.Input
import me.anno.input.Input.isShiftDown
import me.anno.objects.Camera
import me.anno.objects.Transform
import me.anno.objects.animation.AnimatedProperty
import me.anno.objects.blending.BlendMode
import me.anno.studio.Studio.editorTimeDilation
import me.anno.studio.Studio.eventTasks
import me.anno.studio.Studio.targetHeight
import me.anno.studio.Studio.targetWidth
import me.anno.ui.base.Panel
import me.anno.ui.base.scrolling.ScrollPanelY
import me.anno.ui.base.SpacePanel
import me.anno.ui.base.TextPanel
import me.anno.ui.base.components.Padding
import me.anno.ui.base.constraints.AxisAlignment
import me.anno.ui.base.constraints.WrapAlign
import me.anno.ui.base.groups.PanelGroup
import me.anno.ui.base.groups.PanelListY
import me.anno.utils.clamp
import me.anno.utils.f1
import me.anno.utils.minus
import me.anno.video.Frame
import org.joml.Matrix4f
import org.joml.Matrix4fStack
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.EXTTextureFilterAnisotropic
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30.*
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*

// todo split the rendering in two parts:
// todo - without blending (no alpha, video or polygons)
// todo - with blending
// todo enqueue all objects for rendering
// todo sort blended objects by depth, if rendering with depth

object GFX: GFXBase1() {

    // for final rendering we need to use the GPU anyways;
    // so just use a static variable
    var isFinalRendering = false
    var supportsAnisotropicFiltering = false
    var anisotropy = 1f

    val nullCamera = Camera(null)

    init {
        nullCamera.name = "Inspector Camera"
        nullCamera.onlyShowTarget = false
        // higher far value to allow other far values to be seen
        nullCamera.farZ.addKeyframe(0f, 5000f, 1f)
        nullCamera.timeDilation = 0f // the camera has no time, so no motion can be recorded
    }

    var root = Transform()
    var selectedCamera = nullCamera
    var selectedTransform: Transform? = null
    var selectedProperty: AnimatedProperty<*>? = null

    var hoveredPanel: Panel? = null
    var hoveredWindow: Window? = null

    fun select(transform: Transform?){
        selectedTransform = transform
        if(isShiftDown && transform is Camera){
            selectedCamera = transform
        }
    }

    val workerTasks = ConcurrentLinkedQueue<() -> Int>()

    fun addTask(task: () -> Int){
        workerTasks += task
    }

    lateinit var gameInit: () -> Unit
    lateinit var gameLoop: (w: Int, h: Int) -> Boolean
    lateinit var shutdown: () -> Unit

    var windowX = 0
    var windowY = 0
    var windowWidth = 0
    var windowHeight = 0
    val windowSize get() = WindowSize(windowX, windowY, windowWidth, windowHeight)

    val flat01 = SimpleBuffer.flat01
    // val defaultFont = DefaultConfig["font"]?.toString() ?: "Verdana"
    val matrixBuffer = BufferUtils.createFloatBuffer(16)

    lateinit var flatShader: Shader
    lateinit var flatShaderTexture: Shader
    lateinit var subpixelCorrectTextShader: Shader
    lateinit var shader3D: Shader
    lateinit var shader3DPolygon: Shader
    lateinit var shader3DYUV: Shader
    lateinit var shader3DARGB: Shader
    lateinit var shader3DBGRA: Shader
    lateinit var shader3DCircle: Shader
    lateinit var shader3DSVG: Shader
    lateinit var shader3DXYZUV: Shader
    lateinit var shader3DSpherical: Shader
    lateinit var lineShader3D: Shader
    lateinit var shader3DMasked: Shader

    val invisibleTexture = Texture2D(1, 1)
    val whiteTexture = Texture2D(1, 1)
    val stripeTexture = Texture2D(5, 1)
    val colorShowTexture = Texture2D(2,2)

    var rawDeltaTime = 0f
    var deltaTime = 0f

    var editorVideoFPS = 10f
    var currentEditorFPS = 60f

    var lastTime = System.nanoTime() - (editorVideoFPS * 1e9).toLong() // to prevent wrong fps ;)

    var panelCtr = 0

    var editorTime = 0f
    var editorHoverTime = 0f

    var smoothSin = 0f
    var smoothCos = 0f

    val menuSeparator = "-----"

    val inFocus = HashSet<Panel>()
    val inFocus0 get() = inFocus.firstOrNull()

    fun requestFocus(panel: Panel?, exclusive: Boolean){
        if(exclusive) inFocus.clear()
        if(panel != null) inFocus += panel
    }

    fun clip(x: Int, y: Int, w: Int, h: Int){
        // from the bottom to the top
        check()
        if(w < 1 || h < 1) throw java.lang.RuntimeException("w < 1 || h < 1 not allowed, got $w x $h")
        GL11.glViewport(x, height-y-h, w, h)
        check()
        // default
        windowX = x
        windowY = y
        windowWidth = w
        windowHeight = h
    }

    fun clip(size: me.anno.gpu.size.WindowSize) = clip(size.x, size.y, size.w, size.h)

    fun clip2(x1: Int, y1: Int, x2: Int, y2: Int) = clip(x1,y1,x2-x1,y2-y1)

    lateinit var windowStack: Stack<Window>

    fun getPanelAndWindowAt(x: Float, y: Float) = getPanelAndWindowAt(x.toInt(), y.toInt())
    fun getPanelAndWindowAt(x: Int, y: Int): Pair<Panel, Window>? {
        for(root in windowStack.reversed()){
            val panel = getPanelAt(root.panel, x, y)
            if(panel != null) return panel to root
        }
        return null
    }

    fun getPanelAt(x: Float, y: Float) = getPanelAt(x.toInt(), y.toInt())
    fun getPanelAt(x: Int, y: Int): Panel? {
        for(root in windowStack.reversed()){
            val panel = getPanelAt(root.panel, x, y)
            if(panel != null) return panel
        }
        return null
    }

    fun getPanelAt(panel: Panel, x: Int, y: Int): Panel? {
        return if(panel.isVisible && (x - panel.x) in 0 until panel.w && (y - panel.y) in 0 until panel.h){
            if(panel is PanelGroup){
                for(child in panel.children.reversed()){
                    val clickedByChild = getPanelAt(child,x,y)
                    if(clickedByChild != null){
                        return clickedByChild
                    }
                }
            }
            panel
        } else null
    }

    fun requestExit(){
        glfwSetWindowShouldClose(window, true)
    }

    override fun addCallbacks() {
        super.addCallbacks()
        Input.initForGLFW()
    }

    fun drawRect(x: Int, y: Int, w: Int, h: Int, color: Int){
        if(w == 0 || h == 0) return
        check()
        val shader = flatShader
        shader.use()
        posSize(shader, x, y, w, h)
        shader.v4("color", color.r()/255f, color.g()/255f, color.b()/255f, color.a()/255f)
        flat01.draw(shader)
        check()
    }

    // the background color is important for correct subpixel rendering, because we can't blend per channel
    fun drawText(x: Int, y: Int, font: String, fontSize: Int, bold: Boolean, italic: Boolean, text: String, color: Int, backgroundColor: Int) =
        writeText(x, y, font, fontSize, bold, italic, text, color, backgroundColor)
    fun writeText(x: Int, y: Int,
                  font: String, fontSize: Int,
                  bold: Boolean, italic: Boolean,
                  text: String,
                  color: Int, backgroundColor: Int): Pair<Int, Int> {

        check()
        val texture = FontManager.getString(font, fontSize.toFloat(), text, italic, bold) ?: return 0 to fontSize
        check()
        val w = texture.w
        val h = texture.h
        if(text.isNotBlank()){
            texture.bind(true)
            check()
            subpixelCorrectTextShader.use()
            check()
            subpixelCorrectTextShader.v2("pos", (x-windowX).toFloat()/windowWidth, 1f-(y-windowY).toFloat()/windowHeight)
            subpixelCorrectTextShader.v2("size", w.toFloat()/windowWidth, -h.toFloat()/windowHeight)
            subpixelCorrectTextShader.v4("textColor", color.r()/255f, color.g()/255f, color.b()/255f, color.a()/255f)
            subpixelCorrectTextShader.v3("backgroundColor", backgroundColor.r()/255f, backgroundColor.g()/255f, backgroundColor.b()/255f)
            flat01.draw(subpixelCorrectTextShader)
            check()
        } else {
            drawRect(x,y,w,h,backgroundColor or black)
        }
        return w to h
    }

    fun drawTexture(x: Int, y: Int, w: Int, h: Int, texture: Texture2D, color: Int){
        check()
        val shader = flatShaderTexture
        shader.use()
        posSize(shader, x, y, w, h)
        shader.v4("color", color.r()/255f, color.g()/255f, color.b()/255f, color.a()/255f)
        texture.bind(0, texture.isFilteredNearest)
        flat01.draw(shader)
        check()
    }

    fun posSize(shader: Shader, x: Int, y: Int, w: Int, h: Int){
        shader.v2("pos", (x-windowX).toFloat()/windowWidth, 1f-(y-windowY).toFloat()/windowHeight)
        shader.v2("size", w.toFloat()/windowWidth, -h.toFloat()/windowHeight)
    }

    fun applyCameraTransform(camera: Camera, time: Float, cameraTransform: Matrix4f, stack: Matrix4fStack){
        val position = cameraTransform.transformProject(Vector3f(0f, 0f, 0f))
        val up = cameraTransform.transformProject(Vector3f(0f, 1f, 0f)) - position
        val lookAt = cameraTransform.transformProject(Vector3f(0f, 0f, -1f))
        stack
            .perspective(
                Math.toRadians(camera.fovYDegrees.getValueAt(time).toDouble()).toFloat(),
                windowWidth*1f/windowHeight,
                camera.nearZ.getValueAt(time),
                camera.farZ.getValueAt(time))
            .lookAt(position, lookAt, up.normalize())
    }

    fun shader3DUniforms(shader: Shader, stack: Matrix4fStack, w: Int, h: Int, color: Vector4f, isBillboard: Float, tiling: Vector4f?){
        check()

        stack.pushMatrix()
        shader.use()

        val avgSize = if(w * targetHeight > h * targetWidth) w.toFloat() * targetHeight / targetWidth  else h.toFloat()
        // val avgSize = sqrt(w * h.toFloat())
        val sx = w / avgSize
        val sy = h / avgSize
        stack.scale(sx, -sy, 1f)

        stack.get(matrixBuffer)
        GL20.glUniformMatrix4fv(shader["transform"], false, matrixBuffer)
        stack.popMatrix()

        val scale = stack.transformDirection(Vector3f(1f, 1f, 1f)).length()
        shader.v2("billboardSize", scale * windowHeight/windowWidth * w/h, -scale)
        shader.v4("tint", color.x, color.y, color.z, color.w)
        if(tiling != null) shader.v4("tiling", tiling)
        else shader.v4("tiling", 1f, 1f, 0f, 0f)
        shader.v1("isBillboard", isBillboard)
    }

    fun toRadians(f: Float) = Math.toRadians(f.toDouble()).toFloat()

    fun positiveFract(a: Float, b: Float): Float {
        val f = a % b
        return if(f < 0f) b + f
        else f
    }

    fun draw3DCircle(stack: Matrix4fStack, innerRadius: Float, startDegrees: Float, endDegrees: Float, color: Vector4f, isBillboard: Float){
        val shader = shader3DCircle
        shader3DUniforms(shader, stack, 1, 1, color, isBillboard, null)
        val angle1 = toRadians(positiveFract(startDegrees+180f, 360f)-180f)
        val angle2 = toRadians(positiveFract(endDegrees+180f, 360f)-180f)
        shader.v3("circleParams", innerRadius * innerRadius, angle1, angle2)
        flat01.draw(shader)
        check()
    }

    fun draw3DMasked(stack: Matrix4fStack, texture: Texture2D, mask: Texture2D, color: Vector4f,
                     isBillboard: Float, nearestFiltering: Boolean, useMaskColor: Float, offsetColor: Vector4f,
                     isInverted: Float){
        val shader = shader3DMasked
        shader3DUniforms(shader, stack, 1, 1, color, isBillboard, null)
        shader.v4("offsetColor", offsetColor.x, offsetColor.y, offsetColor.z, offsetColor.w)
        shader.v1("useMaskColor", useMaskColor)
        shader.v1("invertMask", isInverted)
        mask.bind(1, nearestFiltering)
        texture.bind(0, nearestFiltering)
        flat01.draw(shader)
        check()
    }

    fun draw3D(stack: Matrix4fStack, buffer: StaticFloatBuffer, texture: Texture2D, w: Int, h:Int, color: Vector4f,
               isBillboard: Float, nearestFiltering: Boolean, tiling: Vector4f?){
        val shader = shader3D
        shader3DUniforms(shader, stack, w, h, color, isBillboard, tiling)
        texture.bind(0, nearestFiltering)
        buffer.draw(shader)
        check()
    }

    fun draw3D(stack: Matrix4fStack, buffer: StaticFloatBuffer, texture: Texture2D, color: Vector4f,
               isBillboard: Float, nearestFiltering: Boolean, tiling: Vector4f?){
        draw3D(stack, buffer, texture, texture.w, texture.h, color, isBillboard, nearestFiltering, tiling)
    }

    fun draw3DPolygon(stack: Matrix4fStack, buffer: StaticFloatBuffer,
                      texture: Texture2D, color: Vector4f,
                      inset: Float,
                      isBillboard: Float, nearestFiltering: Boolean){
        val shader = shader3DPolygon
        shader3DUniforms(shader, stack, 1, 1, color, isBillboard, null)
        shader.v1("inset", inset)
        texture.bind(0, nearestFiltering)
        buffer.draw(shader)
        check()
    }

    fun draw3D(stack: Matrix4fStack, texture: Texture2D, color: Vector4f,
               isBillboard: Float, nearestFiltering: Boolean, tiling: Vector4f?){
        return draw3D(stack, flat01, texture, color, isBillboard, nearestFiltering, tiling)
    }

    fun draw3D(stack: Matrix4fStack, texture: Frame, color: Vector4f,
               isBillboard: Float, nearestFiltering: Boolean, tiling: Vector4f?){
        val shader = texture.get3DShader()
        shader3DUniforms(shader, stack, texture.w, texture.h, color, isBillboard, tiling)
        texture.bind(0, nearestFiltering)
        flat01.draw(shader)
        check()
    }

    fun drawXYZUV(stack: Matrix4fStack, buffer: StaticFloatBuffer, texture: Texture2D, color: Vector4f,
                  isBillboard: Float, nearestFiltering: Boolean, mode: Int = GL11.GL_TRIANGLES){
        val shader = shader3DXYZUV
        shader3DUniforms(shader, stack, 1,1 , color, isBillboard, null)
        texture.bind(0, nearestFiltering)
        buffer.draw(shader, mode)
        check()
    }

    fun drawSpherical(stack: Matrix4fStack, buffer: StaticFloatBuffer, texture: Texture2D, color: Vector4f,
                  isBillboard: Float, nearestFiltering: Boolean, mode: Int = GL11.GL_TRIANGLES){
        val shader = shader3DSpherical
        shader3DUniforms(shader, stack, 1,1 , color, isBillboard, null)
        texture.bind(0, nearestFiltering)
        buffer.draw(shader, mode)
        check()
    }

    fun draw3DSVG(stack: Matrix4fStack, buffer: StaticFloatBuffer, texture: Texture2D, color: Vector4f,
                  isBillboard: Float, nearestFiltering: Boolean){
        val shader = shader3DSVG
        shader3DUniforms(shader, stack, 1,1 , color, isBillboard, null)
        texture.bind(0, nearestFiltering)
        buffer.draw(shader)
        check()
    }

    fun String.endSpaceCount(): Int {
        var spaceCount = 0
        var index = lastIndex
        loop@while(index > -1){
            when(this[index]){
                ' ' -> spaceCount++
                '\t' -> spaceCount += 4
                else -> break@loop
            }
            index--
        }
        return spaceCount
    }

    // fun getTextSize(fontSize: Int, bold: Boolean, italic: Boolean, text: String) = getTextSize(defaultFont, fontSize, bold, italic, text)
    fun getTextSize(font: String, fontSize: Int, bold: Boolean, italic: Boolean, text: String): Pair<Int, Int> {
        // count how many spaces there are at the end
        // todo get accurate space and tab widths
        val spaceWidth = text.endSpaceCount() * fontSize / 4
        val texture = FontManager.getString(font, fontSize.toFloat(), text, bold = bold, italic = italic) ?: return spaceWidth to fontSize
        return (texture.w + spaceWidth) to texture.h
    }

    fun initShaders(){

        // color only
        flatShader = Shader("" +
                "a2 attr0;\n" +
                "u2 pos, size;\n" +
                "void main(){\n" +
                "   gl_Position = vec4((pos + attr0 * size)*2.-1., 0.0, 1.0);\n" +
                "}", "", "" +
                "u4 color;\n" +
                "void main(){\n" +
                "   gl_FragColor = color;\n" +
                "}")

        flatShaderTexture = Shader("" +
                "a2 attr0;\n" +
                "u2 pos, size;\n" +
                "void main(){\n" +
                "   gl_Position = vec4((pos + attr0 * size)*2.-1., 0.0, 1.0);\n" +
                "   uv = attr0;\n" +
                "}", "" +
                "varying vec2 uv;\n", "" +
                "uniform sampler2D tex;\n" +
                "u4 color;\n" +
                "void main(){\n" +
                "   gl_FragColor = color * texture(tex, uv);\n" +
                "}")

        // with texture
        subpixelCorrectTextShader = Shader("" +
                "a2 attr0;\n" +
                "u2 pos, size;\n" +
                "void main(){\n" +
                "   gl_Position = vec4((pos + attr0 * size)*2.-1., 0.0, 1.0);\n" +
                "   uv = attr0;\n" +
                "}", "" +
                "varying v2 uv;\n", "" +
                "uniform vec4 textColor;" +
                "uniform vec3 backgroundColor;\n" +
                "uniform sampler2D tex;\n" +
                "float brightness(vec3 color){" +
                "   return dot(color, vec3(1.));\n" +
                "}" +
                "void main(){\n" +
                "   vec3 textMask = texture(tex, uv).rgb;\n" +
                "   vec3 mixing = brightness(textColor.rgb) > brightness(backgroundColor) ? textMask.rgb : textMask.bgr;\n" +
                "   vec3 color = vec3(\n" +
                "       mix(backgroundColor.r, textColor.r, mixing.r),\n" +
                "       mix(backgroundColor.g, textColor.g, mixing.g),\n" +
                "       mix(backgroundColor.b, textColor.b, mixing.b));\n" +
                "   gl_FragColor = vec4(color, textColor.a);\n" +
                "}")

        subpixelCorrectTextShader.use()
        GL20.glUniform1i(subpixelCorrectTextShader["tex"], 0)

        val positionPostProcessing = "" +
                ""

        val colorPostProcessing = "" +
                "   gl_FragColor.rgb *= gl_FragColor.rgb;\n"

        val colorProcessing = "" +
                "   if(color.a <= 0.0) discard;\n"

        val v3DBase = "" +
                "u2 billboardSize;\n" +
                "uniform mat4 transform;\n" +
                "uniform float isBillboard;\n" +
                "" +
                "vec4 billboardTransform(vec2 betterUV, float z){" +
                "   vec4 pos0 = transform * vec4(0.0,0.0,0.0,1.0);\n" +
                "   pos0.xy += betterUV * billboardSize;\n" +
                "   pos0.z += z;\n" +
                "   return pos0;\n" +
                "}" +
                "" +
                "vec4 transform3D(vec2 betterUV){\n" +
                "   return transform * vec4(betterUV, 0.0, 1.0);\n" +
                "}\n"

        val uv3D = "" +
                "" +
                "   vec2 betterUV = attr0*2.-1.;\n" +
                "   uv = (attr0-0.5) * tiling.xy + 0.5 + tiling.zw;\n"

        val v3D = v3DBase +
                "a2 attr0;\n" +
                "u4 tiling;\n" +
                "void main(){\n" +
                uv3D +
                "   vec4 billboard = billboardTransform(betterUV, 0.0);\n" +
                "   vec4 in3D = transform3D(betterUV);\n" +
                "   gl_Position = mix(in3D, billboard, isBillboard);\n" +
                positionPostProcessing +
                "}"

        val v3DMasked = v3DBase +
                "a2 attr0;\n" +
                "void main(){\n" +
                "   vec2 betterUV = attr0*2.-1.;\n" +
                "   vec4 billboard = billboardTransform(betterUV, 0.0);\n" +
                "   vec4 in3D = transform3D(betterUV);\n" +
                "   gl_Position = mix(in3D, billboard, isBillboard);\n" +
                positionPostProcessing +
                "   uv = gl_Position.xyw;\n" +
                "}"

        val v3DPolygon = v3DBase +
                "a3 attr0;\n" +
                "in vec2 attr1;\n" +
                "uniform float inset;\n" +
                "void main(){\n" +
                "   vec2 betterUV = attr0.xy*2.-1.;\n" +
                "   betterUV *= mix(1.0, attr1.r, inset);\n" +
                "   vec4 billboard = billboardTransform(betterUV, attr0.z);\n" +
                "   vec4 in3D = transform * vec4(betterUV, attr0.z, 1.0);\n" +
                "   gl_Position = mix(in3D, billboard, isBillboard);\n" +
                positionPostProcessing +
                "   uv = attr1.yx;\n" +
                "}"

        val v3DXYZUV = v3DBase +
                "a3 attr0;\n" +
                "a2 attr1;\n" +
                "void main(){\n" +
                "   vec4 billboard = billboardTransform(attr0.xy, attr0.z);\n" +
                "   vec4 in3D = transform * vec4(attr0, 1.0);\n" +
                "   gl_Position = mix(in3D, billboard, isBillboard);\n" +
                positionPostProcessing +
                "   uv = attr1;\n" +
                "}"


        val v3DSpherical = v3DBase +
                "a3 attr0;\n" +
                "a2 attr1;\n" +
                "void main(){\n" +
                "   vec4 billboard = billboardTransform(attr0.xy, attr0.z);\n" +
                "   vec4 in3D = transform * vec4(attr0, 1.0);\n" +
                "   gl_Position = mix(in3D, billboard, isBillboard);\n" +
                positionPostProcessing +
                "   uvw = attr0;\n" +
                "}"

        val y3DSpherical = "varying vec3 uvw;\n"

        val f3DSpherical = "" +
                "uniform vec4 tint;\n" +
                "precision highp float;\n" +
                "uniform sampler2D tex;\n" +
                "void main(){\n" +
                "   float u = atan(uvw.z, uvw.x)*${0.5/PI}+0.5;\n " +
                "   float v = atan(uvw.y, length(uvw.xz))*${1/PI}+0.5;\n" +
                "   vec4 color = texture(tex, vec2(u,v));\n" +
                colorProcessing +
                "   gl_FragColor = tint * color;\n" +
                colorPostProcessing +
                "}"

        val v3DSVG = v3DBase +
                "a3 attr0;\n" +
                "a4 attr1;\n" +
                "void main(){\n" +
                "   vec2 betterUV = attr0.xy*2.-1.;\n" +
                "   vec4 billboard = billboardTransform(betterUV, attr0.z);\n" +
                "   vec4 in3D = transform * vec4(betterUV, attr0.z, 1.0);\n" +
                "   gl_Position = mix(in3D, billboard, isBillboard);\n" +
                positionPostProcessing +
                "   uv = attr0.xy;\n" +
                "   color = attr1;\n" +
                "}"

        val y3D = "" +
                "varying v2 uv;\n"

        val y3DSVG = y3D +
                "varying v4 color;\n"

        val f3D = "" +
                "uniform vec4 tint;" +
                "uniform sampler2D tex;\n" +
                "void main(){\n" +
                "   vec4 color = texture(tex, uv);\n" +
                colorProcessing +
                "   gl_FragColor = tint * color;\n" +
                colorPostProcessing +
                "}"

        val y3DMasked = "varying v3 uv;\n"

        val f3DMasked = "" +
                "uniform vec4 tint;" +
                "uniform sampler2D tex, mask;\n" +
                "uniform vec4 offsetColor;\n" +
                "uniform float useMaskColor;\n" +
                "uniform float invertMask;\n" +
                "void main(){\n" +
                "   vec2 uv2 = uv.xy/uv.z * 0.5 + 0.5;\n" +
                "   vec4 mask = texture(mask, uv2);\n" +
                "   vec4 maskColor = vec4(mix(vec3(1.0), mask.rgb, useMaskColor), mix(mask.a, 1.0-mask.a, invertMask));\n" +
                "   if(maskColor.a <= 0.0) discard;\n " +
                "   vec4 color = texture(tex, uv2);\n" +
                colorProcessing +
                "   gl_FragColor = offsetColor + tint * color * maskColor;\n" +
                "   gl_FragColor.a = clamp(gl_FragColor.a, 0.0, 1.0);\n" +
                // no postprocessing, because it was already applied
                "}"

        val f3DSVG = "" +
                "uniform vec4 tint;" +
                "uniform sampler2D tex;\n" +
                "void main(){\n" +
                "   gl_FragColor = tint * color * texture(tex, uv);\n" +
                colorPostProcessing +
                "}"

        // todo anti-aliasing... -> taa?
        val f3DCircle = "" +
                "u4 tint;\n" + // rgba
                "u3 circleParams;\n" + // r², start, end
                "void main(){\n" +
                "   gl_FragColor = tint;\n" +
                colorPostProcessing +
                "   vec2 d0 = uv*2.-1.;\n" +
                "   float dst = dot(d0,d0);\n" +
                "   if(dst > 1.0 || dst < circleParams.r) discard;\n" +
                "   else {" +
                "       float angle = atan(d0.y,d0.x);\n" +
                "       if(circleParams.g < circleParams.b){" +
                "           if(angle < circleParams.g || angle > circleParams.b) discard;" +
                "       } else {" +
                "           if(angle > circleParams.b && angle < circleParams.g) discard;" +
                "       }" +
                "   }" +
                "}"

        val f3DYUV = "" +
                "uniform vec4 tint;" +
                "uniform sampler2D texY, texU, texV;\n" +
                "void main(){\n" +
                "   vec3 yuv = vec3(texture(texY, uv).r, texture(texU, uv).r, texture(texV, uv).r);\n" +
                "   yuv -= vec3(${16f/255f}, 0.5, 0.5);\n" +
                "   vec3 rgb = vec3(" +
                "       dot(yuv, vec3( 1.164,  0.000,  1.596))," +
                "       dot(yuv, vec3( 1.164, -0.392, -0.813))," +
                "       dot(yuv, vec3( 1.164,  2.017,  0.000)));\n" +
                "   gl_FragColor = vec4(tint.rgb * rgb, tint.a);\n" +
                colorPostProcessing +
                "}"

        shader3D = createCustomShader(v3D, y3D, f3D, listOf("tex"))
        shader3DPolygon = createCustomShader(v3DPolygon, y3D, f3D, listOf("tex"))
        shader3DCircle = Shader(v3D, y3D, f3DCircle)
        shader3DMasked = createCustomShader(v3DMasked, y3DMasked, f3DMasked, listOf("tex", "mask"))

        shader3DSVG = createCustomShader(v3DSVG, y3DSVG, f3DSVG, listOf("tex"))
        shader3DXYZUV = createCustomShader(v3DXYZUV, y3D, f3D, listOf("tex"))
        shader3DSpherical = createCustomShader(v3DSpherical, y3DSpherical, f3DSpherical, listOf("tex"))

        shader3DYUV = createCustomShader(v3D, y3D, f3DYUV, listOf("texY", "texU", "texV"))

        shader3DARGB = createCustomShader(v3D, y3D, "" +
                "uniform vec4 tint;" +
                "uniform sampler2D tex;\n" +
                "void main(){\n" +
                "   vec4 color = texture(tex, uv).gbar;\n" +
                colorProcessing +
                "   gl_FragColor = tint * color;\n" +
                colorPostProcessing +
                "}", listOf("tex"))
        shader3DBGRA = createCustomShader(v3D, y3D, "" +
                "uniform vec4 tint;" +
                "uniform sampler2D tex;\n" +
                "void main(){\n" +
                "   vec4 color = texture(tex, uv).bgra;\n" +
                colorProcessing +
                "   gl_FragColor = tint * color;\n" +
                colorPostProcessing +
                "}", listOf("tex"))

        lineShader3D = Shader("in vec3 attr0;\n" +
                "uniform mat4 transform;\n" +
                "void main(){" +
                "   gl_Position = transform * vec4(attr0, 1.0);\n" +
                positionPostProcessing +
                "}", "", "" +
                "uniform vec4 color;\n" +
                "void main(){" +
                "   gl_FragColor = color;\n" +
                colorPostProcessing +
                "}")
    }

    fun createCustomShader2(v3D: String, y3D: String, fragmentShader: String, textures: List<String>): Shader {
        val shader = Shader(v3D, y3D, fragmentShader, true)
        shader.use()
        textures.forEachIndexed { index, name ->
            GL20.glUniform1i(shader[name], index)
        }
        return shader
    }

    fun createCustomShader(v3D: String, y3D: String, fragmentShader: String, textures: List<String>): Shader {
        val shader = Shader(v3D, y3D, fragmentShader)
        shader.use()
        textures.forEachIndexed { index, name ->
            GL20.glUniform1i(shader[name], index)
        }
        return shader
    }

    override fun renderStep0() {
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1) // opengl is evil ;), for optimizations, we might set it back
        supportsAnisotropicFiltering = GL.getCapabilities().GL_EXT_texture_filter_anisotropic
        println("[INFO] OpenGL supports Anisotropic Filtering? $supportsAnisotropicFiltering")
        if(supportsAnisotropicFiltering){
            val max = glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT)
            anisotropy = min(max, DefaultConfig["gpu.filtering.anisotropic.max", 16f])
        }
        invisibleTexture.create(ByteArray(4) { 0.toByte() })
        whiteTexture.create(
            byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 255.toByte()))
        whiteTexture.filtering(true)
        stripeTexture.createMonochrome(
            byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 255.toByte(), 255.toByte()))
        colorShowTexture.create(
            intArrayOf(
                255,255,255,127, 255,255,255,255,
                255,255,255,255, 255,255,255,127
            ).map { it.toByte() }.toByteArray())
        colorShowTexture.filtering(true)
        initShaders()
        setIcon()
    }

    override fun renderStep(){

        // async work section

        var workDone = 0
        val workTime0 = System.nanoTime()
        while(workDone < 100){
            val nextTask = workerTasks.poll() ?: break
            workDone += nextTask()
            val workTime1 = System.nanoTime()
            val workTime = abs(workTime1 - workTime0) * 1e-9f
            if(workTime * editorVideoFPS > 1f){// work is too slow
                break
            }
        }

        // rendering and editor section

        updateTime()
        // updating the local times must be done before the events, because
        // the worker thread might have invalidated those
        updateLastLocalTime(root, editorTime)

        while(eventTasks.isNotEmpty()){
            eventTasks.poll()!!.invoke()
        }

        Texture2D.textureBudgetUsed = 0

        Framebuffer.bindNull()
        glViewport(0, 0, width, height)
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glBindTexture(GL_TEXTURE_2D, 0)

        check()

        glDisable(GL_DEPTH_TEST)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glEnable(GL_BLEND)
        BlendMode.DEFAULT.apply()
        glDisable(GL_CULL_FACE)
        glDisable(GL_ALPHA_TEST)

        check()

        gameLoop(width, height)

        check()

    }

    fun updateLastLocalTime(parent: Transform, time: Float){
        val localTime = parent.getLocalTime(time)
        parent.lastLocalTime = localTime
        parent.children.forEach { child ->
            updateLastLocalTime(child, localTime)
        }
    }

    fun updateTime(){

        val thisTime = System.nanoTime()
        rawDeltaTime = (thisTime - lastTime) * 1e-9f
        deltaTime = min(rawDeltaTime, 0.1f)
        currentEditorFPS += (1f / rawDeltaTime - currentEditorFPS) * 0.1f
        lastTime = thisTime

        editorTime = max(editorTime + deltaTime * editorTimeDilation, 0f)
        if(editorTime == 0f && editorTimeDilation < 0f){
            editorTimeDilation = 0f
        }

        smoothSin = sin(editorTime)
        smoothCos = cos(editorTime)

    }

    fun openMenu(x: Int, y: Int, title: String, options: List<Pair<String, (button: Int, isLong: Boolean) -> Boolean>>){
        val style = DefaultConfig.style.getChild("menu")
        val list = PanelListY(style)
        list += WrapAlign.LeftTop
        val container =
            ScrollPanelY(list, Padding(1), style, AxisAlignment.MIN)
        container += WrapAlign.LeftTop
        lateinit var window: Window
        fun close(){
            windowStack.remove(window)
        }
        val padding = 4
        if(title.isNotEmpty()){
            val titlePanel = TextPanel(title, style)
            titlePanel.padding.left = padding
            titlePanel.padding.right = padding
            list += titlePanel
            list += SpacePanel(0, 1, style)
        }
        for((index, element) in options.withIndex()){
            val (name, action) = element
            if(name == menuSeparator){
                if(index != 0){
                    list += SpacePanel(0, 1, style)
                }
            } else {
                val buttonView = TextPanel(name, style)
                buttonView.setOnClickListener { _, _, button, long ->
                    if(action(button, long)){
                        close()
                    }
                }
                buttonView.padding.left = padding
                buttonView.padding.right = padding
                list += buttonView
            }
        }
        val maxWidth = max(300, GFX.width)
        val maxHeight = max(300, GFX.height)
        container.calculateSize(maxWidth, maxHeight)
        container.applyConstraints()
        val wx = clamp(x, 0, GFX.width - container.w)
        val wy = clamp(y, 0, GFX.height- container.h)
        window = Window(container, wx, wy)
        windowStack.add(window)
    }

    fun openMenu(x: Float, y: Float, title: String, options: List<Pair<String, (button: Int, isLong: Boolean) -> Boolean>>, delta: Int = 10){
        openMenu(x.roundToInt() - delta, y.roundToInt() - delta, title, options)
    }

    fun check(){
        val error = glGetError()
        if(error != 0) throw RuntimeException("GLException: ${when(error){
            1281 -> "invalid value"
            1282 -> "invalid operation"
            else -> "$error"
        }}")
    }

    fun showFPS(){
        clip(0, 0, width, height)
        drawText(1, 1, "SansSerif", 12, false, false, currentEditorFPS.f1(), -1, 0)
    }

}