package me.anno.video.formats

import me.anno.gpu.GFX
import me.anno.gpu.ShaderLib.shader3DRGBA
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.GPUFiltering
import me.anno.gpu.texture.Texture2D
import me.anno.utils.hpc.HeavyProcessing.processBalanced
import me.anno.utils.input.readNBytes2
import me.anno.video.LastFrame
import me.anno.video.VFrame
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer

class RGBFrame(w: Int, h: Int) : VFrame(w, h, -1) {

    private val rgb = Texture2D("rgb-frame", w, h, 1)

    override fun load(input: InputStream) {
        val s0 = w * h
        val data = ByteBuffer.allocateDirect(s0 * 4)
        val srcData = input.readNBytes2(s0 * 3)
        if (srcData.isEmpty()) throw LastFrame()
        if (srcData.size < s0 * 3) throw EOFException("Missing data ${srcData.size} < ${s0 * 3} for $w x $h")
        processBalanced(0, s0, 512) { i0, i1 ->
            for (i in i0 until i1) {
                val j = i * 4
                val k = i * 3
                data.put(j, srcData[k])
                data.put(j + 1, srcData[k + 1])
                data.put(j + 2, srcData[k + 2])
                data.put(j + 3, -1) // offset is required
            }
        }
        GFX.addGPUTask(w, h) {
            rgb.createRGBA(data)
            isLoaded = true
        }
    }

    override fun get3DShader() = shader3DRGBA

    override fun bind(offset: Int, nearestFiltering: GPUFiltering, clamping: Clamping) {
        rgb.bind(offset, nearestFiltering, clamping)
    }

    override fun destroy() {
        rgb.destroy()
    }

}