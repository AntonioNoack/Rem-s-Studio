package me.anno.ui.base.constraints

import me.anno.ui.base.Panel
import me.anno.ui.base.components.Padding
import me.anno.ui.base.groups.PanelContainer
import me.anno.ui.style.Style
import me.anno.utils.Maths

class SizeLimitingContainer(
    child: Panel,
    var sizeX: Int, var sizeY: Int,
    style: Style
) : PanelContainer(child, Padding.Zero, style) {

    override fun calculateSize(w: Int, h: Int) {
        val limitedW = if (sizeX < 0) w else Maths.min(w, sizeX)
        val limitedH = if (sizeY < 0) h else Maths.min(h, sizeY)
        super.calculateSize(limitedW, limitedH)
        if (sizeX >= 0) minW = kotlin.math.min(minW, padding.width + sizeX)
        if (sizeY >= 0) minH = kotlin.math.min(minH, padding.height + sizeY)
    }

}