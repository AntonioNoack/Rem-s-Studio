package me.anno.objects

import me.anno.fonts.FontManager
import me.anno.gpu.GFX
import me.anno.io.base.BaseWriter
import me.anno.objects.animation.AnimatedProperty
import me.anno.objects.cache.Cache
import me.anno.objects.cache.VideoData
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.input.FileInput
import me.anno.ui.input.FloatInput
import me.anno.ui.style.Style
import org.joml.Matrix4fStack
import org.joml.Vector4f
import java.io.File
import kotlin.concurrent.thread

// todo button and calculation to match video/image/... to screen size

// todo hovering needs to be used to predict when the user steps forward in time
class Video(var file: File, parent: Transform?): GFXTransform(parent){

    private var lastFile: Any? = null

    // todo add audio component...

    var startTime = 0f
    var endTime = 100f

    // val fps get() = videoCache.fps
    var fps = -1f

    // val duration get() = videoCache.duration

    override fun onDraw(stack: Matrix4fStack, time: Float, color: Vector4f) {

        if(lastFile != file){
            lastFile = file
            fps = -1f
            if(file.exists()){
                // request the metadata :)
                thread {
                    val file = file
                    loop@ while(this.file == file){
                        val frames = Cache.getVideoFrames(file, 0)
                        if(frames != null){
                            fps = frames.fps
                            if(fps > 0f) break@loop
                        } else Thread.sleep(1)
                    }
                }
            }
        }

        var wasDrawn = false

        if(file.exists()){

            if(fps > 0f){
                if(time in startTime .. endTime){

                    // todo draw the current or last texture
                    val frameIndex = ((time-startTime)*fps).toInt()

                    val frame = Cache.getVideoFrame(file, frameIndex)
                    if(frame != null){
                        GFX.draw3D(stack, frame, color, isBillboard.getValueAt(time))
                        wasDrawn = true
                    }

                    // stack.scale(0.1f)
                    // GFX.draw3D(stack, FontManager.getString("Verdana",15f, "$frameIndex")!!, Vector4f(1f,1f,1f,1f), 0f)

                }
            }


        }

        if(!wasDrawn){
            GFX.draw3D(stack, GFX.flat01, GFX.colorShowTexture, 16, 9, Vector4f(0.5f, 0.5f, 0.5f, 1f).mul(color), isBillboard.getValueAt(time))
        }

    }

    override fun createInspector(list: PanelListY, style: Style) {
        super.createInspector(list, style)
        val fileInput = FileInput("Path", style)
            .setText(file.toString())
            .setChangeListener { text -> file = File(text) }
        list += FloatInput(style, "Start Time", startTime, AnimatedProperty.Type.FLOAT)
            .setChangeListener { startTime = it }
        list += FloatInput(style, "End Time", endTime, AnimatedProperty.Type.FLOAT)
            .setChangeListener { endTime = it }
        list += fileInput
    }

    override fun getClassName(): String = "Video"

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeString("path", file.toString())
        writer.writeFloat("startTime", startTime)
        writer.writeFloat("endTime", endTime)
    }

    override fun readString(name: String, value: String) {
        when(name){
            "path" -> file = File(value)
            else -> super.readString(name, value)
        }
    }

    override fun readFloat(name: String, value: Float) {
        when(name){
            "startTime" -> startTime = value
            "endTime" -> endTime = value
            else -> super.readFloat(name, value)
        }
    }

}