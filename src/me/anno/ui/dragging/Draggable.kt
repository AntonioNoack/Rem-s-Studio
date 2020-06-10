package me.anno.ui.dragging

import me.anno.gpu.GFX
import me.anno.ui.base.Panel

class Draggable(
    private val content: String,
    private val contentType: String,
    val ui: Panel
): IDraggable {

    override fun draw(x: Int, y: Int) {
        ui.placeInParent(x, y)
        ui.draw(x, y, x + ui.w, y + ui.h)
    }

    override fun getSize(w: Int, h: Int): Pair<Int, Int> {
        ui.calculateSize(w, h)
        ui.applyConstraints()
        return ui.w to ui.h
    }

    override fun getContent(): String = content
    override fun getContentType(): String = contentType

}