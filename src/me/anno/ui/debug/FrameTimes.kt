package me.anno.ui.debug

import me.anno.config.DefaultConfig
import me.anno.gpu.GFXx2D.drawRect
import me.anno.ui.base.Panel
import me.anno.ui.base.text.TextPanel
import kotlin.math.max

object FrameTimes : Panel(DefaultConfig.style.getChild("fps")) {

    val width = 200
    val height = 50

    val colors = TextPanel("", style)
    val textColor = colors.textColor

    override fun calculateSize(w: Int, h: Int) {
        minW = width
        minH = height
    }

    var maxValue = 0f
    val values = FloatArray(width)
    var nextIndex = 0
    fun putValue(value: Float) {
        values[nextIndex] = value
        nextIndex = (nextIndex + 1) % width
        val max = values.max()!!
        maxValue = max(maxValue * 0.995f, max)
    }

    fun draw() {
        canBeSeen = true
        draw(x, y, x + w, y + h)
    }

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        drawBackground()
        val indexOffset = nextIndex - 1 + width
        for (x in x0 until x1) {
            val i = x - this.x
            val v = values[(indexOffset + i) % width]
            val barHeight = (height * v / maxValue).toInt()
            val barColor = textColor
            drawRect(x, y + height - barHeight, 1, barHeight, barColor)
        }
    }

}