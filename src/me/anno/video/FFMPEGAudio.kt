package me.anno.video

import me.anno.audio.SoundBuffer
import me.anno.audio.format.WaveReader
import java.io.File
import kotlin.concurrent.thread

class FFMPEGAudio(file: File?, val sampleRate: Int, val length: Double) :
    FFMPEGStream(file) {

    override fun process(process: Process, arguments: List<String>) {
        // ("starting process for audio $sampleRate x $length")
        // (arguments)
        thread {
            val out = process.errorStream.bufferedReader()
            val parser = FFMPEGMetaParser()
            while (true) {
                val line = out.readLine() ?: break
                parser.parseLine(line, this)
            }
        }
        thread {
            val input = process.inputStream.buffered()
            val frameCount = (sampleRate * length).toInt()
            input.mark(3)
            if (input.read() < 0) { // EOF
                isEmpty = true
                return@thread
            }
            input.reset()
            val wav = WaveReader(input, frameCount)
            val buffer = SoundBuffer()
            buffer.loadRawStereo16(wav.stereoPCM, sampleRate)
            soundBuffer = buffer
            input.close()
        }
    }

    var isEmpty = false
    var soundBuffer: SoundBuffer? = null

    override fun destroy() {}

}