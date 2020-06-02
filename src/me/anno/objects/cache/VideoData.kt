package me.anno.objects.cache

import me.anno.gpu.GFX
import me.anno.video.FFMPEGStream
import java.io.File

class VideoData(file: File, index: Int, val fps: Float): CacheData {

    val time0 = GFX.lastTime

    val stream = FFMPEGStream.getImageSequence(file, index * framesPerContainer, framesPerContainer, fps)
    val frames = stream.frames

    override fun destroy() {
        stream.destroy()
    }

    companion object {
        val framesPerContainer = 16
    }

}