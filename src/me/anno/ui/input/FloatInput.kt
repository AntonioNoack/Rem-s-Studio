package me.anno.ui.input

import me.anno.gpu.GFX
import me.anno.animation.AnimatedProperty
import me.anno.animation.Type
import me.anno.parser.SimpleExpressionParser
import me.anno.parser.SimpleExpressionParser.toDouble
import me.anno.studio.StudioBase.Companion.shiftSlowdown
import me.anno.ui.style.Style
import me.anno.utils.types.AnyToFloat.get
import org.joml.*
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

open class FloatInput(
    style: Style, title: String,
    type: Type = Type.FLOAT,
    owningProperty: AnimatedProperty<*>?,
    indexInProperty: Int
) : NumberInput(style, title, type, owningProperty, indexInProperty) {

    var lastValue: Double = getValue(type.defaultValue)
    var changeListener: (value: Double) -> Unit = { }

    init {
        inputPanel.setChangeListener {
            val newValue = parseValue(it)
            if (newValue != null) {
                lastValue = newValue
                changeListener(newValue)
            }
        }
    }

    constructor(
        title: String,
        owningProperty: AnimatedProperty<*>,
        indexInProperty: Int,
        time: Double,
        style: Style
    ) : this(style, title, owningProperty.type, owningProperty, indexInProperty) {
        when (val value = owningProperty[time]) {
            is Float -> setValue(value, false)
            is Double -> setValue(value, false)
            else -> throw RuntimeException("Unknown type $value for ${javaClass.simpleName}")
        }
    }

    constructor(title: String, value0: Float, type: Type, style: Style) : this(style, title, type, null, 0) {
        setValue(value0, false)
    }

    constructor(title: String, value0: Double, type: Type, style: Style) : this(style, title, type, null, 0) {
        setValue(value0, false)
    }

    var allowInfinity = false

    fun parseValue(text: String): Double? {
        val trimmed = text.trim()
        val newValue =
            if (trimmed.isEmpty()) 0.0 else trimmed.toDoubleOrNull() ?: SimpleExpressionParser.parseDouble(trimmed)
        if (newValue == null || !((allowInfinity && !newValue.isNaN()) || newValue.isFinite())) return null
        return newValue
    }

    fun setValue(v: Int, notify: Boolean) = setValue(v.toDouble(), notify)
    fun setValue(v: Long, notify: Boolean) = setValue(v.toDouble(), notify)
    fun setValue(v: Float, notify: Boolean) = setValue(v.toDouble(), notify)

    fun stringify(v: Double): String =
        if (type.defaultValue is Double) v.toString()
        else v.toFloat().toString()

    override fun changeValue(dx: Float, dy: Float) {
        val scale = 20f * shiftSlowdown
        val size = scale / max(GFX.width, GFX.height)
        val dx0 = dx * size
        val dy0 = dy * size
        val delta = dx0 - dy0
        // chose between exponential and linear curve, depending on the use-case
        var value = lastValue
        if (type.hasLinear || value == 0.0) value += delta * 0.1 * type.unitScale
        if (type.hasExponential) value *= StrictMath.pow(
            if (lastValue < 0) 1.0 / 1.03 else 1.03,
            delta * if (type.hasLinear) 1.0 else 3.0
        )
        setValueClamped(value, true)
    }

    fun setValueClamped(value: Double, notify: Boolean) {
        val clampFunc = type.clampFunc
        if (clampFunc == null) {
            setValue(value, notify)
        } else {
            val input: Any = when (type.defaultValue) {
                is Boolean -> value >= 0.5
                is Float -> value.toFloat()
                is Double -> value
                is Int -> value.roundToInt()
                is Long -> value.roundToLong()
                is Vector2fc, is Vector3fc,
                is Vector4fc, is Quaternionf -> value.toFloat()
                else -> throw RuntimeException("Unknown type ${type.defaultValue}")
            }
            val asDouble = when (val clamped = clampFunc(input)) {
                is Boolean -> clamped.toDouble()
                is Float -> clamped.toDouble()
                is Double -> clamped
                is Int -> clamped.toDouble()
                is Long -> clamped.toDouble()
                else -> throw RuntimeException("Unknown type $clamped for ${javaClass.simpleName}")
            }
            setValue(asDouble, notify)
        }
    }

    fun getValue(value: Any): Double {
        return when (value) {
            is Boolean -> value.toDouble()
            is Float -> value.toDouble()
            is Double -> value
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            is Vector2fc, is Vector3fc, is Vector4fc,
            is Quaternionf -> value[indexInProperty].toDouble()
            else -> throw RuntimeException("Unknown type $value for ${value.javaClass.simpleName}")
        }
    }

    fun setChangeListener(listener: (value: Double) -> Unit): NumberInput {
        changeListener = listener
        return this
    }

    override fun onEmpty(x: Float, y: Float) {
        val newValue = getValue(owningProperty?.defaultValue ?: type.defaultValue)
        if (newValue != lastValue) {
            setValue(newValue, true)
        }
    }

    fun setValue(v: Double, notify: Boolean) {
        if (v != lastValue || !hasValue) {
            hasValue = true
            lastValue = v
            if (notify) changeListener(v)
            inputPanel.text = stringify(v)
            inputPanel.updateChars(false)
        }
    }

    fun updateValueMaybe() {
        if (inputPanel.isInFocus) {
            wasInFocus = true
        } else if (wasInFocus) {
            // apply the value, or reset if invalid
            val value = parseValue(inputPanel.text) ?: lastValue
            setValue(value, true)
            wasInFocus = false
        }
    }

}