package me.anno.ui.input.components

import me.anno.gpu.GFX
import me.anno.gpu.TextureLib.whiteTexture
import me.anno.gpu.texture.Texture2D
import me.anno.input.MouseButton
import me.anno.objects.cache.Cache
import me.anno.studio.RemsStudio
import me.anno.ui.base.Panel
import me.anno.ui.style.Style
import kotlin.math.min

class Checkbox(startValue: Boolean, val size: Int, style: Style): Panel(style.getChild("checkbox")){

    companion object {
        fun getImage(checked: Boolean): Texture2D? = Cache.getIcon(if(checked) "checked.png" else "unchecked.png", true)
    }

    var isChecked = startValue

    init {
        minW = size + 2
        minH = size + 2
    }

    override fun draw(x0: Int, y0: Int, x1: Int, y1: Int) {
        super.draw(x0, y0, x1, y1)

        val size = min(w, h)
        if(size > 0){
            // draw the icon on/off
            GFX.drawTexture(x0+(w-size)/2, y0+(h-size)/2, size, size, getImage(isChecked) ?: whiteTexture, -1, null)
        }

    }

    private var onCheckedChanged: ((Boolean) -> Unit)? = null

    fun setChangeListener(listener: (Boolean) -> Unit): Checkbox {
        onCheckedChanged = listener
        return this
    }

    fun toggle(){
        isChecked = !isChecked
        onCheckedChanged?.invoke(isChecked)
        RemsStudio.onSmallChange()
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

    override fun getClassName() = "CheckBox"

}