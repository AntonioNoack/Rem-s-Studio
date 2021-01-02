package me.anno.audio.effects

import me.anno.io.Saveable
import me.anno.objects.Audio
import me.anno.objects.Camera
import me.anno.objects.inspectable.Inspectable

abstract class SoundEffect(val inputDomain: Domain, val outputDomain: Domain) : Saveable(),
    Inspectable {

    open fun reset() {
        bufferIndex = 0
    }

    var bufferIndex = 0

    lateinit var audio: Audio

    abstract fun apply(
        data: FloatArray,
        source: Audio,
        destination: Camera,
        time0: Time, time1: Time
    ): FloatArray

    abstract val displayName: String
    abstract val description: String

    abstract fun clone(): SoundEffect

    override fun getApproxSize() = 10
    override fun isDefaultValue() = false

}