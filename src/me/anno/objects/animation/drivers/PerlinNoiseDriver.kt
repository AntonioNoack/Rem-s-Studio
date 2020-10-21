package me.anno.objects.animation.drivers

import me.anno.io.ISaveable
import me.anno.io.base.BaseWriter
import me.anno.objects.Transform
import me.anno.objects.animation.AnimatedProperty
import me.anno.objects.animation.Type
import me.anno.ui.base.Panel
import me.anno.ui.editor.SettingCategory
import me.anno.ui.style.Style
import me.anno.utils.clamp
import org.kdotjpg.OpenSimplexNoise
import kotlin.math.min

class PerlinNoiseDriver: AnimationDriver(){

    var falloff = AnimatedProperty.float01(0.5f)
    var octaves = 5

    var seed = 0L

    private var noiseInstance = OpenSimplexNoise(seed)
    fun getNoise(): OpenSimplexNoise {
        if(noiseInstance.seed != seed) noiseInstance = OpenSimplexNoise(seed)
        return noiseInstance
    }

    override fun getValue0(time: Double): Double {
        val falloff = falloff[time]
        val octaves = clamp(octaves, 0, 16)
        return getValue(time, getNoise(), falloff.toDouble(), octaves) / getMaxValue(falloff, min(octaves, 10))
    }

    // recursion isn't the best... but whatever...
    fun getMaxValue(falloff: Float, octaves: Int): Float = if(octaves >= 0) 1f else 1f + falloff * getMaxValue(falloff,octaves-1)

    fun getValue(time: Double, noise: OpenSimplexNoise, falloff: Double, step: Int): Double {
        var value0 = noise.eval(time, step.toDouble())
        if(step > 0) value0 += falloff * getValue(2.0 * time, noise, falloff, step-1)
        return value0
    }

    override fun getClassName() = "PerlinNoiseDriver"
    override fun getDisplayName() = "Noise"

    override fun createInspector(list: MutableList<Panel>, transform: Transform, style: Style, getGroup: (title: String, id: String) -> SettingCategory) {
        super.createInspector(list, transform, style, getGroup)
        list += transform.VI("Octaves", "Levels of Detail", Type.INT_PLUS, octaves, style){ octaves = it }
        list += transform.VI("Seed", "", Type.LONG, seed, style){ seed = it }
        list += transform.VI("Falloff", "Changes high-frequency weight", falloff, style)
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeLong("seed", seed, true)
        writer.writeInt("octaves", octaves, true)
        writer.writeObject(this,"falloff", falloff)
    }

    override fun readInt(name: String, value: Int) {
        when(name){
            "octaves" -> octaves = clamp(value, 0, MAX_OCTAVES)
            else -> super.readInt(name, value)
        }
    }

    override fun readLong(name: String, value: Long) {
        when(name){
            "seed" -> seed = value
            else -> super.readLong(name, value)
        }
    }

    override fun readObject(name: String, value: ISaveable?) {
        when(name){
            "falloff" -> falloff.copyFrom(value)
            else -> super.readObject(name, value)
        }
    }

    companion object {
        val MAX_OCTAVES = 32
    }

}