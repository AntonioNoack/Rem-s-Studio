package me.anno.io.json

import me.anno.utils.types.Strings
import java.io.OutputStream

class JsonWriter(val output: OutputStream) {

    private var first = true
    private var isKeyValue = false

    private fun writeString(value: String){
        output.write('"'.toInt())
        val sb = StringBuilder()
        Strings.writeEscaped(value, sb)
        output.write(sb.toString().toByteArray())
        output.write('"'.toInt())
    }

    fun keyValue(key: String) {
        next()
        writeString(key)
        isKeyValue = true
    }

    private fun next(){
        if(!first){
            output.write(if(isKeyValue) ':'.toInt() else ','.toInt())
        }
        isKeyValue = false
        first = false
    }

    fun write(b: Boolean){
        next()
        if(b){
            output.write("true".toByteArray())
        } else {
            output.write("false".toByteArray())
        }
    }

    fun write(i: Int){
        next()
        output.write(i.toString().toByteArray())
    }

    fun write(l: Long){
        next()
        output.write(l.toString().toByteArray())
    }

    fun write(f: Float){
        next()
        output.write(f.toString().toByteArray())
    }

    fun write(d: Double){
        next()
        output.write(d.toString().toByteArray())
    }

    fun write(value: String){
        next()
        writeString(value)
    }

    fun open(array: Boolean) {
        next()
        output.write(if (array) '['.toInt() else '{'.toInt())
        first = true
    }

    fun close(array: Boolean) {
        output.write(if (array) ']'.toInt() else '}'.toInt())
        first = false
    }

}