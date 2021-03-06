package me.anno.ui.input.components

import me.anno.cache.instances.ImageCache.getInternalTexture
import me.anno.gpu.drawing.DrawTextures.drawTexture
import me.anno.gpu.TextureLib.whiteTexture
import me.anno.gpu.texture.Texture2D
import me.anno.input.MouseButton
import me.anno.studio.rems.RemsStudio
import me.anno.ui.base.Panel
import me.anno.ui.style.Style
import org.lwjgl.glfw.GLFW
import kotlin.math.min

open class Checkbox(startValue: Boolean, val size: Int, style: Style) : Panel(style.getChild("checkbox")) {

    // todo hover/toggle/focus color change

    companion object {
        fun getImage(checked: Boolean): Texture2D? =
            getInternalTexture(if (checked) "checked.png" else "unchecked.png", true)
    }

    var isChecked = startValue

    override fun calculateSize(w: Int, h: Int) {
        super.calculateSize(w, h)
        minW = size + 2
        minH = size + 2
    }

    override fun getVisualState(): Any? {
        return Triple(super.getVisualState(), getImage(isChecked)?.state, isHovered)
    }

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        super.onDraw(x0, y0, x1, y1)

        val size = min(w, h)
        if (size > 0) {
            val color = if (isHovered) 0xccffffff.toInt() else -1
            // draw the icon on/off
            drawTexture(
                x0 + (w - size) / 2,
                y0 + (h - size) / 2,
                size,
                size,
                getImage(isChecked) ?: whiteTexture,
                color,
                null
            )
        }

    }

    private var onCheckedChanged: ((Boolean) -> Unit)? = null

    fun setChangeListener(listener: (Boolean) -> Unit): Checkbox {
        onCheckedChanged = listener
        return this
    }

    fun toggle() {
        // todo show undo somehow...
        RemsStudio.largeChange("Toggled to ${!isChecked}") {
            isChecked = !isChecked
            onCheckedChanged?.invoke(isChecked)
        }
    }

    override fun onMouseClicked(x: Float, y: Float, button: MouseButton, long: Boolean) {
        toggle()
    }

    override fun onDoubleClick(x: Float, y: Float, button: MouseButton) {
        toggle()
    }

    override fun onEnterKey(x: Float, y: Float) {
        toggle()
    }

    override fun onKeyTyped(x: Float, y: Float, key: Int) {
        when (key) {
            GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_UP -> toggle()
        }
    }

    override fun acceptsChar(char: Int) = false // ^^
    override fun isKeyInput() = true

}