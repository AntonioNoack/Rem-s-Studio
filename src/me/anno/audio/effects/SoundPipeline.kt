package me.anno.audio.effects

import me.anno.audio.effects.falloff.ExponentialFalloff
import me.anno.audio.effects.falloff.LinearFalloff
import me.anno.audio.effects.falloff.SquareFalloff
import me.anno.audio.effects.impl.AmplitudeEffect
import me.anno.audio.effects.impl.EchoEffect
import me.anno.audio.effects.impl.EqualizerEffect
import me.anno.audio.effects.impl.PitchEffect
import me.anno.io.ISaveable
import me.anno.io.Saveable
import me.anno.io.base.BaseWriter
import me.anno.objects.Audio
import me.anno.objects.Camera
import me.anno.objects.inspectable.Inspectable
import me.anno.studio.rems.RemsStudio
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.editor.stacked.Option
import me.anno.ui.editor.stacked.StackPanel
import me.anno.ui.style.Style
import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

class SoundPipeline() : Saveable(), Inspectable {

    lateinit var audio: Audio
    lateinit var camera: Camera

    constructor(audio: Audio) : this() {
        this.audio = audio
    }

    override fun createInspector(
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        stages.forEach { it.audio = audio }
        val effectsGroup = getGroup("Audio Effects", "Audio Effects", "audio-fx")
        effectsGroup += object : StackPanel(
            "Effects Stack",
            "Effects can be added with RMB, are applied one after another",
            options.map { gen ->
                option { gen().apply { this.audio = this@SoundPipeline.audio } }
            },
            stages,
            style
        ) {

            override fun onAddComponent(component: Inspectable, index: Int) {
                component as SoundEffect
                component.audio = audio
                RemsStudio.largeChange("Add ${component.displayName}") {
                    if (index >= stages.size) {
                        stages.add(component)
                    } else {
                        stages.add(index, component)
                    }
                }
            }

            override fun onRemoveComponent(component: Inspectable) {
                component as SoundEffect
                RemsStudio.largeChange("Remove ${component.displayName}") {
                    stages.remove(component)
                }
            }

            override fun getOptionFromInspectable(inspectable: Inspectable): Option? {
                return if (inspectable is SoundEffect) {
                    option { inspectable }
                } else null
            }

        }
    }

    val stages = ArrayList<SoundEffect>()

    val fft = FloatFFT_1D(bufferSize.toLong())

    val input = FloatArray(bufferSize)

    fun process(
        data0: FloatArray,
        audio: Audio,
        inputDomain: Domain,
        outputDomain: Domain,
        time0: Time, time1: Time
    ): FloatArray {

        val totalSize = data0.size
        if (totalSize < bufferSize) {
            throw IllegalArgumentException("Input is too small!")
        } else if (totalSize > bufferSize) {
            if (totalSize % bufferSize != 0) throw RuntimeException("Size must be a match, modulo")
            val output = FloatArray(totalSize)
            for (offset in data0.indices step bufferSize) {
                System.arraycopy(data0, offset, input, 0, bufferSize)
                val f0 = offset.toDouble() / totalSize
                val f1 = (offset + bufferSize).toDouble() / totalSize
                val t0 = time0.mix(time1, f0)
                val t1 = time0.mix(time1, f1)
                val partial = process(input, audio, inputDomain, outputDomain, t0, t1)
                System.arraycopy(partial, 0, output, offset, bufferSize)
            }
            return output
        }

        var data = data0
        var domain = inputDomain
        for (stage in stages) {
            changeDomain(domain, stage.inputDomain, data)
            data = stage.apply(data, audio, camera, time0, time1)
            domain = stage.outputDomain
        }

        changeDomain(domain, outputDomain, data)
        if(outputDomain == Domain.TIME_DOMAIN){
            fixJumps(data, lastValue, 0, min(512, bufferSize))
            lastValue = data.last()
        }

        return data

    }

    var lastValue = 0f
    fun fixJumps(output: FloatArray, v0: Float, index1: Int, length: Int) {
        val v1 = output[index1]
        val v2 = output[index1 + 1]
        val delta = 2 * v1 - (v0 + v2) // flatten, keep the gradient
        if (abs(delta) > 0.2f * max(abs(v0), abs(v1))) {// high frequency, not ok -> cut it off
            val falloff = 6f
            val lastExponent = exp(-falloff)
            val amplitude = delta / (1f - lastExponent)
            for (i in 0 until length) {
                output[index1 + i] -= amplitude * (exp(-i * falloff / length) - lastExponent)
            }
        }
    }

    private fun changeDomain(src: Domain, dst: Domain, data: FloatArray) {
        if (src != dst) {
            when (dst) {
                Domain.TIME_DOMAIN -> fft.realInverse(data, true)
                Domain.FREQUENCY_DOMAIN -> fft.realForward(data)
            }
        }
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        for (stage in stages) {
            writer.writeObject(this, "stage", stage)
        }
    }

    override fun readObject(name: String, value: ISaveable?) {
        when (name) {
            "stage" -> {
                if (value is SoundEffect) {
                    stages.add(value)
                }
            }
            else -> super.readObject(name, value)
        }
    }

    override fun getClassName() = "SoundPipeline"
    override fun getApproxSize() = 100
    override fun isDefaultValue() = stages.isEmpty()

    fun clone(): SoundPipeline {
        val copy = SoundPipeline(audio)
        copy.stages.addAll(
            stages.map {
                it.clone().apply {
                    audio = this@SoundPipeline.audio
                }
            }
        )
        return copy
    }

    companion object {

        // 1024 (48Hz .. 48kHz) or 2048? (24Hz .. 48kHz)
        const val bufferSize = 1024
        const val bufferSizeM1 = bufferSize - 1

        fun option(generator: () -> SoundEffect): Option {
            val sample = generator()
            return Option(sample.displayName, sample.description, generator)
        }

        /**
         * could be extended/modified by mods or plugins
         * */
        val options = arrayListOf(
            { EchoEffect() },
            { AmplitudeEffect() },
            { EqualizerEffect() },
            { PitchEffect() },
            { SquareFalloff() },
            { LinearFalloff() },
            { ExponentialFalloff() }
        )

    }


}