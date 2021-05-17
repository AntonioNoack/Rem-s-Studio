package me.anno.video.formats

import me.anno.gpu.GFX
import me.anno.gpu.ShaderLib.shader3DYUV
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.GPUFiltering
import me.anno.gpu.texture.Texture2D
import me.anno.utils.input.readNBytes2
import me.anno.video.VFrame
import java.io.InputStream

class I420Frame(iw: Int, ih: Int) : VFrame(iw, ih, 2) {

    // this is correct, confirmed by example
    private val w2 = (w + 1) / 2
    private val h2 = (h + 1) / 2

    private val y = Texture2D("i420-y-frame", w, h, 1)
    private val u = Texture2D("i420-u-frame", w2, h2, 1)
    private val v = Texture2D("i420-v-frame", w2, h2, 1)

    override val isCreated: Boolean get() = y.isCreated && u.isCreated && v.isCreated

    override fun load(input: InputStream) {
        val s0 = w * h
        val s1 = w2 * h2
        val yData = input.readNBytes2(s0, Texture2D.byteBufferPool[s0, false])
        creationLimiter.acquire()
        GFX.addGPUTask(w, h) {
            y.createMonochrome(yData)
            creationLimiter.release()
        }
        val uData = input.readNBytes2(s1, Texture2D.byteBufferPool[s0, false])
        creationLimiter.acquire()
        GFX.addGPUTask(w2, h2) {
            u.createMonochrome(uData)
            creationLimiter.release()
        }
        val vData = input.readNBytes2(s1, Texture2D.byteBufferPool[s0, false])
        creationLimiter.acquire()
        GFX.addGPUTask(w2, h2) {
            v.createMonochrome(vData)
            creationLimiter.release()
            // tasks are executed in order, so this is true
            // (if no exception happened)
        }
    }

    override fun get3DShader() = shader3DYUV

    override fun getTextures(): List<Texture2D> = listOf(y, u, v)

    override fun bind(offset: Int, nearestFiltering: GPUFiltering, clamping: Clamping) {
        v.bind(offset + 2, nearestFiltering, clamping)
        u.bind(offset + 1, nearestFiltering, clamping)
        y.bind(offset, nearestFiltering, clamping)
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
    override fun destroy() {
        super.destroy()
        y.destroy()
        u.destroy()
        v.destroy()
    }

}