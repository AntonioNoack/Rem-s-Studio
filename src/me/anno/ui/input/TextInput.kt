package me.anno.ui.input

import me.anno.gpu.Cursor
import me.anno.gpu.GFX
import me.anno.ui.base.components.Padding
import me.anno.ui.base.groups.PanelContainer
import me.anno.ui.input.components.PureTextInput
import me.anno.ui.style.Style
import java.io.File

open class TextInput(title: String, style: Style): PanelContainer(
    PureTextInput(
        style
    ), Padding(0), style) {

    constructor(title: String, style: Style, v0: String): this(title, style){
        setText(v0, false)
    }

    val base = child as PureTextInput

    init {
        base.placeholder = title
        base.backgroundColor = backgroundColor
    }

    fun setCursorToEnd() = base.setCursorToEnd()
    fun updateChars(notify: Boolean) = base.updateChars(notify)
    fun updateText(notify: Boolean) = base.updateText(notify)

    fun setPlaceholder(text: String): TextInput {
        base.placeholder = text
        return this
    }

    fun deleteKeys() = base.deleteSelection()
    fun addKey(codePoint: Int) = base.addKey(codePoint)
    fun insert(insertion: String) = base.insert(insertion)
    fun insert(insertion: Int) = base.insert(insertion)
    fun deleteBefore() = base.deleteBefore()
    fun deleteAfter() = base.deleteAfter()
    fun ensureCursorBounds() = base.ensureCursorBounds()
    fun setChangeListener(listener: (text: String) -> Unit): TextInput {
        base.setChangeListener(listener)
        return this
    }

    val text get() = base.text

    fun setText(text: String, notify: Boolean): TextInput {
        base.text = text
        updateChars(notify)
        return this
    }

    override fun draw(x0: Int, y0: Int, x1: Int, y1: Int) {
        super.draw(x0, y0, x1, y1)
        if(base.isInFocus){
            isSelectedListener?.invoke()
        }
    }

    private var isSelectedListener: (() -> Unit)? = null
    fun setIsSelectedListener(listener: () -> Unit): TextInput {
        isSelectedListener = listener
        return this
    }

    override fun onPasteFiles(x: Float, y: Float, files: List<File>) {
        val keyFile = files.firstOrNull() ?: return
        setText(keyFile.toString(), true)
    }

    override fun getCursor(): Long = Cursor.drag
    override fun isKeyInput() = true
    override fun getClassName() = "TextInput"


}