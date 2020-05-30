package me.anno.ui.input

import me.anno.ui.base.SpacePanel
import me.anno.ui.base.TextPanel
import me.anno.ui.base.constraints.WrapAlign
import me.anno.ui.base.groups.PanelListX
import me.anno.ui.input.components.Checkbox
import me.anno.ui.style.Style
import org.newdawn.slick.util.pathfinding.navmesh.Space

// checkbox with title
class BooleanInput(title: String, style: Style, startValue: Boolean): PanelListX(style){

    val checkView = Checkbox(style.getSize("fontSize",10), style, startValue)
    val titleView = TextPanel(title, style)

    init {
        this += titleView
        titleView.padding.right = 5
        this += checkView
        this += WrapAlign.LeftTop
    }

    fun setChangeListener(listener: (value: Boolean) -> Unit): BooleanInput {
        checkView.setChangeListener(listener)
        return this
    }

    override fun draw(x0: Int, y0: Int, x1: Int, y1: Int) {
        super.draw(x0, y0, x1, y1)
        if(isInFocus) isSelectedListener?.invoke()
    }

    private var isSelectedListener: (() -> Unit)? = null
    fun setIsSelectedListener(listener: () -> Unit): BooleanInput {
        isSelectedListener = listener
        return this
    }

}