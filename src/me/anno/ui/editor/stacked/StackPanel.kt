package me.anno.ui.editor.stacked

import me.anno.input.MouseButton
import me.anno.language.translation.NameDesc
import me.anno.objects.inspectable.Inspectable
import me.anno.ui.base.components.Padding
import me.anno.ui.base.groups.PanelContainer
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.menu.Menu.openMenu
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.base.text.TextPanel
import me.anno.ui.style.Style

// todo is glTexture2D a bottleneck for playback?

/**
 * done allow the user to add fields
 * done allow the user to customize fields
 * done allow the user to remove fields
 * todo reorder fields by dragging up/down
 * done copy fields
 * todo paste fields
 * todo add left-padding to all fields...
 * */
abstract class StackPanel(
    val titleText: String,
    tooltipText: String,
    val options: List<Option>,
    val values: List<Inspectable>,
    style: Style
) : PanelListY(style) {

    val content = PanelListY(style)

    val title = object : TextPanel(titleText, style) {
        init {
            focusTextColor = textColor
        }

        override fun onMouseClicked(x: Float, y: Float, button: MouseButton, long: Boolean) {
            /*if(button.isLeft && !long && !content.isEmpty()){
                val isHidden = content.children.firstOrNull()?.visibility == Visibility.GONE
                val visibility = if(isHidden) Visibility.VISIBLE else Visibility.GONE
                content.children.forEach { panel ->
                    panel.visibility = visibility
                }
            } else */
            super.onMouseClicked(x, y, button, long)
        }
    }

    init {
        add(this.title)
        add(PanelContainer(content, Padding(10, 0, 0, 0), style))
        values.forEachIndexed { index, it ->
            addComponent(getOptionFromInspectable(it)!!, index, false)
        }
        setTooltip(tooltipText)
    }

    fun showMenu() {
        openMenu(
            options.map { option ->
                MenuOption(
                    NameDesc(
                        "Append %1", "Add an element at the end of the list",
                        "ui.option.append"
                    ).with("%1", option.title)
                ) {
                    addComponent(option, content.children.size, true)
                }
            }
        )
    }

    override fun onMouseClicked(x: Float, y: Float, button: MouseButton, long: Boolean) {
        when {
            button.isRight || long || content.isEmpty() -> {
                showMenu()
            }
            else -> super.onMouseClicked(x, y, button, long)
        }
    }

    fun addComponent(option: Option, index: Int, notify: Boolean) {
        val component = option.value0 ?: option.generator()
        content.add(index, OptionPanel(this, option.title, option.description, component))
        if (notify) {
            onAddComponent(component, index)
            invalidateLayout()
        }
    }

    fun removeComponent(component: Inspectable) {
        content.children.removeIf { it is OptionPanel && it.value === component }
        onRemoveComponent(component)
        invalidateLayout()
    }


    abstract fun onAddComponent(component: Inspectable, index: Int)
    abstract fun onRemoveComponent(component: Inspectable)
    abstract fun getOptionFromInspectable(inspectable: Inspectable): Option?


}