package me.anno.audio

import me.anno.objects.Audio
import me.anno.objects.Camera
import me.anno.objects.modes.LoopingState
import me.anno.objects.cache.Cache
import me.anno.utils.f2
import me.anno.utils.minus
import me.anno.utils.mix
import me.anno.video.FFMPEGMetadata
import me.anno.video.FFMPEGMetadata.Companion.getMeta
import me.anno.video.FFMPEGStream
import org.joml.Vector3f
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// only play once, then destroy; it makes things easier
// (on user input and when finally rendering only)

/**
 * todo audio effects:
 * todo echoing
 * todo velocity frequency change
 * todo pitch
 * todo losing high frequencies in the distance
 * todo audio becoming quiet in the distance
 * todo -> we need some kind of fourier transform, but how such that it doesn't change the tone???
 * */
abstract class AudioStream(
    val file: File, val repeat: LoopingState,
    val startTime: Double, val meta: FFMPEGMetadata,
    val sender: Audio,
    val listener: Camera,
    val playbackSampleRate: Int = 48000){

    constructor(audio: Audio, speed: Double, globalTime: Double, playbackSampleRate: Int, listener: Camera):
            this(audio.file, audio.isLooping, 0.0, getMeta(audio.file, false)!!, audio, listener, playbackSampleRate){
        configure(audio, speed, globalTime)
    }

    fun configure(audio: Audio, speed: Double, globalTime: Double){
        globalToLocalTime = { time -> audio.getGlobalTransform(time * speed + globalTime).second }
        val amplitude = audio.amplitude
        localAmplitude = { time -> amplitude[time] }
    }

    val minPerceptibleAmplitude = 1f/32500f

    val ffmpegSampleRate = meta.audioSampleRate

    val maxSampleIndex = meta.audioSampleCount

    val ffmpegSliceSampleDuration = 10.0 // seconds, 10s of music
    val ffmpegSliceSampleCount get() = (ffmpegSampleRate * ffmpegSliceSampleDuration).toInt()

    // should be as short as possible for fast calculation
    // should be at least as long as the ffmpeg response time (0.3s for the start of a FHD video)
    companion object {
        val playbackSliceDuration = 1.0
    }

    // map the real time to the correct time xD
    // to do allow skipping and such -> no, too much cleanup ;)

    // var time = 0f

    var isWaitingForBuffer = AtomicBoolean(false)

    var isPlaying = false

    var globalToLocalTime = { globalTime: Double -> globalTime }
    var localAmplitude = { localtime: Double -> 1f }

    val buffers = ArrayList<SoundBuffer>()

    data class AudioSliceKey(val file: File, val slice: Long)

    fun getAmplitudesSync(index: Double): Pair<Float, Float> {
        if(index < 0f) return 0f to 0f
        // multiply by local time dependent amplitude
        val localAmplitude = localAmplitude(index / ffmpegSampleRate)
        if(localAmplitude < minPerceptibleAmplitude) return 0f to 0f
        val i0 = index.toLong()
        val data0 = getMaxAmplitudesSync(i0)
        val data1 = if(index.toInt().toDouble() == index){ // <3, data doesn't need to be interpolated
            return data0.first * localAmplitude to data0.second * localAmplitude
        } else getMaxAmplitudesSync(i0+1)
        val f = (index - i0).toFloat() // sollte ok sein; hohe Präzession ist hier nicht notwendig
        return mix(data0.first, data1.first, f) * localAmplitude to
                mix(data0.second, data1.second, f) * localAmplitude
    }

    fun getMaxAmplitudesSync(index0: Long): Pair<Short, Short> {
        if(index0 < 0 || (repeat == LoopingState.PLAY_ONCE && index0 >= maxSampleIndex)) return 0.toShort() to 0.toShort()
        val index = repeat[index0, maxSampleIndex]
        // val index = if(repeat) index % maxSampleIndex else index
        val sliceIndex = index / ffmpegSliceSampleCount
        val localIndex = (index % ffmpegSliceSampleCount).toInt()
        val arrayIndex0 = localIndex * 2 // for stereo
        val sliceTime = sliceIndex * ffmpegSliceSampleDuration
        val soundBuffer = Cache.getEntry(AudioSliceKey(file, sliceIndex), (ffmpegSliceSampleDuration * 2 * 1000).toLong(), false){
            val sequence = FFMPEGStream.getAudioSequence(file, sliceTime, ffmpegSliceSampleDuration, ffmpegSampleRate)
            var buffer: SoundBuffer?
            while(true){
                buffer = sequence.soundBuffer
                if(buffer != null) break
                // somebody else needs to work on the queue
                Thread.sleep(0, 100_000) // wait 0.1ms
            }
            buffer!!
        } as SoundBuffer
        val data = soundBuffer.pcm!!
        return data[arrayIndex0] to data[arrayIndex0+1]
    }

    val copyTransfer = object: AudioTransfer(1.0, 1.0, 0.0, 0.0){
        override fun l2l(f: Double, s: AudioTransfer) = 1.0
        override fun r2r(f: Double, s: AudioTransfer) = 1.0
        override fun l2r(f: Double, s: AudioTransfer) = 0.0
        override fun r2l(f: Double, s: AudioTransfer) = 0.0
        override fun getLeft(left: Double, right: Double, f: Double, s: AudioTransfer) = left
        override fun getRight(left: Double, right: Double, f: Double, s: AudioTransfer) = right
    }

    open class AudioTransfer(val l2l: Double, val r2r: Double, val l2r: Double, val rl2: Double){
        open fun l2l(f: Double, s: AudioTransfer) = mix(l2l, s.l2l, f)
        open fun r2r(f: Double, s: AudioTransfer) = mix(r2r, s.r2r, f)
        open fun l2r(f: Double, s: AudioTransfer) = mix(l2r, s.l2r, f)
        open fun r2l(f: Double, s: AudioTransfer) = mix(rl2, s.rl2, f)
        open fun getLeft(left: Double, right: Double, f: Double, s: AudioTransfer) =
            left * l2l(f, s) + right * r2l(f, s)
        open fun getRight(left: Double, right: Double, f: Double, s: AudioTransfer) =
            left * l2r(f, s) + right * r2r(f, s)
        override fun toString() = "[${l2l.f2()} ${r2r.f2()} ${l2r.f2()} ${rl2.f2()}]"
    }

    fun calculateLoudness(global1: Double): AudioTransfer {

        // todo decide on loudness depending on speaker orientation and size (e.g. Nierencharcteristik)
        // todo mix left and right channel depending on orientation and speaker size
        // todo top/bottom (tested from behind) sounds different: because I could hear it
        // todo timing differences seam to matter, so we need to include them (aww)

        val (camLocal2Global, _) = listener.getGlobalTransform(global1)
        val (srcLocal2Global, _) = sender.getGlobalTransform(global1)
        val camGlobalPos = camLocal2Global.transformPosition(Vector3f())
        val srcGlobalPos = srcLocal2Global.transformPosition(Vector3f())
        val dirGlobal = (camGlobalPos - srcGlobalPos).normalize() // in global space
        val leftDirGlobal = camLocal2Global.transformDirection(Vector3f(+1f,0f,-0.1f)).normalize()
        val rightDirGlobal = camLocal2Global.transformDirection(Vector3f(-1f,0f,-0.1f)).normalize()
        // val distance = camGlobalPos.distance(srcGlobalPos)

        val left1 = leftDirGlobal.dot(dirGlobal) * 0.48 + 0.52
        val right1 = rightDirGlobal.dot(dirGlobal) * 0.48 + 0.52
        return AudioTransfer(left1, right1, 0.0, 0.0)

    }

    fun requestNextBuffer(startTime: Double, bufferIndex: Long){

        // println("requesting audio buffer $startTime")

        isWaitingForBuffer.set(true)
        thread {// load all data async

            // println("[INFO:AudioStream] Working on buffer $queued")

            // todo speed up for 1:1 playback
            // todo cache sound buffer for 1:1 playback
            // (superfluous calculations)

            // time += dt
            val sampleCount = (playbackSampleRate * playbackSliceDuration).toInt()

            // todo get higher/lower quality, if it's sped up/slowed down?
            // rare use-case...
            // slow motion may be a use case, for which it's worth to request 96kHz or more
            // sound recorded at 0.01x speed is really rare, and at the edge (10Hz -> 10.000Hz)
            // slower frequencies can't be that easily recorded (besides the song/noise of wind (alias air pressure zones changing))

            val dtx = playbackSliceDuration / sampleCount
            val ffmpegSampleRate = ffmpegSampleRate
            val globalToLocalTime = globalToLocalTime
            val localAmplitude = localAmplitude

            val global0 = startTime
            val local0 = globalToLocalTime(global0)
            var index0 = ffmpegSampleRate * local0

            val byteBuffer = ByteBuffer.allocateDirect(sampleCount * 2 * 2)
                .order(ByteOrder.nativeOrder())
            val stereoBuffer = byteBuffer.asShortBuffer()

            var transfer0 = if(sender.is3D) calculateLoudness(startTime) else copyTransfer
            var transfer1 = transfer0

            val updatePositionEveryNFrames = 100

            for(sampleIndex in 0 until sampleCount){

                if(sampleIndex % updatePositionEveryNFrames == 0){

                    // load loudness from camera

                    if(sender.is3D) {

                        transfer0 = transfer1
                        val global1 = startTime + (sampleIndex + updatePositionEveryNFrames) * dtx
                        transfer1 = calculateLoudness(global1)

                    }

                }

                val global1 = startTime + (sampleIndex + 1) * dtx
                val local1 = globalToLocalTime(global1)

                val index1 = ffmpegSampleRate * local1

                // average values from index0 to index1
                val mni = min(index0, index1)
                val mxi = max(index0, index1)

                val mnI = mni.toLong()
                val mxI = mxi.toLong()

                var a0: Double
                var a1: Double

                when {
                    mni == mxi -> {
                        val data = getAmplitudesSync(mni)
                        a0 = data.first.toDouble()
                        a1 = data.second.toDouble()
                    }
                    mnI == mxI -> {
                        // from the same index, so 50:50
                        val data0 = getAmplitudesSync(mni)
                        val data1 = getAmplitudesSync(mxi)
                        a0 = 0.5 * (data0.first + data1.first)
                        a1 = 0.5 * (data0.second + data1.second)
                    }
                    else -> {
                        // sampling from all values
                        // (slow motion sound effects)
                        val data0 = getAmplitudesSync(mni)
                        val data1 = getAmplitudesSync(mxi)
                        val f0 = 1f - (mni - mnI) // x.2f -> 0.8f
                        val f1 = mxi - mxI // x.2f -> 0.2f
                        var b0 = data0.first * f0 + data1.first * f1
                        var b1 = data0.second * f0 + data1.second * f1
                        for(index in mnI+1 until mxI){
                            val data = getMaxAmplitudesSync(index)
                            val time = index.toDouble() / ffmpegSampleRate
                            val amplitude = localAmplitude(time)
                            b0 += amplitude * data.first
                            b1 += amplitude * data.second
                        }
                        val dt = mxi - mni
                        // average the values over the time span
                        a0 = b0 / dt
                        a1 = b1 / dt
                    }
                }

                val approxFraction = (sampleIndex % updatePositionEveryNFrames) * 1.0 / updatePositionEveryNFrames

                // low quality echo
                // because high quality may be expensive...
                // it rather should be computed as a post-processing effect...
                // todo irregular chaos component?? (different reflection directions)
                val echoMultiplier = sender.echoMultiplier[global0]
                if(echoMultiplier > 0f){
                    val echoDelay = sender.echoDelay[global0]
                    var sum = 1f
                    if(abs(echoDelay) > 1e-5f){
                        var global = global1 - echoDelay
                        var multiplier = echoMultiplier
                        for(i in 0 until 10){
                            val local = globalToLocalTime(global)
                            val index = (ffmpegSampleRate * local).toLong()
                            val data = getMaxAmplitudesSync(index)
                            val avg = data.first // echo mixes it anyways ;)
                            val delta = avg * multiplier
                            a0 += delta
                            a1 += delta
                            sum += multiplier
                            global -= echoDelay
                            multiplier *= echoMultiplier
                            if(multiplier < 1e-5f) break
                        }
                    }
                    // normalize the values
                    a0 /= sum
                    a1 /= sum
                }

                // write the data
                stereoBuffer.put(transfer0.getLeft(a0, a1, approxFraction, transfer1).toShort())
                stereoBuffer.put(transfer0.getRight(a0, a1, approxFraction, transfer1).toShort())

                // global0 = global1
                // local0 = local1
                index0 = index1

            }

            stereoBuffer.position(0)

            // 1:1 playback
            /*val soundBuffer = Cache.getEntry(AudioSliceKey(file, (time/dt).roundToInt()), 1000){
                val sequence = FFMPEGStream.getAudioSequence(file, time, dt, sampleRate)
                var buffer: SoundBuffer?
                while(true){
                    buffer = sequence.soundBuffer
                    if(buffer != null) break
                    // somebody else needs to work on the queue
                    Thread.sleep(10)
                }
                buffer!!
            } as SoundBuffer*/

            onBufferFilled(stereoBuffer, bufferIndex)

        }

    }

    abstract fun onBufferFilled(stereoBuffer: ShortBuffer, bufferIndex: Long)

}