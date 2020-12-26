package me.anno.io.binary

import me.anno.io.ISaveable
import me.anno.io.base.BaseWriter
import me.anno.io.binary.BinaryTypes.*
import me.anno.utils.Booleans.toInt
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4d
import org.joml.Vector4f
import java.io.DataOutputStream

class BinaryWriter(val output: DataOutputStream) : BaseWriter(true) {

    /**
     * max number of strings? idk...
     * typically we need only a few, but what if we need many?
     * */
    private val knownStrings = HashMap<String, Int>()

    private val knownNameTypes = HashMap<String, HashMap<NameType, Int>>()

    private var currentClass = ""
    private var currentNameTypes = knownNameTypes.getOrPut(currentClass) { HashMap() }

    private fun writeEfficientString(string: String?) {
        if (string == null) {
            output.writeInt(-1)
        } else {
            val known = knownStrings.getOrDefault(string, -1)
            if (known >= 0) {
                output.writeInt(known)
            } else {
                val bytes = string.toByteArray()
                output.writeInt(-2 - bytes.size)
                output.write(bytes)
                knownStrings[string] = knownStrings.size
            }
        }
    }

    private fun writeTypeString(value: String) {
        writeEfficientString(value)
    }

    private fun writeAttributeStart(name: String, type: Char) {
        val nameType = NameType(name, type)
        val id = currentNameTypes.getOrDefault(nameType, -1)
        if (id >= 0) {
            // known -> short cut
            output.writeInt(id)
        } else {
            // not previously known -> create a new one
            output.writeInt(-1)
            val newId = currentNameTypes.size
            currentNameTypes[nameType] = newId
            writeTypeString(name)
            output.writeByte(type.toInt())
        }
    }

    override fun writeBool(name: String, value: Boolean, force: Boolean) {
        if (force || value) {
            writeAttributeStart(name, BOOL)
            output.writeByte(value.toInt())
        }
    }

    override fun writeByte(name: String, value: Byte, force: Boolean) {
        if (force || value != 0.toByte()) {
            writeAttributeStart(name, BYTE)
            output.writeByte(value.toInt())
        }
    }

    override fun writeShort(name: String, value: Short, force: Boolean) {
        if (force || value != 0.toShort()) {
            writeAttributeStart(name, 's')
            output.writeShort(value.toInt())
        }
    }

    override fun writeInt(name: String, value: Int, force: Boolean) {
        if (force || value != 0) {
            writeAttributeStart(name, INT)
            output.writeInt(value)
        }
    }

    override fun writeIntArray(name: String, value: IntArray, force: Boolean) {
        if (force || value.isNotEmpty()) {
            writeAttributeStart(name, INT_ARRAY)
            output.writeInt(value.size)
            for (v in value) output.writeInt(v)
        }
    }

    override fun writeFloat(name: String, value: Float, force: Boolean) {
        if (force || value != 0f) {
            writeAttributeStart(name, FLOAT)
            output.writeFloat(value)
        }
    }

    override fun writeFloatArray(name: String, value: FloatArray, force: Boolean) {
        if (force || value.isNotEmpty()) {
            writeAttributeStart(name, FLOAT_ARRAY)
            output.writeInt(value.size)
            for (v in value) output.writeFloat(v)
        }
    }

    override fun writeDouble(name: String, value: Double, force: Boolean) {
        if (force || value != 0.0) {
            writeAttributeStart(name, DOUBLE)
            output.writeDouble(value)
        }
    }

    override fun writeDoubleArray(name: String, value: DoubleArray, force: Boolean) {
        if (force || value.isNotEmpty()) {
            writeAttributeStart(name, DOUBLE_ARRAY)
            output.writeInt(value.size)
            for (v in value) output.writeDouble(v)
        }
    }

    override fun writeString(name: String, value: String?, force: Boolean) {
        if (force || value != null) {
            writeAttributeStart(name, STRING)
            writeEfficientString(value)
        }
    }

    override fun writeLong(name: String, value: Long, force: Boolean) {
        if (force || value != 0L) {
            writeAttributeStart(name, LONG)
            output.writeLong(value)
        }
    }

    override fun writeLongArray(name: String, value: LongArray, force: Boolean) {
        if (force || value.isNotEmpty()) {
            writeAttributeStart(name, LONG_ARRAY)
            output.writeInt(value.size)
            for (v in value) output.writeLong(v)
        }
    }

    override fun writeVector2f(name: String, value: Vector2f, force: Boolean) {
        if (force || (value.x != 0f && value.y != 0f)) {
            writeAttributeStart(name, VECTOR2F)
            output.writeFloat(value.x)
            output.writeFloat(value.y)
        }
    }

    override fun writeVector3f(name: String, value: Vector3f, force: Boolean) {
        if (force || (value.x != 0f || value.y != 0f || value.z != 0f)) {
            writeAttributeStart(name, VECTOR3F)
            output.writeFloat(value.x)
            output.writeFloat(value.y)
            output.writeFloat(value.z)
        }
    }

    override fun writeVector4f(name: String, value: Vector4f, force: Boolean) {
        if (force || (value.x != 0f || value.y != 0f || value.z != 0f || value.w != 0f)) {
            writeAttributeStart(name, VECTOR4F)
            output.writeFloat(value.x)
            output.writeFloat(value.y)
            output.writeFloat(value.z)
            output.writeFloat(value.w)
        }
    }

    override fun writeVector4d(name: String, value: Vector4d, force: Boolean) {
        if (force || (value.x != 0.0 || value.y != 0.0 || value.z != 0.0 || value.w != 0.0)) {
            writeAttributeStart(name, VECTOR4D)
            output.writeDouble(value.x)
            output.writeDouble(value.y)
            output.writeDouble(value.z)
            output.writeDouble(value.w)
        }
    }

    override fun writeNull(name: String?) {
        if (name != null) {
            writeAttributeStart(name, OBJECT)
        }
        output.writeInt(-1)
    }

    override fun writePointer(name: String?, className: String, ptr: Int) {
        if (name != null) {
            writeAttributeStart(name, OBJECT)
        }
        output.writeInt(ptr)
        writeEfficientString(className)
    }

    override fun writeObjectImpl(name: String?, value: ISaveable) {
        if (name != null) {
            writeAttributeStart(name, OBJECT)
        }
        currentClass = value.getClassName()
        currentNameTypes = knownNameTypes.getOrPut(currentClass) { HashMap() }
        writeTypeString(currentClass)
        value.save(this)
        output.writeInt(-2) // end
    }

    private fun <V> writeGenericList(
        name: String,
        elements: List<V>?,
        force: Boolean,
        type: Char,
        writeInstance: (V) -> Unit
    ) {
        if (force || elements != null) {

            if (elements == null) {
                writeNull(name)
                return
            }

            writeAttributeStart(name, type)
            output.writeInt(elements.size)
            elements.forEach { element ->
                writeInstance(element)
            }

        }
    }

    override fun <V : ISaveable> writeList(self: ISaveable?, name: String, elements: List<V>?, force: Boolean) {
        writeGenericList(name, elements, force, OBJECT_ARRAY) {
            writeObject(self, name, it, true)
        }
    }

    override fun writeListV2f(name: String, elements: List<Vector2f>?, force: Boolean) {
        writeGenericList(name, elements, force, VECTOR2F_ARRAY) {
            output.writeFloat(it.x)
            output.writeFloat(it.y)
        }
    }

    override fun writeListV3f(name: String, elements: List<Vector3f>?, force: Boolean) {
        writeGenericList(name, elements, force, VECTOR3F_ARRAY) {
            output.writeFloat(it.x)
            output.writeFloat(it.y)
            output.writeFloat(it.z)
        }
    }

    override fun writeListV4f(name: String, elements: List<Vector4f>?, force: Boolean) {
        writeGenericList(name, elements, force, VECTOR4F_ARRAY) {
            output.writeFloat(it.x)
            output.writeFloat(it.y)
            output.writeFloat(it.z)
            output.writeFloat(it.w)
        }
    }

    override fun writeListStart() {
        writeAttributeStart("", OBJECT_LIST_UNKNOWN_LENGTH)
    }

    override fun writeListEnd() {
        output.write(0)
    }

    override fun writeListSeparator() {
        output.write(1)
    }
}