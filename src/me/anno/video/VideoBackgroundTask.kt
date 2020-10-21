package me.anno.video

import me.anno.gpu.GFX
import me.anno.gpu.framebuffer.Frame
import me.anno.gpu.framebuffer.Framebuffer
import me.anno.gpu.shader.ShaderPlus
import me.anno.objects.Camera
import me.anno.studio.RemsStudio.nullCamera
import me.anno.studio.RemsStudio.root
import me.anno.studio.Scene
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.min

class VideoBackgroundTask(val video: VideoCreator) {

    val cameras = root.listOfAll.filter { it is Camera }.toList() as List<Camera>

    val camera = cameras.firstOrNull() ?: nullCamera

    val framebuffer =
        Framebuffer("VideoBackgroundTask", video.w, video.h, 1, 1, false, Framebuffer.DepthBufferType.TEXTURE)

    val renderingIndex = AtomicInteger(0)
    val savingIndex = AtomicInteger(0)

    val totalFrameCount = video.totalFrameCount

    var isDone = false

    fun start() {

        if (renderingIndex.get() < totalFrameCount) addNextTask()
        else video.close()

    }

    var isDoneRenderingAndSaving = false

    fun addNextTask() {

        if (!isDoneRenderingAndSaving) {// not done yet

            /**
             * runs on GPU thread
             * */
            val ri = renderingIndex.get()
            if (ri < totalFrameCount && ri < savingIndex.get() + 2) {
                GFX.addGPUTask(1000, 1000) {

                    val frameIndex = renderingIndex.get()
                    if (renderFrame(frameIndex / video.fps)) {
                        renderingIndex.incrementAndGet()
                        video.writeFrame(framebuffer, frameIndex) {
                            // it was saved -> everything works well :)
                            val si = savingIndex.incrementAndGet()
                            if(si == totalFrameCount){
                                isDoneRenderingAndSaving = true
                            } else if(si > totalFrameCount) throw RuntimeException("too many saves: $si, $totalFrameCount")
                        }
                        addNextTask()
                    } else {
                        // waiting
                        thread {
                            Thread.sleep(1)
                            addNextTask()
                        }
                    }

                }
            } else {
                // ("waiting for frame (writing is slow)")
                // waiting for saving to ffmpeg
                thread {
                    Thread.sleep(100)
                    addNextTask()
                }
            }

        } else {
            video.close()
            isDone = true
        }

    }

    fun renderFrame(time: Double): Boolean {

        GFX.check()

        GFX.isFinalRendering = true

        var needsMoreSources = false
        Frame(0, 0, video.w, video.h, false, framebuffer) {
            try {
                Scene.draw(
                    camera, 0, 0, video.w, video.h,
                    time, true,
                    ShaderPlus.DrawMode.COLOR,
                    null
                )
                if (!GFX.isFinalRendering) throw RuntimeException()
            } catch (e: MissingFrameException) {
                needsMoreSources = true
            }
        }

        GFX.isFinalRendering = false

        if (needsMoreSources) return false

        GFX.check()

        return true

    }

}