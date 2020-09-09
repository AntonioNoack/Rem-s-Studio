package me.anno.ui.base

import me.anno.config.DefaultConfig
import me.anno.config.DefaultStyle.iconGray
import me.anno.gpu.Cursor
import me.anno.gpu.GFX
import me.anno.gpu.GFX.loadTexturesSync
import me.anno.ui.base.components.Padding
import me.anno.ui.style.Style
import me.anno.utils.mixARGB

// todo cache ui, as long as it's not changing?
// would reduce gpu usage, but make things harder...

open class TextPanel(open var text: String, style: Style): Panel(style){

    var instantTextLoading = false
    var padding = style.getPadding("textPadding", 2)
    var isBold = style.getBoolean("textBold", false)
    var isItalic = style.getBoolean("textItalic", false)
    var fontName = style.getString("textFont", DefaultConfig.defaultFont)
    var textSize = style.getSize("textSize", 12)
    var textColor = style.getColor("textColor", iconGray)
    val targetHoverColor = style.getColor("textTargetColor", -1)
    val hoverColor get() = mixARGB(textColor, targetHoverColor, 0.5f)
    var focusTextColor = style.getColor("textColorFocused", -1)

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

    override fun calculateSize(w: Int, h: Int) {
        if(text.isBlank()){
            super.calculateSize(w, h)
        } else {
            val inst = instantTextLoading
            if(inst) loadTexturesSync.push(true)
            super.calculateSize(w, h)
            val (w2, h2) = GFX.getTextSize(fontName, textSize, isBold, isItalic, text, if(breaksIntoMultiline) w else -1)
            minW = w2 + padding.width
            minH = h2 + padding.height
            if(inst) loadTexturesSync.pop()
        }
    }

    override fun draw(x0: Int, y0: Int, x1: Int, y1: Int) {
        val inst = instantTextLoading
        if(inst) loadTexturesSync.push(true)
        super.draw(x0, y0, x1, y1)
        drawText(0,0, text, effectiveTextColor)
        if(inst) loadTexturesSync.pop()
    }

    override fun onCopyRequested(x: Float, y: Float): String? {
        if(disableCopy) return super.onCopyRequested(x, y)
        else return text
    }

    open val enableHoverColor get() = false

    open val effectiveTextColor get() =
        if(isHovered && enableHoverColor) hoverColor
        else if(isInFocus) focusTextColor
        else textColor

    override fun getCursor(): Long? = if(onClickListener == null) super.getCursor() else Cursor.drag

}