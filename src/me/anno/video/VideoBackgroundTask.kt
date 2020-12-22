package me.anno.video

import me.anno.gpu.GFX
import me.anno.gpu.blending.BlendDepth
import me.anno.gpu.blending.BlendMode
import me.anno.gpu.framebuffer.FBStack
import me.anno.gpu.framebuffer.Frame
import me.anno.gpu.framebuffer.Framebuffer
import me.anno.gpu.shader.ShaderPlus
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.GPUFiltering
import me.anno.objects.Camera
import me.anno.studio.rems.RemsStudio.nullCamera
import me.anno.studio.rems.RemsStudio.root
import me.anno.studio.rems.Scene
import org.lwjgl.opengl.GL11.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class VideoBackgroundTask(val video: VideoCreator, val motionBlurSteps: Int, val shutterPercentage: Float) {

    val cameras = root.listOfAll.filter { it is Camera }.toList() as List<Camera>

    val camera = cameras.firstOrNull() ?: nullCamera ?: Camera()

    val partialFrame =
        Framebuffer("VideoBackgroundTask-partial", video.w, video.h, 1, 1, false, Framebuffer.DepthBufferType.TEXTURE)
    val averageFrame =
        Framebuffer("VideoBackgroundTask-sum", video.w, video.h, 1, 1, true, Framebuffer.DepthBufferType.TEXTURE)

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
                        video.writeFrame(averageFrame, frameIndex) {
                            // it was saved -> everything works well :)
                            val si = savingIndex.incrementAndGet()
                            if (si == totalFrameCount) {
                                isDoneRenderingAndSaving = true
                            } else if (si > totalFrameCount) throw RuntimeException("too many saves: $si, $totalFrameCount")
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

        if (motionBlurSteps < 2 || shutterPercentage <= 1e-3f) {
            Frame(0, 0, video.w, video.h, false, averageFrame) {
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
        } else {
            Frame(averageFrame) {

                Frame.bind()
                glClearColor(0f, 0f, 0f, 0f)
                glClear(GL_COLOR_BUFFER_BIT)

                var i = 0
                while (i++ < motionBlurSteps && !needsMoreSources) {
                    FBStack.clear(video.w, video.h)
                    Frame(partialFrame) {
                        try {
                            Scene.draw(
                                camera, 0, 0, video.w, video.h,
                                time + (i - motionBlurSteps / 2f) * shutterPercentage / (video.fps * motionBlurSteps),
                                true, ShaderPlus.DrawMode.COLOR, null
                            )
                            if (!GFX.isFinalRendering) throw RuntimeException()
                        } catch (e: MissingFrameException) {
                            needsMoreSources = true
                        }
                    }
                    if (!needsMoreSources) {
                        partialFrame.bindTexture0(0, GPUFiltering.TRULY_NEAREST, Clamping.CLAMP)
                        BlendDepth(BlendMode.PURE_ADD, false) {
                            // write with alpha 1/motionBlurSteps
                            GFX.copy(1f / motionBlurSteps)
                        }
                    }
                }

            }
        }


        GFX.isFinalRendering = false

        if (needsMoreSources) return false

        GFX.check()

        return true

    }

}