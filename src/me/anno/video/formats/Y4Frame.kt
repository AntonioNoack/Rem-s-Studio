package me.anno.video.formats

import me.anno.gpu.GFX
import me.anno.gpu.texture.Texture2D
import java.io.EOFException
import java.io.InputStream

class Y4Frame(w: Int, h: Int) : RGBFrame(w, h) {

    override fun load(input: InputStream) {
        val s0 = w * h
        val data = Texture2D.byteBufferPool[s0 * 4, false]
        data.position(0)
        for (i in 0 until s0) {
            val y = input.read()
            if (y < 0) throw EOFException()
            data.put(y.toByte())
            data.put(y.toByte())
            data.put(y.toByte())
            data.put(-1) // offset is required
        }
        data.position(0)
        creationLimiter.acquire()
        GFX.addGPUTask(w, h) {
            rgb.createRGBA(data)
            creationLimiter.release()
        }
    }

}