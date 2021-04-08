package me.anno.gpu

import me.anno.fonts.FontManager
import me.anno.fonts.FontManager.getAvgFontSize
import me.anno.fonts.keys.TextCacheKey
import me.anno.gpu.GFXx3D.draw3D
import me.anno.gpu.GFXx3D.draw3DCircle
import me.anno.gpu.TextureLib.whiteTexture
import me.anno.gpu.shader.Shader
import me.anno.gpu.shader.ShaderPlus
import me.anno.gpu.texture.*
import me.anno.objects.GFXTransform.Companion.uploadAttractors0
import me.anno.objects.modes.UVProjection
import me.anno.ui.base.Font
import me.anno.ui.debug.FrameTimes
import me.anno.utils.Maths.clamp
import me.anno.video.VFrame
import org.joml.Matrix4fArrayList
import org.joml.Vector4f
import org.joml.Vector4fc
import kotlin.math.roundToInt

object GFXx2D {

    fun drawRectGradient(x: Int, y: Int, w: Int, h: Int, lColor: Vector4fc, rColor: Vector4fc) {
        if (w == 0 || h == 0) return
        GFX.check()
        val shader = ShaderLib.flatShaderGradient
        shader.use()
        whiteTexture.bind(0, whiteTexture.filtering, whiteTexture.clamping)
        posSize(shader, x, y, w, h)
        shader.v4("lColor", lColor)
        shader.v4("rColor", rColor)
        shader.v1("code", -1)
        GFX.flat01.draw(shader)
        GFX.check()
    }

    fun drawRectGradient(x: Int, y: Int, w: Int, h: Int, lColor: Int, rColor: Int) {
        if (w == 0 || h == 0) return
        GFX.check()
        val shader = ShaderLib.flatShaderGradient
        shader.use()
        whiteTexture.bind(0, whiteTexture.filtering, whiteTexture.clamping)
        posSize(shader, x, y, w, h)
        shader.v4("lColor", lColor)
        shader.v4("rColor", rColor)
        shader.v1("code", -1)
        GFX.flat01.draw(shader)
        GFX.check()
    }

    fun drawRectGradient(
        x: Int, y: Int, w: Int, h: Int, lColor: Vector4fc, rColor: Vector4fc,
        frame: VFrame, uvs: Vector4fc
    ) {
        if (w == 0 || h == 0) return
        GFX.check()
        val shader = ShaderLib.flatShaderGradient
        shader.use()
        frame.bind(0, GPUFiltering.TRULY_LINEAR, Clamping.CLAMP)
        posSize(shader, x, y, w, h)
        shader.v4("lColor", lColor)
        shader.v4("rColor", rColor)
        shader.v4("uvs", uvs)
        shader.v1("code", frame.code)
        GFX.flat01.draw(shader)
        GFX.check()
    }

    fun drawRectGradient(
        x: Int, y: Int, w: Int, h: Int, lColor: Int, rColor: Int,
        frame: VFrame, uvs: Vector4fc
    ) {
        if (w == 0 || h == 0) return
        GFX.check()
        val shader = ShaderLib.flatShaderGradient
        shader.use()
        frame.bind(0, GPUFiltering.TRULY_LINEAR, Clamping.CLAMP)
        posSize(shader, x, y, w, h)
        shader.v4("lColor", lColor)
        shader.v4("rColor", rColor)
        shader.v4("uvs", uvs)
        shader.v1("code", frame.code)
        GFX.flat01.draw(shader)
        GFX.check()
    }

    fun drawRectStriped(x: Int, y: Int, w: Int, h: Int, offset: Int, stride: Int, color: Vector4fc) {
        if (w == 0 || h == 0) return
        GFX.check()
        val shader = ShaderLib.flatShaderStriped
        shader.use()
        posSize(shader, x, y, w, h)
        shader.v4("color", color)
        var o = offset % stride
        if (o < 0) o += stride
        shader.v1("offset", o)
        shader.v1("stride", stride)
        GFX.flat01.draw(shader)
        GFX.check()
    }

    fun drawRect(x: Int, y: Int, w: Int, h: Int, color: Vector4fc) {
        if (w == 0 || h == 0) return
        GFX.check()
        val shader = ShaderLib.flatShader
        shader.use()
        posSize(shader, x, y, w, h)
        shader.v4("color", color)
        GFX.flat01.draw(shader)
        GFX.check()
    }

    fun drawRect(x: Int, y: Int, w: Int, h: Int, color: Int) {
        if (w == 0 || h == 0) return
        GFX.check()
        val shader = ShaderLib.flatShader
        shader.use()
        posSize(shader, x, y, w, h)
        shader.v4("color", color)
        GFX.flat01.draw(shader)
        GFX.check()
    }

    fun drawRect(x: Int, y: Int, w: Int, h: Int) {
        if (w == 0 || h == 0) return
        val shader = ShaderLib.flatShader
        shader.use()
        posSize(shader, x, y, w, h)
        GFX.flat01.draw(shader)
    }

    fun drawRect(x: Float, y: Float, w: Float, h: Float, color: Vector4fc) {
        GFX.check()
        val shader = ShaderLib.flatShader
        shader.use()
        posSize(shader, x, y, w, h)
        shader.v4("color", color)
        GFX.flat01.draw(shader)
        GFX.check()
    }

    fun drawRect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        GFX.check()
        val shader = ShaderLib.flatShader
        shader.use()
        posSize(shader, x, y, w, h)
        shader.v4("color", color)
        GFX.flat01.draw(shader)
        GFX.check()
    }

    fun drawBorder(x: Int, y: Int, w: Int, h: Int, color: Int, size: Int) {
        flatColor(color)
        drawRect(x, y, w, size)
        drawRect(x, y + h - size, w, size)
        drawRect(x, y + size, size, h - 2 * size)
        drawRect(x + w - size, y + size, size, h - 2 * size)
    }

    fun flatColor(color: Int) {
        val shader = ShaderLib.flatShader
        shader.use()
        shader.v4("color", color)
    }

    // the background color is important for correct subpixel rendering, because we can't blend per channel
    /*fun drawText(
        x: Int, y: Int, font: Font, text: String,
        color: Int, backgroundColor: Int, widthLimit: Int, centerX: Boolean = false
    ) =
        writeText(x, y, font, text, color, backgroundColor, widthLimit, centerX)*/

    fun getSizeX(value: Int) = value.and(0xffff)
    fun getSizeY(value: Int) = value.shr(16).and(0xffff)
    fun getSize(x: Int, y: Int) = clamp(x, 0, 0xffff) or clamp(y, 0, 0xffff).shl(16)

    fun drawText(
        x: Int, y: Int, font: Font, text: String,
        color: Int, backgroundColor: Int, widthLimit: Int, centerX: Boolean = false
    ) = writeText(x, y, font, text, color, backgroundColor, widthLimit, centerX)


    fun drawText(
        x: Int, y: Int, font: Font, key: TextCacheKey,
        color: Int, backgroundColor: Int, centerX: Boolean = false
    ) = writeText(x, y, font, key, color, backgroundColor, centerX)

    val simpleChars = Array('z'.toInt() + 1) { it.toChar().toString() }
    var monospaceFont = Font("Consolas", 12f, false, false)
    val monospaceKeys = Array(simpleChars.size){ FontManager.getTextCacheKey(monospaceFont, simpleChars[it], -1) }

    fun drawSimpleTextCharByChar(
        x0: Int, y0: Int,
        padding: Int,
        text: CharArray,
        textColor: Int = FrameTimes.textColor,
        backgroundColor: Int = FrameTimes.backgroundColor
    ) {
        val font = monospaceFont
        val keys = monospaceKeys
        val charWidth = font.sampleWidth
        drawRect(
            x0, y0,
            charWidth * text.size + 2 * padding, font.sizeInt + 2 * padding,
            FrameTimes.backgroundColor
        )
        for (i in text.indices) {
            val char = text[i]
            val charInt = char.toInt()
            if (charInt < simpleChars.size) {
                val key = keys[charInt] ?: continue
                drawText(
                    x0 + padding + i * charWidth, y0 + padding,
                    font, key, textColor, backgroundColor.and(0xffffff)
                )
            }
        }
    }

    fun drawSimpleTextCharByChar(
        x0: Int, y0: Int,
        padding: Int,
        text: String,
        textColor: Int = FrameTimes.textColor,
        backgroundColor: Int = FrameTimes.backgroundColor
    ) {
        val font = monospaceFont
        val keys = monospaceKeys
        val charWidth = font.sampleWidth
        drawRect(
            x0, y0,
            charWidth * text.length + 2 * padding, font.sizeInt + 2 * padding,
            FrameTimes.backgroundColor
        )
        for (i in text.indices) {
            val char = text[i]
            val charInt = char.toInt()
            if (charInt < simpleChars.size) {
                val key = keys[charInt] ?: continue
                drawText(
                    x0 + padding + i * charWidth, y0 + padding,
                    font, key, textColor, backgroundColor.and(0xffffff)
                )
            }
        }
    }

    fun drawTextCharByChar(
        x: Int, y: Int,
        font: Font,
        text: String,
        color: Int,
        backgroundColor: Int,
        widthLimit: Int,
        centerX: Boolean,
        equalSpaced: Boolean
    ): Int {

        // todo correct distances for everything

        if ('\n' in text) {
            throw NotImplementedError("Linebreak for drawTextCharByChar")
        }

        val charWidth = if (equalSpaced) getTextSizeX(font, "x", widthLimit) else 0

        if (centerX) {
            return if (equalSpaced) {
                drawTextCharByChar(
                    x - (charWidth * text.length) / 2, y,
                    font, text, color, backgroundColor,
                    widthLimit, false, equalSpaced
                )
            } else {
                drawTextCharByChar(
                    x - getTextSizeX(font, text, widthLimit) / 2, y,
                    font, text, color, backgroundColor,
                    widthLimit, false, equalSpaced
                )
            }
        }

        // todo width limit...

        val shader = ShaderLib.subpixelCorrectTextShader
        shader.use()
        shader.v4("textColor", color)
        shader.v4("backgroundColor", backgroundColor)

        GFX.loadTexturesSync.push(true)

        var fx = x
        var h = font.sizeInt
        for (char in text) {
            val txt = "$char"
            val size = FontManager.getSize(font, txt, -1)
            val sizeFirst = getSizeX(size)
            val sizeSecond = getSizeY(size)
            h = sizeSecond
            val w = if (equalSpaced) charWidth else sizeFirst
            if (txt.isNotBlank()) {
                val texture = FontManager.getString(font, txt, -1)
                if (texture != null && (texture !is Texture2D || texture.isCreated)) {
                    texture.bind(GPUFiltering.TRULY_NEAREST, Clamping.CLAMP)
                    val x2 = fx + (w - sizeFirst) / 2
                    shader.v2(
                        "pos",
                        (x2 - GFX.windowX).toFloat() / GFX.windowWidth,
                        1f - (y - GFX.windowY).toFloat() / GFX.windowHeight
                    )
                    shader.v2("size", sizeFirst.toFloat() / GFX.windowWidth, -h.toFloat() / GFX.windowHeight)
                    GFX.flat01.draw(shader)
                    GFX.check()
                }
            }
            fx += w
        }

        GFX.loadTexturesSync.pop()

        return getSize(fx - x, h)

    }

    fun writeText(
        x: Int, y: Int,
        font: Font, text: String,
        color: Int, backgroundColor: Int,
        widthLimit: Int,
        centerX: Boolean = false
    ): Int {

        GFX.check()
        val tex0 = FontManager.getString(font, text, widthLimit)
        val charByChar = (tex0 == null || tex0 !is Texture2D || !tex0.isCreated) && text.length > 1
        if (charByChar) {
            return drawTextCharByChar(x, y, font, text, color, backgroundColor, widthLimit, centerX, false)
        }

        val texture = tex0 ?: return getSize(0, font.sizeInt)
        return writeText(x, y, color, backgroundColor, texture, centerX)

    }

    fun writeText(
        x: Int, y: Int,
        color: Int, backgroundColor: Int,
        texture: ITexture2D,
        centerX: Boolean
    ): Int {
        val w = texture.w
        val h = texture.h
        if (texture !is Texture2D || texture.isCreated) {
            texture.bind(GPUFiltering.TRULY_NEAREST, Clamping.CLAMP)
            val shader = ShaderLib.subpixelCorrectTextShader
            shader.use()
            var x2 = x
            if (centerX) x2 -= w / 2
            shader.v2(
                "pos",
                (x2 - GFX.windowX).toFloat() / GFX.windowWidth,
                1f - (y - GFX.windowY).toFloat() / GFX.windowHeight
            )
            shader.v2("size", w.toFloat() / GFX.windowWidth, -h.toFloat() / GFX.windowHeight)
            shader.v4("textColor", color)
            shader.v4("backgroundColor", backgroundColor)
            GFX.flat01.draw(shader)
            GFX.check()
        }
        return getSize(w, h)
    }

    fun writeText(
        x: Int, y: Int,
        font: Font, key: TextCacheKey,
        color: Int, backgroundColor: Int,
        centerX: Boolean = false
    ): Int {

        GFX.check()

        val tex0 = FontManager.getString(key)
        val charByChar = tex0 == null || tex0 !is Texture2D || !tex0.isCreated
        if (charByChar) {
            return drawTextCharByChar(x, y, font, key.text, color, backgroundColor, key.widthLimit, centerX, false)
        }

        val texture = tex0 ?: return getSize(0, font.sizeInt)
        return writeText(x, y, color, backgroundColor, texture, centerX)

    }

    // minimalistic function only using key, coordinates, colors, and whether it's centered horizontally
    fun writeText(
        x: Int, y: Int,
        key: TextCacheKey,
        color: Int, backgroundColor: Int,
        centerX: Boolean = false
    ): Int {

        GFX.check()

        val tex0 = FontManager.getString(key)
        val charByChar = tex0 == null || tex0 !is Texture2D || !tex0.isCreated
        if (charByChar) {
            return drawTextCharByChar(
                x, y, key.createFont(), key.text, color,
                backgroundColor, key.widthLimit, centerX, false
            )
        }

        val texture = tex0 ?: return getSize(0, getAvgFontSize(key.fontSizeIndex()).roundToInt())
        return writeText(x, y, color, backgroundColor, texture, centerX)

    }

    fun getTextSizeX(font: Font, text: String, widthLimit: Int) =
        getSizeX(getTextSize(font, text, widthLimit))

    fun getTextSizeY(font: Font, text: String, widthLimit: Int) =
        getSizeY(getTextSize(font, text, widthLimit))

    fun getTextSize(font: Font, text: String, widthLimit: Int) =
        FontManager.getSize(font, text, widthLimit)

    fun drawTexture(x: Int, y: Int, w: Int, h: Int, texture: ITexture2D, color: Int, tiling: Vector4fc?) {
        GFX.check()
        val shader = ShaderLib.flatShaderTexture
        shader.use()
        posSize(shader, x, y, w, h)
        shader.v4("color", color)
        if (tiling != null) shader.v4("tiling", tiling)
        else shader.v4("tiling", 1f, 1f, 0f, 0f)
        val tex = texture as? Texture2D
        texture.bind(
            0,
            tex?.filtering ?: GPUFiltering.NEAREST,
            tex?.clamping ?: Clamping.CLAMP
        )
        GFX.flat01.draw(shader)
        GFX.check()
    }

    fun drawTexture(matrix: Matrix4fArrayList, w: Int, h: Int, texture: Texture2D, color: Int, tiling: Vector4fc?) {
        matrix.scale(w.toFloat() / GFX.windowWidth, h.toFloat() / GFX.windowHeight, 1f)
        GFX.drawMode = ShaderPlus.DrawMode.COLOR
        draw3D(
            matrix, texture, color,
            Filtering.LINEAR, Clamping.CLAMP, tiling, UVProjection.Planar
        )
    }

    fun drawTexture(w: Int, h: Int, texture: VFrame, color: Int, tiling: Vector4fc?) {
        val matrix = Matrix4fArrayList()
        matrix.scale(w.toFloat() / GFX.windowWidth, h.toFloat() / GFX.windowHeight, 1f)
        GFX.drawMode = ShaderPlus.DrawMode.COLOR
        uploadAttractors0(texture.get3DShader())
        draw3D(
            matrix, texture, color,
            Filtering.LINEAR, Clamping.CLAMP, tiling, UVProjection.Planar
        )
    }

    fun drawCircle(
        x: Int, y: Int,
        radiusX: Float, radiusY: Float, innerRadius: Float, startDegrees: Float, endDegrees: Float, color: Vector4f
    ) {

        val rx = (x - GFX.windowX).toFloat() / GFX.windowWidth * 2 - 1
        val ry = (y - GFX.windowY).toFloat() / GFX.windowHeight * 2 - 1

        val stack = Matrix4fArrayList()
        stack.translate(rx, ry, 0f)
        stack.scale(2f * radiusX / GFX.windowWidth, 2f * radiusY / GFX.windowHeight, 1f)

        GFX.drawMode = ShaderPlus.DrawMode.COLOR

        // not perfect, but pretty good
        // anti-aliasing for the rough edges
        // not very economical, could be improved
        color.w /= 25f
        for (dx in 0 until 5) {
            for (dy in 0 until 5) {
                stack.next {
                    stack.translate((dx - 2f) / (2.5f * GFX.windowWidth), (dy - 2f) / (2.5f * GFX.windowHeight), 0f)
                    draw3DCircle(null, 0.0, stack, innerRadius, startDegrees, endDegrees, color)
                }
            }
        }

    }

    fun posSize(shader: Shader, x: Int, y: Int, w: Int, h: Int) {
        shader.v2(
            "pos",
            (x - GFX.windowX).toFloat() / GFX.windowWidth,
            1f - (y - GFX.windowY).toFloat() / GFX.windowHeight
        )
        shader.v2("size", w.toFloat() / GFX.windowWidth, -h.toFloat() / GFX.windowHeight)
    }

    fun posSize(shader: Shader, x: Float, y: Float, w: Float, h: Float) {
        shader.v2("pos", (x - GFX.windowX) / GFX.windowWidth, 1f - (y - GFX.windowY) / GFX.windowHeight)
        shader.v2("size", w / GFX.windowWidth, -h / GFX.windowHeight)
    }

    fun disableAdvancedGraphicalFeatures(shader: Shader) {
        shader.v1("forceFieldUVCount", 0)
        shader.v1("forceFieldColorCount", 0)
        shader.v3("cgSlope", 1f)
        shader.v3("cgOffset", 0f)
        shader.v3("cgPower", 1f)
        shader.v1("cgSaturation", 1f)
    }

    fun draw2D(texture: VFrame) {

        if (!texture.isCreated) throw RuntimeException("Frame must be loaded to be rendered!")
        val shader = texture.get3DShader()

        GFX.check()

        shader.use()
        shader.v1("filtering", Filtering.LINEAR.id)
        shader.v2("textureDeltaUV", 1f / texture.w, 1f / texture.h)
        shader.m4x4("transform", null)
        shader.v4("tint", 1f)
        shader.v4("tiling", 1f, 1f, 0f, 0f)
        shader.v1("drawMode", ShaderPlus.DrawMode.COLOR.id)
        shader.v1("uvProjection", UVProjection.Planar.id)

        disableAdvancedGraphicalFeatures(shader)

        texture.bind(0, Filtering.LINEAR, Clamping.CLAMP)
        if (shader == ShaderLib.shader3DYUV) {
            val w = texture.w
            val h = texture.h
            shader.v2("uvCorrection", w.toFloat() / ((w + 1) / 2 * 2), h.toFloat() / ((h + 1) / 2 * 2))
        }

        UVProjection.Planar.getBuffer().draw(shader)
        GFX.check()

    }

}