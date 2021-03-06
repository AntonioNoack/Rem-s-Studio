package me.anno.ui.base.groups

import me.anno.ui.base.Panel
import me.anno.ui.base.Visibility
import me.anno.ui.style.Style
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

open class PanelListY(sorter: Comparator<Panel>?, style: Style) : PanelList(sorter, style) {
    constructor(style: Style) : this(null, style)

    var sumConst = 0
    var sumWeight = 0f

    override fun calculateSize(w: Int, h: Int) {
        super.calculateSize(w, h)

        val x = x
        var maxX = x
        var constantSum = 0
        var weightSum = 0f

        val availableW = w - padding.width
        val availableH = h - padding.height

        for (child in children.filter { it.visibility != Visibility.GONE }) {
            child.calculateSize(availableW, availableH)
            // apply constraints?
            constantSum += child.minH
            maxX = max(maxX, child.x + child.minW)
            weightSum += max(0f, child.weight)
        }

        val spaceCount = children.size - 1
        constantSum += spacing * spaceCount
        sumConst = constantSum
        sumWeight = weightSum

        minH = constantSum + padding.height
        minW = (maxX - x) + padding.width

    }

    override fun placeInParent(x: Int, y: Int) {
        super.placeInParent(x, y)

        var perWeight = 0f
        val perConst = 1f // could be used to force elements into the available space

        val availableW = w - padding.width
        val availableH = h - padding.height

        if (availableH > sumConst && sumWeight > 1e-16f) {
            val extraAvailable = availableH - sumConst
            perWeight = extraAvailable / sumWeight
        }

        var currentY = y + padding.top
        val childX = x + padding.left

        for (child in children.filter { it.visibility != Visibility.GONE }) {
            var childH = (perConst * child.minH + perWeight * max(0f, child.weight)).roundToInt()
            val currentH = currentY - y
            val remainingH = availableH - currentH
            childH = min(childH, remainingH)
            child.calculateSize(availableW, childH)
            child.placeInParent(childX, currentY)
            child.applyPlacement(availableW, childH)
            currentY += childH + spacing
        }

    }

}