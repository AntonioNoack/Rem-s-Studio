package me.anno.video

import me.anno.audio.AudioStream
import me.anno.objects.Audio
import me.anno.objects.Camera
import java.nio.ShortBuffer
import java.util.concurrent.atomic.AtomicInteger

class BufferStream(
    val audio: Audio, sampleRate: Int, val buffer: ShortBuffer,
    listener: Camera, private val notifier: AtomicInteger
    ) :
        AudioStream(audio, 1.0, 0.0, sampleRate, listener) {
        override fun onBufferFilled(stereoBuffer: ShortBuffer, bufferIndex: Long) {
            synchronized(buffer){
                buffer.position(0)
                buffer.put(stereoBuffer)
                notifier.incrementAndGet()
            }
        }
    }