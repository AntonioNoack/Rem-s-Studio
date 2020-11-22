package me.anno.utils

import me.anno.utils.Color.a
import me.anno.utils.Color.b
import me.anno.utils.Color.g
import me.anno.utils.Color.r
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.nio.ByteBuffer

class FloatArrayList(val capacity: Int){

    private val buffers = ArrayList<FloatArray>()
    var size = 0

    operator fun get(index: Int) = buffers[index / capacity][index % capacity]
    operator fun plusAssign(value: Int) = plusAssign(value.toFloat())
    operator fun plusAssign(value: Float){
        val index = size % capacity
        if(index == 0) buffers.add(FloatArray(capacity))
        buffers.last()[index] = value
        size++
    }

    operator fun plusAssign(v: Vector2f){
        this += v.x
        this += v.y
    }

    operator fun plusAssign(v: Vector3f){
        this += v.x
        this += v.y
        this += v.z
    }

    operator fun plusAssign(v: Vector4f){
        this += v.x
        this += v.y
        this += v.z
        this += v.w
    }

    fun addRGB(c: Int){
        this += c.r()/255f
        this += c.g()/255f
        this += c.b()/255f
    }

    fun addRGBA(c: Int){
        this += c.r()/255f
        this += c.g()/255f
        this += c.b()/255f
        this += c.a()/255f
    }

    fun putInto(dst0: ByteBuffer){
        if(size > 0){
            val pos0 = dst0.position()
            val dst = dst0.asFloatBuffer()
            // dst.position(pos0/4)
            val lastSize = size % capacity
            if(lastSize == 0){
                for(buffer in buffers){
                    dst.put(buffer)
                }
            } else {
                for(i in 0 until buffers.size-1){
                    dst.put(buffers[i])
                }
                val lastBuffer = buffers.last()
                dst.put(lastBuffer, 0, lastSize)
            }
            dst0.position(pos0 + size*4)
            // dst0.position(dst.position()*4)
        }
    }

}