package me.anno.ui.base

import me.anno.config.DefaultConfig
import me.anno.config.DefaultStyle.iconGray
import me.anno.fonts.FontManager
import me.anno.gpu.Cursor
import me.anno.gpu.GFX
import me.anno.gpu.GFX.loadTexturesSync
import me.anno.gpu.texture.Texture2D
import me.anno.input.MouseButton
import me.anno.ui.base.components.Padding
import me.anno.ui.base.groups.PanelContainer
import me.anno.ui.style.Style
import me.anno.utils.Quad
import me.anno.utils.Tabs
import me.anno.utils.isClickKey
import me.anno.utils.mixARGB
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

open class TextPanel(open var text: String, style: Style): Panel(style){

    var instantTextLoading = false
    var padding = style.getPadding("textPadding", 2)
    var isBold = style.getBoolean("textBold", false)
    var isItalic = style.getBoolean("textItalic", false)
    var fontName = style.getString("textFont", DefaultConfig.defaultFont)
    var textSize = style.getSize("textSize", 12)
    var textColor = style.getColor("textColor", iconGray)
    var focusTextColor = style.getColor("textColorFocused", -1)
    val hoverColor get() = mixARGB(textColor, focusTextColor, 0.5f)

    override fun getLayoutState(): Any? {
        val texture = if(canBeSeen){
            // keep the texture loaded, in case we need it
            val widthLimit = if(breaksIntoMultiline) w else -1
            FontManager.getString(fontName, textSize.toFloat(), text, isItalic, isBold, widthLimit)
        } else null
        val texWidth = texture?.w
        return Pair(super.getLayoutState(), texWidth)
    }

    override fun getVisualState(): Any? {
        val texture = if(canBeSeen){
            // keep the texture loaded, in case we need it
            val widthLimit = if(breaksIntoMultiline) w else -1
            FontManager.getString(fontName, textSize.toFloat(), text, isItalic, isBold, widthLimit)
        } else null
        return Triple(super.getVisualState(), (texture as? Texture2D)?.state, effectiveTextColor)
    }

    // breaks into multiline
    // todo use a guess instead???
    // todo at max use full text length???
    // todo only 5er steps?
    var breaksIntoMultiline = false

    // can be disabled for parents to copy ALL lines, e.g. for a bug report :)
    var disableCopy = false

    fun drawText(x: Int, y: Int, text: String, color: Int): Pair<Int, Int> {
        return GFX.drawText(this.x + x + padding.left, this.y + y + padding.top, fontName, textSize, isBold, isItalic,
            text, color, backgroundColor, if(breaksIntoMultiline) this.w else -1)
    }

    var minW2 = 0
    var minH2 = 0

    override fun calculateSize(w: Int, h: Int) {
        val text = if(text.isBlank()) "." else text
        val inst = instantTextLoading
        if(inst) loadTexturesSync.push(true)
        super.calculateSize(w, h)
        val (w2, h2) = GFX.getTextSize(fontName, textSize, isBold, isItalic, text, if(breaksIntoMultiline) w else -1)
        minW = max(1, w2 + padding.width)
        minH = max(1, h2 + padding.height)
        minW2 = minW
        minH2 = minH
        if(inst) loadTexturesSync.pop()
    }

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        val inst = instantTextLoading
        if(inst) loadTexturesSync.push(true)
        super.onDraw(x0, y0, x1, y1)
        drawText(0,0, text, effectiveTextColor)
        if(inst) loadTexturesSync.pop()
    }

    override fun onCopyRequested(x: Float, y: Float): String? {
        return if(disableCopy) super.onCopyRequested(x, y) else text
    }

    open var enableHoverColor = false

    open val effectiveTextColor get() =
        if(isHovered && enableHoverColor) hoverColor
        else if(isInFocus) focusTextColor
        else textColor

    override fun getCursor(): Long? = if(onClickListener == null) super.getCursor() else Cursor.drag

    override fun printLayout(tabDepth: Int) {
        println("${Tabs.spaces(tabDepth * 2)}${javaClass.simpleName}($weight, ${if(visibility==Visibility.VISIBLE) "v" else "_"}) $x $y += $w $h ($minW $minH) ${text.substring(0, min(text.length, 20))}")
    }

    override fun isKeyInput() = onClickListener != null
    override fun acceptsChar(char: Int) = when(char.toChar()){ '\t', '\n' -> false else -> true }
    override fun onKeyDown(x: Float, y: Float, key: Int) {
        if(key.isClickKey()) onClickListener?.invoke(x,y,MouseButton.LEFT,false)
    }

}