package me.anno.utils.structures

import me.anno.io.ISaveable
import me.anno.io.base.BaseWriter
import me.anno.objects.animation.TimeValue.Companion.writeValue

/**
 * class for values, which have not the typical default value
 * these values don't need to be saved in text form,
 * because they can be set automatically
 * */
class ValueWithDefaultFunc<V>(var value: V, var default: () -> V) {
    constructor(value: V) : this(value, { value })
    constructor(value: () -> V): this(value(), value)

    val isSet get() = value != default()
    fun write(writer: BaseWriter, self: ISaveable?, name: String) {
        if (isSet) {
            writer.writeValue(self, name, value)
        }
    }

    fun set(v: V){
        value = v
    }

}