package me.anno.objects

import me.anno.gpu.GFX
import me.anno.gpu.TextureLib.whiteTexture
import me.anno.gpu.buffer.Attribute
import me.anno.gpu.buffer.StaticFloatBuffer
import me.anno.gpu.texture.FilteringMode
import me.anno.io.base.BaseWriter
import me.anno.objects.cache.Cache
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.style.Style
import me.anno.utils.plus
import me.anno.utils.times
import me.anno.video.MissingFrameException
import org.joml.Matrix4fArrayList
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL11
import java.io.File

class Cubemap(var file: File = File(""), parent: Transform? = null): GFXTransform(parent){

    var filtering = FilteringMode.LINEAR
    var otherFormat = false

    // create a cubemap on the gpu instead to support best-ram-usage mipmapping and linear filtering?
    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {

        val texture = Cache.getImage(file, 1000, true) ?:
            if(GFX.isFinalRendering) throw MissingFrameException(file)
            else whiteTexture

        val sphericalProjection = file.name.endsWith(".hdr", true) != otherFormat

        if(sphericalProjection){
            GFX.drawSpherical(stack, buffer, texture, color, filtering, GL11.GL_QUADS)
        } else {
            GFX.drawXYZUV(stack, buffer, texture, color, filtering, GL11.GL_QUADS)
        }

    }

    override fun createInspector(list: PanelListY, style: Style) {
        super.createInspector(list, style)
        list += VI("Texture", "File location of the texture to use", null, file, style){ file = it }
        list += VI("Other Format", "If it looks wrong ;)", null, otherFormat, style){ otherFormat = it }
        list += VI("Filtering", "", null, filtering, style){ filtering = it }
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeFile("file", file)
        writer.writeBool("otherFormat", otherFormat)
        writer.writeInt("filtering", filtering.id)
    }

    override fun readBool(name: String, value: Boolean) {
        when(name){
            "otherFormat" -> otherFormat = value
            else -> super.readBool(name, value)
        }
    }

    override fun readInt(name: String, value: Int) {
        when(name){
            "filtering" -> filtering = filtering.find(value)
            else -> super.readInt(name, value)
        }
    }

    override fun readString(name: String, value: String) {
        when(name){
            "file" -> file = File(name)
            else -> super.readString(name, value)
        }
    }

    companion object {

        val buffer = StaticFloatBuffer(listOf(Attribute("attr0", 3), Attribute("attr1", 2)), 4 * 6)
        init {

            fun put(v0: Vector3f, dx: Vector3f, dy: Vector3f, x: Float, y: Float, u: Int, v: Int){
                val pos = v0 + dx*x + dy*y
                buffer.put(pos.x, pos.y, pos.z, u/4f, v/3f)
            }

            fun addFace(u: Int, v: Int, v0: Vector3f, dx: Vector3f, dy: Vector3f){
                put(v0, dx, dy, -1f, -1f, u+1, v)
                put(v0, dx, dy, -1f, +1f, u+1, v+1)
                put(v0, dx, dy, +1f, +1f, u, v+1)
                put(v0, dx, dy, +1f, -1f, u, v)
            }

            val mxAxis = Vector3f(-1f,0f,0f)
            val myAxis = Vector3f(0f,-1f,0f)
            val mzAxis = Vector3f(0f,0f,-1f)

            addFace(1, 1, mzAxis, mxAxis, yAxis) // center, front
            addFace(0, 1, mxAxis, zAxis, yAxis) // left, left
            addFace(2, 1, xAxis, mzAxis, yAxis) // right, right
            addFace(3, 1, zAxis, xAxis, yAxis) // 2x right, back
            addFace(1, 0, myAxis, mxAxis, mzAxis) // top
            addFace(1, 2, yAxis, mxAxis, zAxis) // bottom

        }

    }

    override fun getClassName() = "Cubemap"

}