package me.anno.objects.animation

import me.anno.gpu.GFX.glThread
import me.anno.io.ISaveable
import me.anno.io.Saveable
import me.anno.io.base.BaseWriter
import me.anno.objects.animation.Interpolation.Companion.getWeights
import me.anno.objects.animation.TimeValue.Companion.writeValue
import me.anno.objects.animation.drivers.AnimationDriver
import me.anno.studio.rems.RemsStudio.root
import me.anno.utils.AnyToDouble.getDouble
import me.anno.utils.Maths.clamp
import me.anno.utils.Vectors.plus
import me.anno.utils.Vectors.times
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

class AnimatedProperty<V>(var type: Type, var defaultValue: V) : Saveable() {

    constructor(type: Type) : this(type, type.defaultValue as V)

    companion object {

        private val LOGGER = LogManager.getLogger(AnimatedProperty::class)

        fun any() = AnimatedProperty<Any>(Type.ANY)
        fun int() = AnimatedProperty<Int>(Type.INT)
        fun int(defaultValue: Int) = AnimatedProperty(Type.INT, defaultValue)
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
        fun vec2(defaultValue: Vector2f) = AnimatedProperty(Type.VEC2, defaultValue)
        fun vec3() = AnimatedProperty<Vector3f>(Type.VEC3)
        fun dir3() = vec3(Vector3f(0f, 1f, 0f))
        fun vec3(defaultValue: Vector3f) = AnimatedProperty(Type.VEC3, defaultValue)
        fun vec4() = AnimatedProperty<Vector4f>(Type.VEC4)
        fun vec4(defaultValue: Vector4f) = AnimatedProperty(Type.VEC4, defaultValue)
        fun pos() = AnimatedProperty<Vector3f>(Type.POSITION)
        fun pos2D() = AnimatedProperty<Vector2f>(Type.POSITION_2D)
        fun rotYXZ() = AnimatedProperty<Vector3f>(Type.ROT_YXZ)
        fun rotXZ() = AnimatedProperty<Vector2f>(Type.ROT_XZ)
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
    var interpolation = Interpolation.SPLINE

    fun ensureCorrectType(v: Any?): V {
        return type.accepts(v!!) as V ?: throw RuntimeException("got $v for $type")
    }

    fun clampAny(value: Any) = clamp(value as V)
    fun clamp(value: V): V {
        return type.clampFunc?.invoke(value) as V ?: value
    }

    fun set(value: V): AnimatedProperty<V> {
        checkThread()
        keyframes.clear()
        keyframes.add(Keyframe(0.0, clamp(value)))
        keyframes.sort()
        return this
    }

    fun addKeyframe(time: Double, value: Any) =
        addKeyframe(time, value, 0.001)

    fun addKeyframe(time: Double, value: Any, equalityDt: Double) {
        val value2 = type.accepts(value)
        if (value2 != null) {
            addKeyframeInternal(time, clamp(value2 as V), equalityDt)
        } else LOGGER.warn("Value $value is not accepted by type $type!")
    }

    fun checkIsAnimated() {
        isAnimated = keyframes.size >= 2 || drivers.any { it != null }
    }

    private fun addKeyframeInternal(time: Double, value: V, equalityDt: Double) {
        checkThread()
        ensureCorrectType(value)
        if (isAnimated) {
            keyframes.forEachIndexed { index, it ->
                if (abs(it.time - time) < equalityDt) {
                    keyframes[index] = Keyframe(time, value)
                    return
                }
            }
        } else {
            keyframes.clear()
        }
        keyframes.add(Keyframe(time, value))
        sort()
    }

    fun checkThread() {
        if (glThread != null && Thread.currentThread() != glThread &&
            !root.listOfAll.none {
                when (this) {
                    it.color, it.position, it.rotationYXZ,
                    it.colorMultiplier, it.skew -> true
                    else -> false
                }
            }
        ) {
            throw RuntimeException()
        }
    }

    /**
     * true, if found
     * */
    fun remove(keyframe: Keyframe<*>): Boolean {
        checkThread()
        return keyframes.remove(keyframe)
    }

    operator fun get(time: Double) = getValueAt(time)
    fun <N : Number> getIntegral(time: Double, allowNegativeValues: Boolean): Double {
        val minValue = if (allowNegativeValues) Double.NEGATIVE_INFINITY else 0.0
        val size = keyframes.size
        return when {
            size == 0 -> max(minValue, (defaultValue as N).toDouble()) * time
            size == 1 || !isAnimated -> max(minValue, (keyframes[0].value as N).toDouble()) * time
            else -> {
                val startTime: Double
                val endTime: Double
                if (time <= 0) {
                    startTime = time
                    endTime = 0.0
                } else {
                    startTime = 0.0
                    endTime = time
                }
                var sum = 0.0
                var lastTime = startTime
                var lastValue = max(minValue, (this[startTime] as N).toDouble())
                for (kf in keyframes) {
                    if (kf.time > time) break // we are done
                    if (kf.time > lastTime) {// a new value
                        val value = max(minValue, (kf.value as N).toDouble())
                        sum += (lastValue + value) * (kf.time - lastTime) * 0.5
                        lastValue = value
                        lastTime = kf.time
                    }
                }
                val endValue = max(minValue, (this[endTime] as N).toDouble())
                sum += (lastValue + endValue) * (time - lastTime) * 0.5
                sum
            }
        }
    }

    operator fun get(t0: Double, t1: Double): List<Keyframe<V>> {
        val i0 = max(0, getIndexBefore(t0))
        val i1 = min(getIndexBefore(t1) + 1, keyframes.size)
        return if (i1 > i0) keyframes.subList(i0, i1).filter { it.time in t0..t1 }
        else emptyList()
    }

    fun getAnimatedValue(time: Double): V {
        val size = keyframes.size
        return when {
            size == 0 -> defaultValue
            size == 1 || !isAnimated -> keyframes[0].value
            else -> {

                val index = clamp(getIndexBefore(time), 0, keyframes.size - 2)
                val frame0 = keyframes.getOrElse(index - 1) { keyframes[0] }
                val frame1 = keyframes[index]
                val frame2 = keyframes[index + 1]
                val frame3 = keyframes.getOrElse(index + 2) { keyframes.last() }
                if (frame1 == frame2) return frame1.value

                val t1 = frame1.time
                val t2 = frame2.time

                val f = (time - t1) / (t2 - t1)
                val w = getWeights(frame0, frame1, frame2, frame3, f)

                val value = mulAdd(
                    mulAdd(
                        mulAdd(
                            mul(toCalc(frame0.value), w.x),
                            toCalc(frame1.value), w.y
                        ), toCalc(frame2.value), w.z
                    ), toCalc(frame3.value), w.w
                )

                return clamp(fromCalc(value))

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
            val v = animatedValue ?: defaultValue ?: 0.0
            val v0 = getDouble(v, 0)
            val v1 by lazy { getDouble(v, 1) }
            val v2 by lazy { getDouble(v, 2) }
            val v3 by lazy { getDouble(v, 3) }
            // todo functions for vectors???...
            when (animatedValue) {
                is Int -> drivers[0]?.getValue(time, v0)?.toInt() ?: animatedValue
                is Long -> drivers[0]?.getValue(time, v0)?.toLong() ?: animatedValue
                is Float -> drivers[0]?.getValue(time, v0)?.toFloat() ?: animatedValue
                is Double -> drivers[0]?.getValue(time, v0) ?: animatedValue
                is Vector2f -> Vector2f(
                    drivers[0]?.getValue(time, v0)?.toFloat() ?: animatedValue.x,
                    drivers[1]?.getValue(time, v1)?.toFloat() ?: animatedValue.y
                )
                is Vector3f -> Vector3f(
                    drivers[0]?.getValue(time, v0)?.toFloat() ?: animatedValue.x,
                    drivers[1]?.getValue(time, v1)?.toFloat() ?: animatedValue.y,
                    drivers[2]?.getValue(time, v2)?.toFloat() ?: animatedValue.z
                )
                is Vector4f -> Vector4f(
                    drivers[0]?.getValue(time, v0)?.toFloat() ?: animatedValue.x,
                    drivers[1]?.getValue(time, v1)?.toFloat() ?: animatedValue.y,
                    drivers[2]?.getValue(time, v2)?.toFloat() ?: animatedValue.z,
                    drivers[3]?.getValue(time, v3)?.toFloat() ?: animatedValue.w
                )
                is Quaternionf -> Quaternionf(
                    drivers[0]?.getValue(time, v0)?.toFloat() ?: animatedValue.x,
                    drivers[1]?.getValue(time, v1)?.toFloat() ?: animatedValue.y,
                    drivers[2]?.getValue(time, v2)?.toFloat() ?: animatedValue.z,
                    drivers[3]?.getValue(time, v3)?.toFloat() ?: animatedValue.w
                )
                else -> throw RuntimeException("Replacing components with drivers in $animatedValue is not yet supported!")
            }
        } as V
    }

    fun mix(a: Float, b: Float, f: Float, g: Float) = a * g + b * f

    /**
     * a * (1-f) + f * b
     * */
    fun mix(a: V, b: V, f: Double): V {
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

    private fun toCalc(a: V): Any {
        return when (a) {
            is Int -> a.toDouble()
            is Float -> a.toDouble()
            is Double -> a
            is Long -> a.toDouble()
            is Vector2f, is Vector3f, is Vector4f, is Quaternionf -> a
            else -> throw RuntimeException("don't know how to calc $a")
        } as Any // needed by Intellij Kotlin compiler
    }

    private fun fromCalc(a: Any): V = clampAny(a)

    /**
     * b + a * f
     * */
    private fun mulAdd(b: Any, a: Any, f: Double): Any {
        return when (b) {
            is Double -> b + (a as Double) * f
            is Vector2f -> b + ((a as Vector2f) * f.toFloat())
            is Vector3f -> b + ((a as Vector3f) * f.toFloat())
            is Vector4f -> b + ((a as Vector4f) * f.toFloat())
            else -> throw RuntimeException("don't know how to mul-add $a and $b")
        }
    }

    /**
     * a * f
     * */
    fun mul(a: Any, f: Double): Any {
        return when (a) {
            is Double -> a * f
            is Vector2f -> a * f.toFloat()
            is Vector3f -> a * f.toFloat()
            is Vector4f -> a * f.toFloat()
            else -> throw RuntimeException("don't know how to mul $a")
        }
    }

    private fun getIndexBefore(time: Double): Int {
        // get the index of the time
        val rawIndex = keyframes.binarySearch { it.time.compareTo(time) }
        return (if (rawIndex < 0) -rawIndex - 1 else rawIndex) - 1
    }

    override fun getClassName(): String = "AnimatedProperty"
    override fun getApproxSize(): Int = 10

    override fun save(writer: BaseWriter) {
        super.save(writer)
        sort()
        // must be written before keyframes!!
        if (isAnimated) {
            writer.writeBoolean("isAnimated", isAnimated)
            writer.writeObjectList(this, "keyframes", keyframes)
        } else {
            // isAnimated = false is default
            val value0 = keyframes.firstOrNull()?.value
            if (value0 != null && value0 != defaultValue) {
                writer.writeValue(this, "v", value0)
            }
        }
        writer.writeInt("interpolation", interpolation.code)
        for (i in 0 until type.components) {
            writer.writeObject(this, "driver$i", drivers[i])
        }
    }

    fun sort() {
        keyframes.sort()
    }

    override fun readSomething(name: String, value: Any?) {
        when (name) {
            "keyframe0", "v" -> addKeyframe(0.0, value ?: return)
            else -> super.readSomething(name, value)
        }
    }

    override fun readInt(name: String, value: Int) {
        when (name) {
            "interpolation" -> interpolation = Interpolation.getType(value)
            else -> super.readInt(name, value)
        }
    }

    override fun readBoolean(name: String, value: Boolean) {
        when (name) {
            "isAnimated" -> isAnimated = value
            else -> super.readBoolean(name, value)
        }
    }

    override fun readObject(name: String, value: ISaveable?) {
        when (name) {
            "keyframes" -> {
                if (value is Keyframe<*>) {
                    val castValue = type.accepts(value.value!!)
                    if (castValue != null) {
                        addKeyframe(value.time, clamp(castValue as V) as Any, 0.0) // do clamp?
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
        if (index >= drivers.size) {
            LOGGER.warn("Driver$index out of bounds for ${type.components}/${drivers.size}/$type")
            return
        }
        if (value is AnimationDriver) {
            drivers[index] = value
        } else WrongClassType.warn("driver", value)
    }

    fun copyFrom(obj: Any?, force: Boolean = false) {
        if (obj === this && !force) throw RuntimeException("Probably a typo!")
        if (obj is AnimatedProperty<*>) {
            isAnimated = obj.isAnimated
            interpolation = obj.interpolation
            keyframes.clear()
            obj.keyframes.forEach {
                val castValue = type.accepts(it.value!!)
                if (castValue != null) {
                    keyframes.add(Keyframe(it.time, clamp(castValue as V)))
                } else LOGGER.warn("${it.value} is not accepted by $type")
                // else convert the type??...
            }
            for (i in 0 until type.components) {
                this.drivers[i] = obj.drivers.getOrNull(i)
            }
            interpolation = obj.interpolation
        } else println("copy-from-object $obj is not an AnimatedProperty!")
    }

    override fun isDefaultValue() =
        !isAnimated && (keyframes.isEmpty() || keyframes[0].value == defaultValue) && drivers.all { it == null }

}