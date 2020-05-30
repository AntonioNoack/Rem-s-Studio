package me.anno.ui.base.groups

import me.anno.config.DefaultStyle.black
import me.anno.ui.base.Panel
import me.anno.ui.style.Style

abstract class PanelList(val sorter: Comparator<Panel>?, style: Style): PanelGroup(style){

    override val children = ArrayList<Panel>()
    var spacing = style.getSize("spacer.width", 0)
    var spaceColor = style.getColor("spacer.background", 0)

    fun clear() = children.clear()

    operator fun plusAssign(child: Panel){
        add(child)
    }

    open fun add(child: Panel): PanelList {
        children += child
        child.parent = this
        return this
    }

    override fun calculateSize(w: Int, h: Int) {
        if(sorter != null){
            children.sortWith(sorter)
        }
        super.calculateSize(w, h)
    }



}