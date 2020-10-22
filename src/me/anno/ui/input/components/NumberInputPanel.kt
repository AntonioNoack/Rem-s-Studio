package me.anno.ui.input.components

import me.anno.input.MouseButton
import me.anno.objects.animation.AnimatedProperty
import me.anno.objects.animation.drivers.AnimationDriver
import me.anno.studio.RemsStudio.editorTime
import me.anno.studio.RemsStudio.onSmallChange
import me.anno.studio.RemsStudio.selectedInspectable
import me.anno.ui.input.FloatInput
import me.anno.ui.input.IntInput
import me.anno.ui.input.NumberInput
import me.anno.ui.style.Style
import me.anno.utils.get

class NumberInputPanel(val owningProperty: AnimatedProperty<*>?,
                       val indexInProperty: Int,
                       val numberInput: NumberInput,
                       style: Style
): PureTextInput(style.getChild("deep")) {

        val driver get() = owningProperty?.drivers?.get(indexInProperty)
        val hasDriver get() = driver != null
        var lastTime = editorTime

        override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
            val editorTime = editorTime
            if(lastTime != editorTime && owningProperty != null && owningProperty.isAnimated){
                lastTime = editorTime
                val value = owningProperty[editorTime]!![indexInProperty]
                when(numberInput){
                    is IntInput -> numberInput.setValue(value.toLong(), false)
                    is FloatInput -> numberInput.setValue(value.toDouble(), false)
                    else -> throw RuntimeException()
                }
            }
            val driver = driver
            if (driver != null) {
                val driverName = driver.getDisplayName()
                if (text != driverName) {
                    text = driverName
                    updateChars(false)
                }
            }
            super.onDraw(x0, y0, x1, y1)
        }

        override fun onMouseDown(x: Float, y: Float, button: MouseButton) {
            if (!hasDriver) {
                super.onMouseDown(x, y, button)
                numberInput.onMouseDown(x, y, button)
            }
        }

        override fun onMouseUp(x: Float, y: Float, button: MouseButton) {
            if (!hasDriver) numberInput.onMouseUp(x, y, button)
        }

        override fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float) {
            if (!hasDriver) numberInput.onMouseMoved(x, y, dx, dy)
        }

        override fun onMouseClicked(x: Float, y: Float, button: MouseButton, long: Boolean) {
            if (owningProperty != null && (!button.isLeft || long)) {
                val oldDriver = owningProperty.drivers[indexInProperty]
                AnimationDriver.openDriverSelectionMenu(oldDriver) {
                    owningProperty.drivers[indexInProperty] = it
                    if (it != null) selectedInspectable = it
                    else {
                        text = when(numberInput){
                            is IntInput -> numberInput.stringify(numberInput.lastValue)
                            is FloatInput -> numberInput.stringify(numberInput.lastValue)
                            else -> throw RuntimeException()
                        }
                    }
                    onSmallChange("number-set-driver")
                }
                return
            }
            super.onMouseClicked(x, y, button, long)
        }

        override fun onEmpty(x: Float, y: Float) {
            if (hasDriver) {
                owningProperty?.drivers?.set(indexInProperty, null)
            }
            numberInput.onEmpty(x, y)
        }

        override fun acceptsChar(char: Int): Boolean {
            return when(char.toChar()){
                '\t', '\n' -> false
                else -> true
            }
        }

    }