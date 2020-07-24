package me.anno.video.formats

import me.anno.gpu.GFX
import me.anno.gpu.texture.Texture2D
import me.anno.video.Frame
import me.anno.video.LastFrame
import java.io.EOFException
import java.io.InputStream

class RGBFrame(w: Int, h: Int): Frame(w,h){

    val rgb = Texture2D(w, h, 1)

    override fun load(input: InputStream){
        val s0 = w*h
        val data = ByteArray(s0 * 4)
        var j = 0
        for(i in 0 until s0){
            val r0 = input.read()
            if(r0 < 0) throw if(j == 0) LastFrame() else EOFException()
            data[j++] = r0.toByte()
            data[j++] = input.read().toByte()
            data[j++] = input.read().toByte()
            data[j++] = 255.toByte()
        }
        GFX.addGPUTask {
            rgb.create(data)
            isLoaded = true
            15
        }
    }

    override fun get3DShader() = GFX.shader3D

    override fun bind(offset: Int, nearestFiltering: Boolean){
        rgb.bind(offset, nearestFiltering)
    }

    override fun destroy(){
        rgb.destroy()
    }

}