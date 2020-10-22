package me.anno.video.formats

import me.anno.gpu.GFX
import me.anno.gpu.ShaderLib.shader3DYUV
import me.anno.gpu.texture.ClampMode
import me.anno.gpu.texture.NearestMode
import me.anno.gpu.texture.Texture2D
import me.anno.utils.readNBytes2
import me.anno.video.VFrame
import me.anno.video.LastFrame
import java.io.InputStream
import java.lang.RuntimeException

class I420Frame(iw: Int, ih: Int): VFrame(iw,ih){

    // this is correct, confirmed by example
    private val w2 = (w+1)/2
    private val h2 = (h+1)/2

    private val y = Texture2D(w, h, 1)
    private val u = Texture2D(w2, h2, 1)
    private val v = Texture2D(w2, h2, 1)

    override fun load(input: InputStream){
        val s0 = w * h
        val s1 = w2 * h2
        val yData = input.readNBytes2(s0)
        if(yData.isEmpty()) throw LastFrame()
        if(yData.size < s0) throw RuntimeException("not enough data, only ${yData.size} of $s0")
        GFX.addGPUTask(w, h){
            y.createMonochrome(yData)
        }
        val uData = input.readNBytes2(s1)
        if(uData.size < s1) throw RuntimeException("not enough data, only ${uData.size} of $s1")
        GFX.addGPUTask(w2, h2){
            u.createMonochrome(uData)
        }
        val vData = input.readNBytes2(s1)
        if(vData.size < s1) throw RuntimeException("not enough data, only ${vData.size} of $s1")
        GFX.addGPUTask(w2, h2){
            v.createMonochrome(vData)
            // tasks are executed in order, so this is true
            // (if no exception happened)
            isLoaded = true
        }
    }

    override fun get3DShader() = shader3DYUV

    override fun bind(offset: Int, nearestFiltering: NearestMode, clampMode: ClampMode){
        v.bind(offset+2, nearestFiltering, clampMode)
        u.bind(offset+1, nearestFiltering, clampMode)
        y.bind(offset, nearestFiltering, clampMode)
    }

    // 319x yuv = 2,400 MB
    // 7.5 MB / yuv
    // 7.5 MB / 1.5 =
    // 5 MB / full channel
    // = 2.4x what is really needed...
    // 305x RGBA uv = 7,000 MB
    // 23 MB / RGBA uv
    // 5.1 MB / full channel
    // -> awkward....
    override fun destroy(){
        y.destroy()
        u.destroy()
        v.destroy()
    }

}