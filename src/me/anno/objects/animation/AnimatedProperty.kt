package me.anno.objects.animation

import me.anno.io.ISaveable
import me.anno.io.Saveable
import me.anno.io.base.BaseWriter
import me.anno.objects.animation.drivers.AnimationDriver
import me.anno.utils.WrongClassType
import org.apache.logging.log4j.LogManager
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class AnimatedProperty<V>(val type: Type, var defaultValue: V) : Saveable() {

    constructor(type: Type) : this(type, type.defaultValue as V)

    companion object {

        private val LOGGER = LogManager.getLogger(AnimatedProperty::class)

        fun any() = AnimatedProperty<Any>(Type.ANY)
        fun int() = AnimatedProperty<Int>(Type.INT)
        fun intPlus() = AnimatedProperty<Int>(Type.INT_PLUS)
        fun intPlus(defaultValue: Int) = AnimatedProperty(Type.INT_PLUS, defaultValue)
        fun long() = AnimatedProperty<Long>(Type.LONG)
        fun float() = AnimatedProperty<Float>(Type.FLOAT)
        fun float(defaultValue: Float) = AnimatedProperty(Type.FLOAT, defaultValue)
        fun floatPlus() = AnimatedProperty<Float>(Type.FLOAT_PLUS)
        fun floatPlus(defaultValue: Float) = AnimatedProperty(Type.FLOAT_PLUS, defaultValue)
        fun floatPlusExp() = AnimatedProperty<Float>(Type.FLOAT_PLUS_EXP)
        fun floatPlusExp(defaultValue: Float) = AnimatedProperty(Type.FLOAT_PLUS_EXP, defaultValue)
        fun float01() = AnimatedProperty<Float>(Type.FLOAT_01)
        fun float01(defaultValue: Float) = AnimatedProperty(Type.FLOAT_01, defaultValue)
        fun float01exp(defaultValue: Float) = AnimatedProperty(Type.FLOAT_01_EXP, defaultValue)
        fun floatPercent() = AnimatedProperty<Float>(Type.FLOAT_PERCENT)
        fun double() = AnimatedProperty<Double>(Type.DOUBLE)
        fun vec2() = AnimatedProperty<Vector2f>(Type.VEC2)
        fun vec3() = AnimatedProperty<Vector3f>(Type.VEC3)
        fun vec4() = AnimatedProperty<Vector3f>(Type.VEC4)
        fun vec4(defaultValue: Vector4f) = AnimatedProperty(Type.VEC4, defaultValue)
        fun pos() = AnimatedProperty<Vector3f>(Type.POSITION)
        fun rotYXZ() = AnimatedProperty<Vector3f>(Type.ROT_YXZ)
        fun scale() = AnimatedProperty<Vector3f>(Type.SCALE)
        fun scale(defaultValue: Vector3f) = AnimatedProperty(Type.SCALE, defaultValue)
        fun color() = AnimatedProperty<Vector4f>(Type.COLOR)
        fun color(defaultValue: Vector4f) = AnimatedProperty(Type.COLOR, defaultValue)
        fun color3() = AnimatedProperty<Vector3f>(Type.COLOR3)
        fun color3(defaultValue: Vector3f) = AnimatedProperty(Type.COLOR3, defaultValue)
        fun quat() = AnimatedProperty<Quaternionf>(Type.QUATERNION)
        fun skew() = AnimatedProperty<Vector2f>(Type.SKEW_2D)
        fun tiling() = AnimatedProperty<Vector4f>(Type.TILING)

    }

    val drivers = arrayOfNulls<AnimationDriver>(type.components)

    var isAnimated = false
    val keyframes = ArrayList<Keyframe<V>>()
    var interpolation = Interpolation.LINEAR_BOUNDED

    fun ensureCorrectType(v: Any?): V {
        if (!type.accepts(v)) throw RuntimeException("got $v for $type")
        return v as V
    }

    fun clampAny(value: Any) = clamp(value as V)
    fun clamp(value: V): V {
        val minValue = type.minValue as? V
        val maxValue = type.maxValue as? V
        if (minValue != null || maxValue != null) {
            value as Comparable<V>
            if (minValue != null && value < minValue) return minValue
            if (maxValue != null && value >= maxValue) return maxValue
        }
        return value
    }

    fun set(value: V): AnimatedProperty<V> {
        keyframes.clear()
        keyframes.add(Keyframe(0.0, clamp(value)))
        keyframes.sort()
        return this
    }

    fun addKeyframe(time: Double, value: Any) =
        addKeyframe(time, value, 0.001)

    fun addKeyframe(time: Double, value: Any, equalityDt: Double) {
        if (type.accepts(value)) {
            addKeyframeInternal(time, clamp(value as V), equalityDt)
        } else LOGGER.warn("value is not accepted!")
    }

    private fun addKeyframeInternal(time: Double, value: V, equalityDt: Double) {
        ensureCorrectType(value)
        keyframes.forEachIndexed { index, it ->
            if (abs(it.time - time) < equalityDt) {
                keyframes[index] = Keyframe(time, value)
                return
            }
        }
        keyframes.add(Keyframe(time, value))
        sort()
    }

    /**
     * true, if found
     * */
    fun remove(keyframe: Keyframe<*>): Boolean {
        return keyframes.remove(keyframe)
    }

    operator fun get(time: Double) = getValueAt(time)

    operator fun get(t0: Double, t1: Double): List<Keyframe<V>> {
        val i0 = max(0, getIndexBefore(t0))
        val i1 = min(getIndexBefore(t1) + 1, keyframes.size)
        return if (i1 > i0) keyframes.subList(i0, i1).filter { it.time in t0..t1 }
        else emptyList()
    }

    fun getAnimatedValue(time: Double): V {
        return when (keyframes.size) {
            0 -> defaultValue
            1 -> keyframes[0].value
            else -> {
                val index = getIndexBefore(time)
                val frame0 = keyframes.getOrElse(index) { keyframes[0] }
                val frame1 = keyframes.getOrElse(index + 1) { keyframes.last() }
                if (frame0 == frame1) return frame0.value
                val t0 = frame0.time
                val t1 = frame1.time
                when (interpolation) {
                    Interpolation.STEP -> {
                        (if (time < t1) frame0 else frame1).value
                    }
                    Interpolation.LINEAR_UNBOUNDED -> {
                        val relativeTime = (time - t0) / (t1 - t0)
                        lerp(frame0.value, frame1.value, relativeTime)
                    }
                    Interpolation.LINEAR_BOUNDED -> {
                        if (time <= t0) return frame0.value
                        if (time >= t1) return frame1.value
                        val relativeTime = (time - t0) / (t1 - t0)
                        lerp(frame0.value, frame1.value, relativeTime)
                    }
                    else -> throw RuntimeException("todo interpolation $interpolation")
                }
            }
        }
    }

    fun getValueAt(time: Double): V {
        val animatedValue = if (drivers.all { it != null })
            type.defaultValue
        else getAnimatedValue(time)
        return if (drivers.all { it == null }) animatedValue
        else {
            // replace the components, which have drivers, with the driver values
            when (animatedValue) {
                is Int -> drivers[0]?.getValue(time)?.toInt() ?: animatedValue
                is Long -> drivers[0]?.getValue(time)?.toLong() ?: animatedValue
                is Float -> drivers[0]?.getValue(time)?.toFloat() ?: animatedValue
                is Double -> drivers[0]?.getValue(time) ?: animatedValue
                is Vector2f -> Vector2f(
                    drivers[0]?.getValue(time)?.toFloat() ?: animatedValue.x,
                    drivers[1]?.getValue(time)?.toFloat() ?: animatedValue.y
                )
                is Vector3f -> Vector3f(
                    drivers[0]?.getValue(time)?.toFloat() ?: animatedValue.x,
                    drivers[1]?.getValue(time)?.toFloat() ?: animatedValue.y,
                    drivers[2]?.getValue(time)?.toFloat() ?: animatedValue.z
                )
                is Vector4f -> Vector4f(
                    drivers[0]?.getValue(time)?.toFloat() ?: animatedValue.x,
                    drivers[1]?.getValue(time)?.toFloat() ?: animatedValue.y,
                    drivers[2]?.getValue(time)?.toFloat() ?: animatedValue.z,
                    drivers[3]?.getValue(time)?.toFloat() ?: animatedValue.w
                )
                is Quaternionf -> Quaternionf(
                    drivers[0]?.getValue(time)?.toFloat() ?: animatedValue.x,
                    drivers[1]?.getValue(time)?.toFloat() ?: animatedValue.y,
                    drivers[2]?.getValue(time)?.toFloat() ?: animatedValue.z,
                    drivers[3]?.getValue(time)?.toFloat() ?: animatedValue.w
                )
                else -> throw RuntimeException("Replacing components with drivers in $animatedValue is not yet supported!")
            }
        } as V
    }

    fun lerp(a: Float, b: Float, f: Float, g: Float) = a * g + b * f

    fun lerp(a: V, b: V, f: Double): V {
        val g = 1.0 - f
        return when (type) {
            Type.INT,
            Type.INT_PLUS -> ((a as Int) * g + f * (b as Int)).roundToInt()
            Type.LONG -> ((a as Long) * g + f * (b as Long)).toLong()
            Type.FLOAT,
            Type.FLOAT_01, Type.FLOAT_01_EXP,
            Type.FLOAT_PLUS -> ((a as Float) * g + f * (b as Float)).toFloat()
            Type.DOUBLE -> (a as Double) * g + f * (b as Double)
            Type.SKEW_2D -> (a as Vector2f).lerp(b as Vector2f, f.toFloat(), Vector2f())
            Type.POSITION,
            Type.ROT_YXZ,
            Type.SCALE -> (a as Vector3f).lerp(b as Vector3f, f.toFloat(), Vector3f())
            Type.COLOR, Type.TILING -> (a as Vector4f).lerp(b as Vector4f, f.toFloat(), Vector4f())
            Type.QUATERNION -> (a as Quaternionf).slerp(b as Quaternionf, f.toFloat())
            else -> throw RuntimeException("don't know how to lerp $a and $b")
        } as V
    }

    fun getIndexBefore(time: Double): Int {
        // get the index of the time
        val rawIndex = keyframes.binarySearch { it.time.compareTo(time) }
        return (if (rawIndex < 0) -rawIndex - 1 else rawIndex) - 1
    }

    override fun getClassName(): String = "AnimatedProperty"
    override fun getApproxSize(): Int = 10

    override fun save(writer: BaseWriter) {
        super.save(writer)
        sort()
        writer.writeList(this, "keyframes", keyframes)
        writer.writeBool("isAnimated", isAnimated)
        for (i in 0 until type.components) {
            writer.writeObject(this, "driver$i", drivers[i])
        }
    }

    fun sort() {
        keyframes.sort()
    }

    override fun readBool(name: String, value: Boolean) {
        when (name) {
            "isAnimated" -> isAnimated = value
            else -> super.readBool(name, value)
        }
    }

    override fun readObject(name: String, value: ISaveable?) {
        when (name) {
            "keyframes" -> {
                if (value is Keyframe<*>) {
                    if (type.accepts(value.value)) {
                        addKeyframe(value.time, clamp(value.value as V) as Any, 1e-5) // do clamp?
                    } else LOGGER.warn("Dropped keyframe!, incompatible type ${value.value} for $type")
                } else WrongClassType.warn("keyframe", value)
            }
            "driver0" -> setDriver(0, value)
            "driver1" -> setDriver(1, value)
            "driver2" -> setDriver(2, value)
            "driver3" -> setDriver(3, value)
            else -> super.readObject(name, value)
        }
    }

    fun setDriver(index: Int, value: ISaveable?) {
        if (index >= drivers.size){
            LOGGER.warn("Driver$index out of bounds for ${type.components}/${drivers.size}/$type")
            return
        }
        if (value is AnimationDriver) {
            drivers[index] = value
        } else WrongClassType.warn("driver", value)
    }

    // todo this may result in an issue, where we can't copy this object by reference...
    // todo do we want copies by reference anyways? maybe with right click, or key combos..
    fun copyFrom(obj: Any?, force: Boolean = false) {
        if (obj === this && !force) throw RuntimeException("Probably a typo!")
        if (obj is AnimatedProperty<*>) {
            /*if(type.accepts(obj.type.defaultValue)){
                isAnimated = obj.isAnimated
                keyframes.clear()
                obj.keyframes.forEach {
                    it.setValueUnsafe(clamp(it.value as V))
                }
                obj.drivers.forEachIndexed { index, animationDriver ->
                    this.drivers[index] = animationDriver
                }
                keyframes.addAll(obj.keyframes as List<Keyframe<V>>)
                interpolation = obj.interpolation
            } else LOGGER.warn("$type does not accept type ${obj.type} with default value ${obj.type.defaultValue}")*/
            isAnimated = obj.isAnimated
            keyframes.clear()
            obj.keyframes.forEach {
                if (type.accepts(it.value)) {
                    keyframes.add(Keyframe(it.time, clamp(it.value as V)))
                } else LOGGER.warn("${it.value} is not accepted by $type")
                // else convert the type??...
            }
            for (i in 0 until type.components) {
                this.drivers[i] = obj.drivers.getOrNull(i)
            }
            interpolation = obj.interpolation
        } else println("copy-from-object $obj is not an AnimatedProperty!")
    }

    override fun isDefaultValue() = !isAnimated && keyframes.isEmpty() && drivers.all { it == null }

}