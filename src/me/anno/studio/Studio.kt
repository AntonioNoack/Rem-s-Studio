package me.anno.studio

import me.anno.audio.ALBase
import me.anno.audio.AudioManager
import me.anno.gpu.GFX
import me.anno.input.Input.isShiftDown
import me.anno.objects.Camera
import me.anno.objects.Inspectable
import me.anno.objects.Transform
import me.anno.objects.animation.AnimatedProperty
import me.anno.studio.RemsStudio.windowStack
import me.anno.studio.project.Project
import me.anno.ui.dragging.IDraggable
import me.anno.ui.editor.PropertyInspector
import org.apache.logging.log4j.LogManager
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max

object Studio {

    private val LOGGER = LogManager.getLogger(Studio::class)

    var project: Project? = null

    var dragged: IDraggable? = null

    var editorTime = 0.0

    var editorTimeDilation = 0.0
        set(value) {
            val v2 = max(value, 0.0)
            if(v2 != field) updateAudio()
            field = v2
        }

    val isPaused get() = editorTimeDilation == 0.0
    val isPlaying get() = editorTimeDilation != 0.0

    val targetDuration get() = project!!.targetDuration
    val targetFPS get() = project!!.targetFPS
    val targetWidth get() = project!!.targetWidth
    val targetHeight get() = project!!.targetHeight
    val targetOutputFile get() = project!!.targetOutputFile

    val shiftSlowdown get() = if(isShiftDown) 0.2f else 1f

    val nullCamera = Camera(null)

    init {
        nullCamera.name = "Inspector Camera"
        nullCamera.onlyShowTarget = false
        // higher far value to allow other far values to be seen
        nullCamera.farZ.addKeyframe(0.0, 5000f, 1.0)
        nullCamera.timeDilation = 0.0 // the camera has no time, so no motion can be recorded
    }

    var root = Transform()
    // var selectedCamera = nullCamera
    var usedCamera = nullCamera

    var selectedTransform: Transform? = null
    var selectedProperty: AnimatedProperty<*>? = null
    var selectedInspectable: Inspectable? = null

    fun updateInspector(){
        windowStack.forEach { window ->
            window.panel.listOfAll.forEach {
                (it as? PropertyInspector)?.apply {
                    needsUpdate = true
                }
            }
        }
    }

    fun updateAudio(){
        GFX.addAudioTask {
            // update the audio player...
            if(isPlaying){
                AudioManager.requestUpdate()
            } else {
                AudioManager.stop()
            }
            ALBase.check()
            100
        }
    }

    fun addEvent(event: () -> Unit){
        eventTasks += event
    }

    fun warn(msg: String){
        LOGGER.warn(msg)
    }

    val eventTasks = ConcurrentLinkedQueue<() -> Unit>()

}