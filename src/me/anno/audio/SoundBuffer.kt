package me.anno.audio

import org.lwjgl.openal.AL10.*
import org.lwjgl.stb.STBVorbis.*
import org.lwjgl.stb.STBVorbisInfo
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer


class SoundBuffer(file: File){

    var buffer = alGenBuffers()
    var pcm: ShortBuffer? = null

    init {
        STBVorbisInfo.malloc().use {  info ->
            Audio.check()
            val pcm = readVorbis(file, info)
            val format = if(info.channels() == 1) AL_FORMAT_MONO16 else AL_FORMAT_STEREO16
            alBufferData(buffer, format, pcm, info.sample_rate())
            Audio.check()
        }
    }

    private fun readVorbis(file: File, info: STBVorbisInfo): ShortBuffer {
        MemoryStack.stackPush().use { stack ->
            val vorbis = ioResourceToByteBuffer(file)
            val error = stack.mallocInt(1)
            val decoder: Long = stb_vorbis_open_memory(vorbis, error, null)
            Audio.check()
            if (decoder == NULL) {
                throw RuntimeException("Failed to open Ogg Vorbis file. Error: " + error[0])
            }
            stb_vorbis_get_info(decoder, info)
            val channels = info.channels()
            val lengthSamples = stb_vorbis_stream_length_in_samples(decoder)
            val pcm = MemoryUtil.memAllocShort(lengthSamples)
            this.pcm = pcm
            pcm.limit(stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm) * channels)
            stb_vorbis_close(decoder)
            return pcm
        }
    }

    fun ioResourceToByteBuffer(file: File): ByteBuffer {
        val bytes = file.readBytes()
        val buffer = ByteBuffer
            .allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
        buffer.position(0)
        buffer.put(bytes)
        buffer.position(0)
        return buffer
    }


    fun destroy(){
        alDeleteBuffers(buffer)
        if(pcm != null){
            MemoryUtil.memFree(pcm)
        }
    }

}