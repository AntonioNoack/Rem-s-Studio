package me.anno.ui.input.components

import me.anno.gpu.Cursor
import me.anno.gpu.GFX
import me.anno.input.Input.isControlDown
import me.anno.input.Input.isShiftDown
import me.anno.utils.clamp
import me.anno.ui.base.TextPanel
import me.anno.ui.style.Style
import me.anno.utils.BinarySearch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.streams.toList

open class PureTextInput(style: Style): TextPanel("", style.getChild("edit")) {

    val characters = ArrayList<Int>()

    fun setCursorToEnd(){
        cursor1 = characters.size
        cursor2 = cursor1
    }

    fun updateChars(){
        characters.clear()
        characters.addAll(text.codePoints().toList())
        changeListener(text)
    }

    fun updateText(){
        text = characters.joinToString(""){ String(Character.toChars(it)) }
        changeListener(text)
    }

    var cursor1 = 0
    var cursor2 = 0

    var placeholderColor = style.getColor("placeholderColor", textColor and 0x7fffffff)
    var placeholder = ""

    fun deleteSelection(): Boolean {
        val min = min(cursor1, cursor2)
        val max = max(cursor1, cursor2)
        for(i in max-1 downTo min){
            characters.removeAt(i)
        }
        updateText()
        return max > min
    }

    var drawingOffset = 0
    var lastMove = 0L

    val wasJustChanged get() = abs(GFX.lastTime-lastMove) < 200_000_000

    fun calculateOffset(required: Int, cursor: Int){
        // center the cursor, 1/3 of the width, if possible;
        // clamp left/right
        drawingOffset = -clamp(cursor - w / 3, 0, max(0, required - w))
    }

    override fun draw(x0: Int, y0: Int, x1: Int, y1: Int) {
        // super.draw(x0, y0, x1, y1)
        drawBackground()
        val x = x + padding.left
        val y = y + padding.top
        val usePlaceholder = text.isEmpty()
        val drawnText = if(usePlaceholder) placeholder else text
        val wh = drawText(drawingOffset, 0, drawnText, if(usePlaceholder) placeholderColor else if(isInFocus) focusTextColor else textColor)
        /*GFX.drawText(x+drawingOffset, y, fontSize, drawnText,
            if(usePlaceholder) placeholderColor else if(isInFocus) focusTextColor else textColor,
            backgroundColor)*/
        val blinkVisible = ((System.nanoTime() / 500_000_000L) % 2L == 0L)
        val showBars = blinkVisible || wasJustChanged
        if(isInFocus && (showBars || cursor1 != cursor2)){
            ensureCursorBounds()
            val padding = textSize/4
            val cursorX1 = if(cursor1 == 0) -1 else GFX.getTextSize(fontName, textSize, isBold, isItalic, text.substring(0, cursor1)).first-1
            if(cursor1 != cursor2){
                val cursorX2 = if(cursor2 == 0) -1 else GFX.getTextSize(fontName, textSize, isBold, isItalic, text.substring(0, cursor2)).first-1
                val min = min(cursorX1, cursorX2)
                val max = max(cursorX1, cursorX2)
                GFX.drawRect(x+min+drawingOffset, y+padding, max-min, h-2*padding, textColor and 0x3fffffff) // marker
                if(showBars) GFX.drawRect(x+cursorX2+drawingOffset, y+padding, 2, h-2*padding, textColor) // cursor 1
                calculateOffset(wh.first, cursorX2)
            } else {
                calculateOffset(wh.first, cursorX1)
            }
            if(showBars) GFX.drawRect(x+cursorX1+drawingOffset, y+padding, 2, h-2*padding, textColor) // cursor 2
        }
    }

    fun addKey(codePoint: Int) = insert(codePoint)

    fun insert(insertion: String){
        lastMove = GFX.lastTime
        insertion.codePoints().forEach {
            insert(it)
        }
    }

    fun insert(insertion: Int){
        lastMove = GFX.lastTime
        deleteSelection()
        characters.add(cursor1, insertion)
        updateText()
        cursor1++
        cursor2++
        ensureCursorBounds()
    }

    fun deleteBefore(){
        lastMove = GFX.lastTime
        if(!deleteSelection() && cursor1 > 0){
            characters.removeAt(cursor1-1)
            updateText()
            cursor1--
            cursor2--
        }
        ensureCursorBounds()
    }

    fun deleteAfter(){
        lastMove = GFX.lastTime
        if(!deleteSelection() && cursor1 < characters.size){
            characters.removeAt(cursor1)
            updateText()
        }
        ensureCursorBounds()
    }

    fun ensureCursorBounds(){
        cursor1 = clamp(cursor1, 0, characters.size)
        cursor2 = clamp(cursor2, 0, characters.size)
    }

    override fun onCharTyped(x: Float, y: Float, key: Int) {
        lastMove = GFX.lastTime
        addKey(key)
    }

    fun moveRight(){
        lastMove = GFX.lastTime
        if(isShiftDown){
            cursor2++
        } else {
            if(cursor2 != cursor1){
                cursor1 = cursor2
            } else {
                cursor1++
                cursor2 = cursor1
            }
        }
        ensureCursorBounds()
    }

    fun moveLeft(){
        lastMove = GFX.lastTime
        if(isShiftDown){
            cursor2--
        } else {
            cursor1--
            cursor2 = cursor1
        }
        ensureCursorBounds()
    }

    override fun onBackKey(x: Float, y: Float) {
        deleteBefore()
    }

    override fun onDeleteKey(x: Float, y: Float) {
        deleteAfter()
    }

    override fun onCopyRequested(x: Float, y: Float): String? {
        return characters.subList(min(cursor1, cursor2), max(cursor1, cursor2)).joinToString(""){ String(Character.toChars(it)) }
    }

    override fun onPaste(x: Float, y: Float, data: String, type: String) {
        insert(data)
    }

    var changeListener: (text: String) -> Unit = {
            _ ->
    }

    override fun onMouseClicked(x: Float, y: Float, button: Int, long: Boolean) {
        lastMove = GFX.lastTime
        if(isControlDown){
            cursor1 = 0
            cursor2 = characters.size
        } else {
            // find the correct location for the cursor
            val localX = x - (this.x + padding.left + drawingOffset)
            // char positions are expensive to calculate, so we use binary search and sparse evaluation
            val list = BinarySearch.ExpensiveList(characters.size+1){
                if(it == 0) 0f
                else { GFX.getTextSize(fontName, textSize, isBold, isItalic, text.substring(0, it)).first.toFloat() }
            }
            var index = list.binarySearch { it.compareTo(localX) }
            if(index < 0) index = -1 - index
            // find the closer neighbor
            if(index > 0 && index < characters.size && abs(list[index-1]-x) < abs(list[index]-x)){
                index--
            }
            cursor1 = index
            cursor2 = index
        }
        super.onMouseClicked(x, y, button, long)
    }

    override fun onDoubleClick(x: Float, y: Float, button: Int) {
        cursor1 = 0
        cursor2 = characters.size
    }

    override fun onSelectAll(x: Float, y: Float) {
        cursor1 = 0
        cursor2 = characters.size
    }

    override fun onEmpty(x: Float, y: Float) {
        deleteSelection()
    }

    fun clear(){
        lastMove = GFX.lastTime
        text = ""
        characters.clear()
        cursor1 = 0
        cursor2 = 0
    }

    override fun onGotAction(x: Float, y: Float, dx: Float, dy: Float, action: String, isContinuous: Boolean): Boolean {
        when(action){
            "DeleteAfter" -> deleteAfter()
            "DeleteBefore" -> deleteBefore()
            "DeleteSelection" -> deleteSelection()
            "MoveLeft" -> moveLeft()
            "MoveRight" -> moveRight()
            "Clear" -> clear()
            else -> return super.onGotAction(x, y, dx, dy, action, isContinuous)
        }
        return true
    }

    override fun getCursor() = Cursor.editText
    override fun isKeyInput() = true
    override fun getClassName() = "PureTextInput"

}