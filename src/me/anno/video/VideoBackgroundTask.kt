package me.anno.video

import me.anno.gpu.GFX
import me.anno.gpu.framebuffer.Framebuffer
import me.anno.gpu.shader.ShaderPlus
import me.anno.objects.Camera
import me.anno.studio.Scene
import me.anno.studio.Studio.nullCamera
import me.anno.studio.Studio.root
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.min

class VideoBackgroundTask(val video: VideoCreator){

    val cameras = root.listOfAll.filter { it is Camera }.toList() as List<Camera>

    val camera = cameras.firstOrNull() ?: nullCamera

    // todo show the progress somehow
    // (percent, time used, expected time remaining)

    val framebuffer = Framebuffer(video.w, video.h, 1, 1, false, Framebuffer.DepthBufferType.TEXTURE)

    val renderingIndex = AtomicInteger(0)
    val savingIndex = AtomicInteger(0)

    val totalFrameCount = video.totalFrameCount

    var isDone = false

    fun start(){

        if(renderingIndex.get() < totalFrameCount) addNextTask()
        else video.close()

    }

    fun addNextTask(){

        if(min(renderingIndex.get(), savingIndex.get()) < totalFrameCount){// not done yet

            /**
             * runs on GPU thread
             * */
            if(renderingIndex.get() < savingIndex.get()+2){
                GFX.addGPUTask {

                    val frameIndex = renderingIndex.get()
                    if(renderFrame(frameIndex / video.fps)){
                        video.writeFrame(framebuffer, frameIndex){
                            // it was saved -> everything works well :)
                            savingIndex.incrementAndGet()
                        }
                        renderingIndex.incrementAndGet()
                        addNextTask()
                    } else {
                        // println("waiting for frame (data missing)")
                        // waiting
                        thread {
                            Thread.sleep(1)
                            addNextTask()
                        }
                    }

                    5 // 5 tokens for this frame ;)
                }
            } else {
                // println("waiting for frame (writing is slow)")
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

        try {
            Scene.draw(framebuffer, camera, 0, 0, video.w, video.h, time, flipY = true, drawMode = ShaderPlus.DrawMode.COLOR)
        } catch (e: MissingFrameException){
            GFX.isFinalRendering = false
            return false
        }

        GFX.isFinalRendering = false

        GFX.check()

        return true

    }

}