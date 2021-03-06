package me.anno.video.formats

import me.anno.gpu.GFX
import me.anno.gpu.ShaderLib.shader3DARGB
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.GPUFiltering
import me.anno.gpu.texture.Texture2D
import me.anno.utils.input.readNBytes2
import me.anno.video.VFrame
import java.io.InputStream

class ARGBFrame(w: Int, h: Int) : VFrame(w, h, 0) {

    private val argb = Texture2D("argb-frame", w, h, 1)

    override val isCreated: Boolean get() = argb.isCreated

    override fun load(input: InputStream) {
        val s0 = w * h * 4
        val data = input.readNBytes2(s0, Texture2D.byteBufferPool[s0, false], true)
        creationLimiter.acquire()
        GFX.addGPUTask(w, h) {
            // the data actually still is argb and shuffling is needed
            // to convert it into rgba (needs to be done in the shader (or by a small preprocessing step of the data))
            argb.createRGBA(data)
            creationLimiter.release()
        }
    }

    override fun get3DShader() = shader3DARGB
    override fun getTextures(): List<Texture2D> = listOf(argb)

    override fun bind(offset: Int, nearestFiltering: GPUFiltering, clamping: Clamping) {
        argb.bind(offset, nearestFiltering, clamping)
    }

    override fun destroy() {
        super.destroy()
        argb.destroy()
    }

}