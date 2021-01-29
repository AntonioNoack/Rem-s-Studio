package me.anno.studio.rems

import me.anno.language.translation.NameDesc
import me.anno.objects.Audio
import me.anno.studio.rems.RemsStudio.motionBlurSteps
import me.anno.studio.rems.RemsStudio.shutterPercentage
import me.anno.studio.rems.RemsStudio.targetOutputFile
import me.anno.ui.base.menu.Menu.ask
import me.anno.ui.base.menu.Menu.msg
import me.anno.utils.types.Strings.getImportType
import me.anno.video.VideoAudioCreator
import me.anno.video.VideoCreator
import org.apache.logging.log4j.LogManager
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object Rendering {

    private val LOGGER = LogManager.getLogger(Rendering::class)

    fun renderPart(size: Int, ask: Boolean) {
        render(RemsStudio.targetWidth / size, RemsStudio.targetHeight / size, ask)
    }

    fun renderSetPercent(ask: Boolean) {
        render(
            max(2, (RemsStudio.project!!.targetWidth * RemsStudio.project!!.targetSizePercentage / 100).roundToInt()),
            max(2, (RemsStudio.project!!.targetHeight * RemsStudio.project!!.targetSizePercentage / 100).roundToInt()),
            ask
        )
    }

    var isRendering = false
    fun render(width: Int, height: Int, ask: Boolean) {
        if (width % 2 != 0 || height % 2 != 0) return render(
            width / 2 * 2,
            height / 2 * 2,
            ask
        )
        if (isRendering) {
            msg(
                NameDesc(
                    "Rendering already in progress!",
                    "If you think, this is an error, please restart!",
                    "ui.warn.renderingInProgress"
                )
            )
            return
        }
        isRendering = true
        LOGGER.info("Rendering video at $width x $height")
        var targetOutputFile = targetOutputFile
        do {
            val file0 = targetOutputFile
            if (targetOutputFile.exists() && targetOutputFile.isDirectory) {
                targetOutputFile = File(targetOutputFile, "output.mp4")
            }
            if (!targetOutputFile.name.contains('.')) {
                targetOutputFile = File(targetOutputFile, ".mp4")
            }
        } while (file0 !== targetOutputFile)
        if (targetOutputFile.extension.getImportType() != "Video") {
            LOGGER.warn("The file extension .${targetOutputFile.extension} is unknown! Your export may fail!")
        }

        if(targetOutputFile.exists() && ask){
            isRendering = false
            ask(NameDesc("Override %1?").with("%1", targetOutputFile)){
                render(width, height, false)
            }
            return
        }

        val tmpFile = File(targetOutputFile.parentFile, targetOutputFile.nameWithoutExtension + ".tmp." + targetOutputFile.extension)
        val fps = RemsStudio.targetFPS
        val totalFrameCount = max(1, (fps * RemsStudio.targetDuration).toInt() + 1)
        val sampleRate = 48000
        val audioSources = RemsStudio.root.listOfAll
            .filterIsInstance<Audio>()
            .filter { it.forcedMeta?.hasAudio == true }.toList()
        val creator = VideoAudioCreator(
            VideoCreator(
                width, height,
                RemsStudio.targetFPS, totalFrameCount,
                if (audioSources.isEmpty()) targetOutputFile else tmpFile
            ), sampleRate, audioSources, targetOutputFile,
            motionBlurSteps, shutterPercentage
        )
        creator.onFinished = { isRendering = false }
        creator.start()

    }

}