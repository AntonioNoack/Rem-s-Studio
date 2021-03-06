package me.anno.utils.types

import org.joml.Matrix4d
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector2fc

object Matrices {

    fun Matrix4f.skew(v: Vector2fc){
        mul3x3(// works
            1f, v.y(), 0f,
            v.x(), 1f, 0f,
            0f, 0f, 1f
        )
    }

    fun Matrix4f.skew(x: Float, y: Float){
        mul3x3(// works
            1f, y, 0f,
            x, 1f, 0f,
            0f, 0f, 1f
        )
    }

    fun Matrix4d.skew(x: Double, y: Double){
        mul3x3(// works
            1.0, y, 0.0,
            x, 1.0, 0.0,
            0.0, 0.0, 1.0
        )
    }

}