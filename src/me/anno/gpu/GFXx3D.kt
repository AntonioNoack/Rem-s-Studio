package me.anno.gpu

import me.anno.gpu.GFX.currentCamera
import me.anno.gpu.GFX.windowHeight
import me.anno.gpu.GFX.windowWidth
import me.anno.gpu.ShaderLib.maxOutlineColors
import me.anno.gpu.blending.BlendDepth
import me.anno.gpu.buffer.SimpleBuffer
import me.anno.gpu.buffer.StaticBuffer
import me.anno.gpu.shader.Shader
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.Filtering
import me.anno.gpu.texture.GPUFiltering
import me.anno.gpu.texture.Texture2D
import me.anno.input.Input
import me.anno.objects.GFXTransform
import me.anno.objects.Video
import me.anno.objects.effects.MaskType
import me.anno.objects.geometric.Circle
import me.anno.objects.geometric.Polygon
import me.anno.objects.modes.UVProjection
import me.anno.studio.rems.RemsStudio
import me.anno.video.VFrame
import ofx.mio.OpticalFlow
import org.joml.*
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11.glDisable
import kotlin.math.min

object GFXx3D {

    fun shader3DUniforms(
        shader: Shader, stack: Matrix4fArrayList,
        w: Int, h: Int, color: Vector4fc?,
        tiling: Vector4fc?, filtering: Filtering,
        uvProjection: UVProjection?
    ) {

        stack.next {

            val doScale2 = (uvProjection?.doScale ?: true) && w != h
            // val avgSize = sqrt(w * h.toFloat())
            if (doScale2) {
                val avgSize =
                    if (w * RemsStudio.targetHeight > h * RemsStudio.targetWidth) w.toFloat() * RemsStudio.targetHeight / RemsStudio.targetWidth else h.toFloat()
                val sx = w / avgSize
                val sy = h / avgSize
                stack.scale(sx, -sy, 1f)
            } else {
                stack.scale(1f, -1f, 1f)
            }

            transformUniform(shader, stack)
            shader.v1("filtering", filtering.id)
            shader.v2("textureDeltaUV", 1f / w, 1f / h)

        }

        GFX.shaderColor(shader, "tint", color)
        if (tiling != null) shader.v4("tiling", tiling)
        else shader.v4("tiling", 1f, 1f, 0f, 0f)
        shader.v1("drawMode", GFX.drawMode.id)
        shader.v1("uvProjection", uvProjection?.id ?: UVProjection.Planar.id)

    }

    fun shader3DUniforms(
        shader: Shader, stack: Matrix4fArrayList,
        w: Int, h: Int, color: Int,
        tiling: Vector4fc?, filtering: Filtering,
        uvProjection: UVProjection?
    ) {

        stack.next {

            val doScale2 = (uvProjection?.doScale ?: true) && w != h
            // val avgSize = sqrt(w * h.toFloat())
            if (doScale2) {

                val avgSize = if (w * RemsStudio.targetHeight > h * RemsStudio.targetWidth)
                    w.toFloat() * RemsStudio.targetHeight / RemsStudio.targetWidth
                else
                    h.toFloat()

                val sx = w / avgSize
                val sy = h / avgSize

                stack.scale(sx, -sy, 1f)

            } else stack.scale(1f, -1f, 1f)

            transformUniform(shader, stack)
            shader.v1("filtering", filtering.id)
            shader.v2("textureDeltaUV", 1f / w, 1f / h)

        }

        GFX.shaderColor(shader, "tint", color)
        if (tiling != null) shader.v4("tiling", tiling)
        else shader.v4("tiling", 1f, 1f, 0f, 0f)
        shader.v1("drawMode", GFX.drawMode.id)
        shader.v1("uvProjection", uvProjection?.id ?: UVProjection.Planar.id)

    }

    fun drawDebugCube(matrix: Matrix4fArrayList, size: Float, color: Vector4fc?) {
        matrix.scale(0.5f * size, -0.5f * size, 0.5f * size) // flip inside out
        val tex = TextureLib.whiteTexture
        draw3D(
            matrix, tex, color,
            Filtering.NEAREST, tex.clamping, null, UVProjection.TiledCubemap
        )
    }

    fun shader3DUniforms(shader: Shader, stack: Matrix4f?, color: Int) {
        transformUniform(shader, stack)
        GFX.shaderColor(shader, "tint", color)
        shader.v4("tiling", 1f, 1f, 0f, 0f)
        shader.v1("drawMode", GFX.drawMode.id)
    }

    fun shader3DUniforms(shader: Shader, stack: Matrix4f, color: Vector4fc) {
        transformUniform(shader, stack)
        GFX.shaderColor(shader, "tint", color)
        shader.v4("tiling", 1f, 1f, 0f, 0f)
        shader.v1("drawMode", GFX.drawMode.id)
    }

    fun transformUniform(shader: Shader, stack: Matrix4fc?) {
        GFX.check()
        shader.m4x4("transform", stack)
    }

    fun draw3DMasked(
        stack: Matrix4fArrayList, color: Vector4fc,
        maskType: MaskType,
        useMaskColor: Float,
        pixelSize: Float,
        offset: Vector2fc,
        isInverted: Float,
        isFullscreen: Boolean,
        greenScreenSettings: Vector3f
    ) {
        val shader = ShaderLib.shader3DMasked
        shader.use()
        shader3DUniforms(shader, stack, color)
        shader.v1("useMaskColor", useMaskColor)
        shader.v1("invertMask", isInverted)
        shader.v1("maskType", maskType.id)
        shader.v2("pixelating", pixelSize * windowHeight / windowWidth, pixelSize)
        shader.v3("greenScreenSettings", greenScreenSettings)
        shader.v2("offset", offset)
        shader.v2("windowSize", windowWidth.toFloat(), windowHeight.toFloat())
        val buffer = if (isFullscreen) SimpleBuffer.flatLarge else SimpleBuffer.flat11
        buffer.draw(shader)
        GFX.check()
    }

    fun draw3DText(
        that: GFXTransform?, time: Double, offset: Vector3fc,
        stack: Matrix4fArrayList, buffer: StaticBuffer, color: Vector4fc
    ) {
        val shader = ShaderLib.shader3DforText
        shader.use()
        shader3DUniforms(shader, stack, color)
        shader.v3("offset", offset)
        that?.uploadAttractors(shader, time) ?: GFXTransform.uploadAttractors0(shader)
        buffer.draw(shader)
        GFX.check()
    }

    fun draw3DTextWithOffset(
        buffer: StaticBuffer,
        offset: Vector3fc
    ) {
        val shader = ShaderLib.shader3DforText
        shader.use()
        shader.v3("offset", offset)
        buffer.draw(shader)
    }

    private val tmp0 = Vector3f()
    private val tmp1 = Vector3f()
    private val tmp2 = Vector4f()
    fun colorGradingUniforms(video: Video?, time: Double, shader: Shader) {
        if (video == null) {
            shader.v3("cgOffset", 0f)
            shader.v3("cgSlope", 1f)
            shader.v3("cgPower", 1f)
            shader.v1("cgSaturation", 1f)
        } else {
            tmp0.set(video.cgOffsetAdd[time, tmp0])
            tmp1.set(video.cgOffsetSub[time, tmp1])
            shader.v3("cgOffset", tmp0.sub(tmp1))
            shader.v3X("cgSlope", video.cgSlope[time, tmp2])
            shader.v3X("cgPower", video.cgPower[time, tmp2])
            shader.v1("cgSaturation", video.cgSaturation[time])
        }
    }

    fun draw3DPolygon(
        polygon: Polygon, time: Double,
        stack: Matrix4fArrayList, buffer: StaticBuffer,
        texture: Texture2D, color: Vector4fc,
        inset: Float,
        filtering: Filtering, clamping: Clamping
    ) {
        val shader = ShaderLib.shader3DPolygon
        shader.use()
        polygon.uploadAttractors(shader, time)
        shader3DUniforms(shader, stack, texture.w, texture.h, color, null, filtering, null)
        shader.v1("inset", inset)
        texture.bind(0, filtering, clamping)
        buffer.draw(shader)
        GFX.check()
    }

    fun draw3D(
        stack: Matrix4fArrayList, texture: VFrame, color: Vector4fc,
        filtering: Filtering, clamping: Clamping, tiling: Vector4fc?, uvProjection: UVProjection
    ) {
        if (!texture.isCreated) throw RuntimeException("Frame must be loaded to be rendered!")
        val shader = texture.get3DShader()
        shader.use()
        shader3DUniforms(shader, stack, texture.w, texture.h, color, tiling, filtering, uvProjection)
        texture.bind(0, filtering, clamping)
        if (shader == ShaderLib.shader3DYUV) {
            val w = texture.w
            val h = texture.h
            shader.v2("uvCorrection", w.toFloat() / ((w + 1) / 2 * 2), h.toFloat() / ((h + 1) / 2 * 2))
        }
        uvProjection.getBuffer().draw(shader)
        GFX.check()
    }

    fun draw3D(
        stack: Matrix4fArrayList, texture: VFrame, color: Int,
        filtering: Filtering, clamping: Clamping, tiling: Vector4fc?, uvProjection: UVProjection
    ) {
        if (!texture.isCreated) throw RuntimeException("Frame must be loaded to be rendered!")
        val shader = texture.get3DShader()
        shader.use()
        shader3DUniforms(shader, stack, texture.w, texture.h, color, tiling, filtering, uvProjection)
        texture.bind(0, filtering, clamping)
        if (shader == ShaderLib.shader3DYUV) {
            val w = texture.w
            val h = texture.h
            shader.v2("uvCorrection", w.toFloat() / ((w + 1) / 2 * 2), h.toFloat() / ((h + 1) / 2 * 2))
        }
        uvProjection.getBuffer().draw(shader)
        GFX.check()
    }

    fun draw3DVideo(
        video: GFXTransform, time: Double,
        stack: Matrix4fArrayList, texture: VFrame, color: Vector4fc,
        filtering: Filtering, clamping: Clamping, tiling: Vector4fc?, uvProjection: UVProjection
    ) {
        if (!texture.isCreated) throw RuntimeException("Frame must be loaded to be rendered!")
        val shader = texture.get3DShader()
        shader.use()
        video.uploadAttractors(shader, time)
        shader3DUniforms(shader, stack, texture.w, texture.h, color, tiling, filtering, uvProjection)
        colorGradingUniforms(video as? Video, time, shader)
        texture.bind(0, filtering, clamping)
        if (shader == ShaderLib.shader3DYUV) {
            val w = texture.w
            val h = texture.h
            shader.v2("uvCorrection", w.toFloat() / ((w + 1) / 2 * 2), h.toFloat() / ((h + 1) / 2 * 2))
        }
        uvProjection.getBuffer().draw(shader)
        GFX.check()
    }

    fun draw3DVideo(
        video: GFXTransform, time: Double,
        stack: Matrix4fArrayList, v0: VFrame, v1: VFrame, interpolation: Float, color: Vector4fc,
        filtering: Filtering, clamping: Clamping, tiling: Vector4fc?, uvProjection: UVProjection
    ) {

        if (!v0.isCreated || !v1.isCreated) throw RuntimeException("Frame must be loaded to be rendered!")

        val t0 = v0.getTextures()
        val t1 = v1.getTextures()

        val lambda = 0.01f
        val blurAmount = 0.05f
        BlendDepth(null,false){
            // interpolate all textures
            val interpolated = t0.zip(t1).map { (x0, x1) -> OpticalFlow.run(lambda, blurAmount, interpolation, x0, x1) }
            // bind them
            v0.bind2(0, filtering, clamping, interpolated)
        }

        val shader = v0.get3DShader()
        shader.use()
        video.uploadAttractors(shader, time)
        shader3DUniforms(shader, stack, v0.w, v0.h, color, tiling, filtering, uvProjection)
        colorGradingUniforms(video as? Video, time, shader)

        if (shader == ShaderLib.shader3DYUV) {
            val w = v0.w
            val h = v0.h
            shader.v2("uvCorrection", w.toFloat() / ((w + 1) / 2 * 2), h.toFloat() / ((h + 1) / 2 * 2))
        }

        uvProjection.getBuffer().draw(shader)
        GFX.check()

    }

    fun draw3D(
        stack: Matrix4fArrayList, texture: Texture2D, color: Vector4fc?,
        filtering: Filtering, clamping: Clamping, tiling: Vector4fc?, uvProjection: UVProjection
    ) = draw3D(stack, texture, texture.w, texture.h, color, filtering, clamping, tiling, uvProjection)

    fun draw3D(
        stack: Matrix4fArrayList, texture: Texture2D, color: Int,
        filtering: Filtering, clamping: Clamping, tiling: Vector4fc?, uvProjection: UVProjection
    ) = draw3D(stack, texture, texture.w, texture.h, color, filtering, clamping, tiling, uvProjection)

    fun draw3D(
        stack: Matrix4fArrayList, texture: Texture2D, w: Int, h: Int, color: Vector4fc?,
        filtering: Filtering, clamping: Clamping, tiling: Vector4fc?, uvProjection: UVProjection
    ) {
        val shader = ShaderLib.shader3D
        shader.use()
        shader3DUniforms(shader, stack, w, h, color, tiling, filtering, uvProjection)
        texture.bind(0, filtering, clamping)
        uvProjection.getBuffer().draw(shader)
        GFX.check()
    }

    fun draw3D(
        stack: Matrix4fArrayList, texture: Texture2D, w: Int, h: Int, color: Int,
        filtering: Filtering, clamping: Clamping, tiling: Vector4fc?, uvProjection: UVProjection
    ) {
        val shader = ShaderLib.shader3D
        shader.use()
        shader3DUniforms(shader, stack, w, h, color, tiling, filtering, uvProjection)
        texture.bind(0, filtering, clamping)
        uvProjection.getBuffer().draw(shader)
        GFX.check()
    }

    val outlineStatsBuffer = BufferUtils.createFloatBuffer(maxOutlineColors * 4)
    fun drawOutlinedText(
        that: GFXTransform?,
        time: Double,
        stack: Matrix4fArrayList,
        offset: Vector2fc,
        scale: Vector2fc,
        texture: Texture2D,
        color: Vector4fc,
        colorCount: Int,
        colors: Array<Vector4fc>,
        distances: FloatArray,
        smoothness: FloatArray,
        depth: Float
    ) {

        val shader = ShaderLib.shader3DOutlinedText
        shader.use()
        transformUniform(shader, stack)
        that?.uploadAttractors(shader, time) ?: GFXTransform.uploadAttractors0(shader)

        GFX.shaderColor(shader, "tint", color)

        shader.v1("drawMode", GFX.drawMode.id)

        texture.bind(0, GPUFiltering.LINEAR, Clamping.CLAMP)

        val cc = min(colorCount, maxOutlineColors)
        /**
         * u4[ maxColors ] colors
         * u2[ maxColors ] distSmooth
         * uniform int colorCount
         * */
        outlineStatsBuffer.position(0)
        for (i in 0 until cc) {
            val colorI = colors[i]
            outlineStatsBuffer.put(colorI.x())
            outlineStatsBuffer.put(colorI.y())
            outlineStatsBuffer.put(colorI.z())
            outlineStatsBuffer.put(colorI.w())
        }
        outlineStatsBuffer.position(0)
        shader.v4Array("colors", outlineStatsBuffer)
        outlineStatsBuffer.position(0)
        for (i in 0 until cc) {
            outlineStatsBuffer.put(distances[i])
            outlineStatsBuffer.put(smoothness[i])
        }
        outlineStatsBuffer.position(0)
        shader.v2Array("distSmoothness", outlineStatsBuffer)
        shader.v1("colorCount", cc)
        shader.v2("offset", offset)
        shader.v2("scale", scale)
        shader.v1("depth", depth * 0.00001f)

        GFX.check()

        UVProjection.Planar.getBuffer().draw(shader)

        GFX.check()
    }

    fun drawOutlinedText(
        stack: Matrix4fArrayList,
        offset: Vector2fc,
        scale: Vector2fc,
        texture: Texture2D
    ) {
        val shader = ShaderLib.shader3DOutlinedText
        shader.use()
        transformUniform(shader, stack)
        shader.v2("offset", offset)
        shader.v2("scale", scale)
        texture.bind(0, GPUFiltering.LINEAR, Clamping.CLAMP)
        UVProjection.Planar.getBuffer().draw(shader)
        GFX.check()
    }

    fun draw3DVideo(
        video: GFXTransform, time: Double,
        stack: Matrix4fArrayList, texture: Texture2D, color: Vector4fc,
        filtering: Filtering, clamping: Clamping, tiling: Vector4fc?, uvProjection: UVProjection
    ) {
        val shader = ShaderLib.shader3DRGBA
        shader.use()
        shader3DUniforms(shader, stack, texture.w, texture.h, color, tiling, filtering, uvProjection)
        video.uploadAttractors(shader, time)
        colorGradingUniforms(video as? Video, time, shader)
        texture.bind(0, filtering, clamping)
        uvProjection.getBuffer().draw(shader)
        GFX.check()
    }

    fun draw3DGaussianBlur(
        stack: Matrix4fArrayList,
        size: Float, w: Int, h: Int,
        threshold: Float, isFirst: Boolean,
        isFullscreen: Boolean
    ) {
        val shader = ShaderLib.shader3DGaussianBlur
        shader.use()
        transformUniform(shader, stack)
        if (isFirst) shader.v2("stepSize", 0f, 1f / h)
        else shader.v2("stepSize", 1f / w, 0f)
        shader.v1("steps", size * h)
        shader.v1("threshold", threshold)
        val buffer = if (isFullscreen) SimpleBuffer.flatLarge else SimpleBuffer.flat11
        buffer.draw(shader)
        GFX.check()
    }

    fun draw3DBoxBlur(
        stack: Matrix4fArrayList,
        steps: Int, w: Int, h: Int,
        isFirst: Boolean
    ) {
        val shader = ShaderLib.shader3DBoxBlur
        shader.use()
        transformUniform(shader, stack)
        if (isFirst) {
            shader.v2("stepSize", 0f, 1f / h)
            shader.v1("steps", steps)
        } else {
            shader.v2("stepSize", 1f / w, 0f)
            shader.v1("steps", steps)
        }
        GFX.flat01.draw(shader)
        GFX.check()
    }

    fun draw3DCircle(
        that: GFXTransform?, time: Double,
        stack: Matrix4fArrayList,
        innerRadius: Float,
        startDegrees: Float,
        endDegrees: Float,
        color: Vector4fc
    ) {
        val shader = ShaderLib.shader3DCircle
        shader.use()
        shader3DUniforms(shader, stack, 1, 1, color, null, Filtering.NEAREST, null)
        that?.uploadAttractors(shader, time) ?: GFXTransform.uploadAttractors0(shader)
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
        val angle0 = GFX.toRadians(a0)
        val angle1 = GFX.toRadians(a1)
        shader.v3("circleParams", 1f - innerRadius, angle0, angle1)
        Circle.drawBuffer(shader)
        GFX.check()
    }

}