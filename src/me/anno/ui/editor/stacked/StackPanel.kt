package me.anno.ui.editor.stacked

import me.anno.config.DefaultConfig.style
import me.anno.gpu.GFX.openMenu
import me.anno.input.MouseButton
import me.anno.objects.Inspectable
import me.anno.ui.base.TextPanel
import me.anno.ui.base.Visibility
import me.anno.ui.base.components.Padding
import me.anno.ui.base.groups.PanelContainer
import me.anno.ui.base.groups.PanelListY

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
    val values: List<Option>
) : PanelListY(style) {

    val content = PanelListY(style)

    val title = object: TextPanel(titleText, style){
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
        add(PanelContainer(content, Padding(10,0,0,0), style))
        values.forEachIndexed { index, it ->
            addComponent(it, index, false)
        }
        setTooltip(tooltipText)
    }

    fun showMenu() {
        openMenu(
            options.map { option ->
                "Append ${option.title}" to {
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
        content.add(index, OptionPanel(this, option.title, option.tooltipText, component))
        if (notify){
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