package me.anno.ui.editor

import me.anno.ui.base.Panel
import me.anno.ui.base.TextPanel
import me.anno.ui.base.Visibility
import me.anno.ui.base.components.Padding
import me.anno.ui.base.groups.PanelGroup
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.style.Style
import me.anno.utils.Maths.mixARGB
import me.anno.utils.isClickKey
import kotlin.math.max

class SettingCategory(titleText: String, style: Style) : PanelGroup(style) {

    val title = TextPanel(titleText, style.getChild("group"))

    val content = PanelListY(style)
    val padding = Padding((title.font.size * .667f).toInt(), 0, 0, 0)

    init {
        title.parent = this
        // title.enableHoverColor = true
        title.textColor = mixARGB(title.textColor, title.textColor and 0xffffff, 0.5f)
        title.focusTextColor = title.textColor
        content.parent = this
        content.visibility = Visibility.GONE
        title.setSimpleClickListener { toggle() }
    }

    fun show2() {
        content.visibility = Visibility.VISIBLE
    }

    fun toggle() {
        content.visibility =
            if (content.visibility == Visibility.VISIBLE) Visibility.GONE
            else Visibility.VISIBLE
    }

    override fun onKeyTyped(x: Float, y: Float, key: Int) {
        if (key.isClickKey()) toggle()
    }

    override fun acceptsChar(char: Int) = char.isClickKey()
    override fun isKeyInput() = true

    override val children: List<Panel> = listOf(this.title, content)
    override fun remove(child: Panel) {
        throw RuntimeException("Not supported!")
    }

    val isEmpty
        get() = content
            .children
            .firstOrNull { it.visibility == Visibility.VISIBLE } == null

    override fun calculateSize(w: Int, h: Int) {
        if (isEmpty) {
            minW = 0
            minH = 0
        } else {
            title.calculateSize(w, h)
            if (content.visibility == Visibility.GONE) {
                minW = title.minW
                minH = title.minH
            } else {
                content.calculateSize(w - padding.width, h)
                minW = max(title.minW, content.minW + padding.width)
                minH = title.minH + content.minH + padding.height
            }
        }
    }

    override fun placeInParent(x: Int, y: Int) {
        super.placeInParent(x, y)
        title.placeInParent(x, y)
        content.placeInParent(x + padding.left, y + title.minH + padding.top)
    }

    operator fun plusAssign(child: Panel) {
        content += child
    }

}