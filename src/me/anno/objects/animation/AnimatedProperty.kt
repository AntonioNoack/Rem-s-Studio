package me.anno.objects.animation

import me.anno.io.Saveable
import me.anno.io.base.BaseWriter
import org.joml.*
import java.lang.RuntimeException
import kotlin.math.abs

class AnimatedProperty<V>(val type: Type): Saveable(){

    enum class Type(
        val code: String,
        val defaultValue: Any,
        val components: Int,
        val accepts: (Any?) -> Boolean){
        FLOAT("float", 0f, 1, { it is Float }),
        POSITION("pos", Vector3f(), 3, { it is Vector3f }),
        SCALE("scale", Vector3f(), 3, { it is Vector3f }),
        ROT_YXZ("rotYXZ", Vector3f(), 3, { it is Vector3f }),
        SKEW_2D("skew2D", Vector2f(), 2, { it is Vector2f }),
        QUATERNION("quaternion", Quaternionf(), 4, { it is Quaternionf }),
        COLOR("color", Vector4f(1f,1f,1f,1f), 4, { it is Vector4f });
        init { types[code] = this }
    }

    companion object {
        val types = HashMap<String, Type>()
        fun float() = AnimatedProperty<Float>(Type.FLOAT)
        fun pos() = AnimatedProperty<Vector3f>(Type.POSITION)
        fun rotYXZ() = AnimatedProperty<Vector3f>(Type.ROT_YXZ)
        fun scale() = AnimatedProperty<Vector3f>(Type.SCALE)
        fun color() = AnimatedProperty<Vector4f>(Type.COLOR)
        fun quat() = AnimatedProperty<Quaternionf>(Type.QUATERNION)
        fun skew() = AnimatedProperty<Vector2f>(Type.SKEW_2D)
    }

    val keyframes = ArrayList<Keyframe<V>>()
    var interpolation = Interpolation.LINEAR_BOUNDED

    fun ensureCorrectType(v: Any?): V {
        if(!type.accepts(v)) throw RuntimeException("got $v for $type")
        return v as V
    }

    fun set(value: V): AnimatedProperty<V> {
        keyframes.clear()
        return add(0f, value)
    }

    fun add(time: Float, value: V): AnimatedProperty<V> {
        keyframes.add(Keyframe(time, value))
        keyframes.sort()
        return this
    }

    fun addKeyframe(time: Float, value: V, equalityDt: Float){
        ensureCorrectType(value)
        keyframes.forEachIndexed { index, it ->
            if(abs(it.time - time) < equalityDt){
                keyframes[index] = Keyframe(time, value)
                return
            }
        }
        keyframes.add(Keyframe(time, value))
        keyframes.sort()
    }

    operator fun get(time: Float) = getValueAt(time)
    fun getValueAt(time: Float): V {
        return when(keyframes.size){
            0 -> type.defaultValue as V
            1 -> keyframes[0].value
            else -> {
                val index = getIndexBefore(time)
                val frame0 = keyframes.getOrElse(index){ keyframes[0] }
                val frame1 = keyframes.getOrElse(index+1){ keyframes.last() }
                if(frame0 == frame1) return frame0.value
                val t0 = frame0.time
                val t1 = frame1.time
                when(interpolation){
                    Interpolation.STEP -> {
                        (if(time < t1) frame0 else frame1).value
                    }
                    Interpolation.LINEAR_UNBOUNDED -> {
                        val relativeTime = (time-t0)/(t1-t0)
                        lerp(frame0.value, frame1.value, relativeTime)
                    }
                    Interpolation.LINEAR_BOUNDED -> {
                        if(time <= t0) return frame0.value
                        if(time >= t1) return frame1.value
                        val relativeTime = (time-t0)/(t1-t0)
                        lerp(frame0.value, frame1.value, relativeTime)
                    }
                    else -> throw RuntimeException("todo interpolation $interpolation")
                }
            }
        }
    }

    fun lerp(a: Float, b: Float, f: Float, g: Float) = a*g+b*f

    fun lerp(a: V, b: V, f: Float): V {
        val g = 1f-f
        return when(type){
            Type.FLOAT -> (a as Float)*g+f*(b as Float)
            // Type.DOUBLE -> (a as Double)*g+f*(b as Double)
            Type.SKEW_2D -> (a as Vector2f).lerp(b as Vector2f, f, Vector2f())
            Type.POSITION,
            Type.ROT_YXZ,
            Type.SCALE -> (a as Vector3f).lerp(b as Vector3f, f, Vector3f())
            Type.COLOR -> (a as Vector4f).lerp(b as Vector4f, f, Vector4f())
            Type.QUATERNION -> (a as Quaternionf).slerp(b as Quaternionf, f)
            else -> throw RuntimeException("don't know how to lerp $a and $b")
        } as V
    }

    fun getIndexBefore(time: Float): Int {
        // todo does this get the correct time, or do we need to inverse?
        val rawIndex = keyframes.binarySearch { it.time.compareTo(time) }
        return if(rawIndex < 0) -rawIndex-1 else rawIndex
    }

    override fun getClassName(): String = "AnimatedProperty<${type.code}>"
    override fun getApproxSize(): Int = 10

    override fun save(writer: BaseWriter) {
        super.save(writer)
        keyframes.sortBy { it.time }
        writer.writeList(this, "keyframes", keyframes)
    }

    override fun readObject(name: String, value: Saveable?) {
        when(name){
            "keyframes" -> {
                if(value is Keyframe<*> && type.accepts(value.value)){
                    addKeyframe(value.time, value.value as V, 0f)
                } else println("dropped keyframe!, incompatible type $value")
            }
            else -> super.readObject(name, value)
        }
    }

}