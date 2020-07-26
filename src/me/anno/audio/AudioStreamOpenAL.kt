package me.anno.audio

import me.anno.gpu.GFX
import me.anno.objects.Audio
import me.anno.objects.Camera
import me.anno.objects.LoopingState
import me.anno.video.FFMPEGMetadata
import org.lwjgl.openal.AL10.*
import java.io.File
import java.nio.ShortBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

// only play once, then destroy; it makes things easier
// (on user input and when finally rendering only)

class AudioStreamOpenAL(file: File, repeat: LoopingState, startTime: Double, meta: FFMPEGMetadata, listener: Camera):
    AudioStream(file, repeat, startTime, meta, listener){

    constructor(audio: Audio, speed: Double, globalTime: Double, listener: Camera):
            this(audio.file, audio.isLooping, 0.0, FFMPEGMetadata.getMeta(audio.file, false)!!, listener){
        configure(audio, speed, globalTime)
    }

    var startTimeNanos = 0L
    val alSource = SoundSource(false, true)

    var queued = AtomicLong()
    var processed = 0

    fun checkProcessed(){
        processed = alGetSourcei(alSource.sourcePtr, AL_BUFFERS_PROCESSED)
        ALBase.check()
    }

    fun start(){
        synchronized(this){
            if(!isPlaying){
                isPlaying = true
                startTimeNanos = System.nanoTime()
                waitForRequiredBuffers()
            }
        }
    }

    // not supported ;)
    /*fun unpause(){
        start(pauseTime)
    }

    fun pause(){
        if(!isPlaying) return
        pauseTime = (System.nanoTime() - startTime)*1e-9f
        isPlaying = false
        alSource.pause()
    }*/

    fun stop(){
        if(!isPlaying) return
        isPlaying = false
        alSource.stop()
        alSource.destroy()
        ALBase.check()
        // ALBase.check()
        // somehow crashes..., buffers can't be reused either (without error)
        // buffers.toSet().forEach { it.destroy() }
        // ALBase.check()
    }


    fun waitForRequiredBuffers() {
        if(!isPlaying) return
        val queued = queued.get()
        if(!isWaitingForBuffer.get() && queued > 0) checkProcessed()
        // keep 2 on reserve
        if(queued < processed+5 && !isWaitingForBuffer.get()){
            // request a buffer
            // only one at a time
            val index = this.queued.getAndIncrement()
            // println("loading $index...")
            requestNextBuffer(startTime + playbackSliceDuration * index, index)
        }
        thread {
            Thread.sleep(10)
            GFX.addAudioTask {
                waitForRequiredBuffers()
                ALBase.check()
                1
            }
        }
    }

    override fun onBufferFilled(stereoBuffer: ShortBuffer, bufferIndex: Long) {
        GFX.addAudioTask {
            val isFirstBuffer = bufferIndex == 0L
            ALBase.check()
            val soundBuffer = SoundBuffer()
            ALBase.check()
            if(isFirstBuffer){
                val dt = max(0f, (System.nanoTime() - startTimeNanos) * 1e-9f)
                // println("skipping first $dt")
                // 10s slices -> 2.6s
                // 1s slices -> 0.55s
                val samples = dt * playbackSampleRate
                val currentIndex = samples.toInt() * 2
                // what if index > sampleCount? add empty buffer???...
                val minPlayedSamples = 32 // not correct, but who cares ;) (our users care ssshhh)
                val skipIndex = min(currentIndex, stereoBuffer.capacity() - 2 * minPlayedSamples)
                if(skipIndex > 0){
                    // println("skipping $skipIndex")
                    stereoBuffer.position(skipIndex)
                }
            }
            soundBuffer.loadRawStereo16(stereoBuffer, playbackSampleRate)
            buffers.add(soundBuffer)
            ALBase.check()
            // println("Invalid Name? alSourceQueueBuffers(${alSource.sourcePtr}, ${soundBuffer.buffer})")
            // println("putting buffer ${soundBuffer.pcm?.capacity()}")
            alSourceQueueBuffers(alSource.sourcePtr, soundBuffer.buffer)
            ALBase.check()
            if(isFirstBuffer){
                alSource.play()
                ALBase.check()
            }
            // time += openALSliceDuration
            isWaitingForBuffer.set(false)
            ALBase.check()
            1
        }
    }

}