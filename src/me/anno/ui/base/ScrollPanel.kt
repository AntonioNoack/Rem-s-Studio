package me.anno.ui.base

import me.anno.gpu.GFX
import me.anno.maths.clamp
import me.anno.ui.base.components.Padding
import me.anno.ui.base.constraints.WrapAlign
import me.anno.ui.base.groups.PanelContainer
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.style.Style
import me.anno.utils.warn
import java.lang.RuntimeException
import kotlin.math.max
import kotlin.math.min

open class ScrollPanel(child: Panel, padding: Padding,
                       style: Style,
                       alignX: WrapAlign.AxisAlignment): PanelContainer(child, padding, style){

    constructor(style: Style, padding: Padding, align: WrapAlign.AxisAlignment): this(PanelListY(style), padding, style, align)

    init {
        child += WrapAlign(alignX, WrapAlign.AxisAlignment.MIN)
    }

    var scrollPosition = 0f
    val maxLength = 100_000

    val maxScrollPosition get() = max(0, child.minH - h)

    override fun calculateSize(w: Int, h: Int) {
        super.calculateSize(w, h)

        if(h > GFX.height) throw RuntimeException()

        child.calculateSize(w-padding.width, maxLength)

        minW = child.minW + padding.width
        minH = 100

        if(h > GFX.height) throw RuntimeException()

        // warn("scroll fini $x += $w ($minW), $y += $h ($minH) by ${child.h}, ${child.minH}")

    }

    override fun placeInParent(x: Int, y: Int) {
        super.placeInParent(x, y)

        if(h > GFX.height) throw RuntimeException()

        val scroll = scrollPosition.toInt()

        child.placeInParent(x+padding.left,y+padding.top-scroll)

    }

    override fun getClassName(): String = "ScrollPanel"

    override fun onMouseWheel(x: Float, y: Float, dx: Float, dy: Float) {
        val delta = dx-dy
        val scale = 20f
        scrollPosition = clamp(scrollPosition + scale * delta, 0f, maxScrollPosition.toFloat())
    }

}