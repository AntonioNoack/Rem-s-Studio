package me.anno.studio

import me.anno.config.DefaultStyle
import me.anno.gpu.GFX
import me.anno.gpu.Shader
import me.anno.gpu.framebuffer.Framebuffer
import me.anno.objects.blending.BlendMode
import me.anno.ui.editor.sceneView.Grid
import org.joml.Matrix4fStack
import org.lwjgl.opengl.GL30

object Scene {

    lateinit var sqrtDisplayShader: Shader

    var isInited = false
    fun init(){
        sqrtDisplayShader = Shader("" +
                "in vec2 attr0;\n" +
                "uniform float ySign;\n" +
                "void main(){" +
                "   vec2 coords = attr0*2.0-1.0;\n" +
                "   gl_Position = vec4(coords.x, coords.y * ySign, 0.0, 1.0);\n" +
                "   uv = attr0;\n" +
                "}", "" +
                "varying vec2 uv;\n", "" +
                "uniform sampler2D tex;\n" +
                "void main(){" +
                "   gl_FragColor = sqrt(texture(tex, uv));\n" +
                "}")
        isInited = true
    }

    fun draw(target: Framebuffer?, framebuffer: Framebuffer, x0: Int, y0: Int, w: Int, h: Int, time: Float, flipY: Boolean){

        GFX.check()

        if(!isInited) init()

        val camera = GFX.selectedCamera
        val (cameraTransform, cameraTime) = camera.getGlobalTransform(time)

        GFX.clip(x0, y0, w, h)

        framebuffer.bind(w, h)

        GFX.check()
        GFX.drawRect(x0, y0, w, h, DefaultStyle.black)

        if(camera.useDepth){
            GL30.glEnable(GL30.GL_DEPTH_TEST)
            GL30.glClearDepth(1.0)
            GL30.glDepthRange(-1.0, 1.0)
            GL30.glDepthFunc(GL30.GL_LEQUAL)
            GL30.glClear(GL30.GL_DEPTH_BUFFER_BIT)
        } else {
            GL30.glDisable(GL30.GL_DEPTH_TEST)
        }

        // draw the 3D stuff
        // todo gizmos for orientation

        val stack = Matrix4fStack(256)

        GFX.applyCameraTransform(camera, cameraTime, cameraTransform, stack)

        val white = camera.color[cameraTime]

        stack.pushMatrix()
        Grid.draw(stack, cameraTransform)
        stack.popMatrix()

        if(camera.useDepth){
            GL30.glEnable(GL30.GL_DEPTH_TEST)
        } else {
            GL30.glDisable(GL30.GL_DEPTH_TEST)
        }

        BlendMode.DEFAULT.apply()
        GL30.glDepthMask(true)

        stack.pushMatrix()
        // root.draw(stack, editorHoverTime, Vector4f(1f,1f,1f,1f))
        GFX.nullCamera.draw(stack, time, white)
        stack.popMatrix()
        stack.pushMatrix()
        GFX.root.draw(stack, time, white)
        stack.popMatrix()

        BlendMode.DEFAULT.apply()

        GL30.glDisable(GL30.GL_DEPTH_TEST)

        if(target == null){
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)
            GFX.clip(x0, y0, w, h)
        } else {
            target.bind()
        }

        framebuffer.bindTextures(true)

        sqrtDisplayShader.use()
        sqrtDisplayShader.v1("ySign", if(flipY) -1f else 1f)
        GFX.flat01.draw(sqrtDisplayShader)
        GFX.check()

    }

}