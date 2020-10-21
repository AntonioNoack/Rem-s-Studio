package me.anno.utils

import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.lang.RuntimeException
import java.nio.FloatBuffer

fun Any.anyToFloat(): Float {
    return when(this){
        is Int -> this.toFloat()
        is Long -> this.toFloat()
        is Float -> this
        is Double -> this.toFloat()
        else -> throw RuntimeException()
    }
}

fun FloatBuffer.put(v: Vector2f){
    put(v.x)
    put(v.y)
}

fun FloatBuffer.put(v: Vector3f){
    put(v.x)
    put(v.y)
    put(v.z)
}

fun FloatBuffer.put(v: Vector4f){
    put(v.x)
    put(v.y)
    put(v.z)
    put(v.w)
}