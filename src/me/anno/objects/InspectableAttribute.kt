package me.anno.objects

import me.anno.ui.base.groups.PanelList
import me.anno.ui.style.Style

interface InspectableAttribute {
    fun createInspector(list: PanelList, actor: Transform, style: Style)
}