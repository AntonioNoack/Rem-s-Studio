package me.anno.objects

import me.anno.gpu.ShaderLib.colorForceFieldBuffer
import me.anno.gpu.ShaderLib.maxColorForceFields
import me.anno.gpu.ShaderLib.uvForceFieldBuffer
import me.anno.gpu.shader.Shader
import me.anno.io.ISaveable
import me.anno.io.base.BaseWriter
import me.anno.objects.animation.AnimatedProperty
import me.anno.objects.attractors.ColorAttractor
import me.anno.objects.attractors.UVAttractor
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.style.Style
import me.anno.utils.put
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL20.*
import kotlin.math.abs
import kotlin.math.sqrt

abstract class GFXTransform(parent: Transform?) : Transform(parent) {

    init {
        timelineSlot = 0
    }

    private val attractorBaseColor = AnimatedProperty.color(Vector4f())

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(this, "attractorBaseColor", attractorBaseColor)
    }

    override fun readObject(name: String, value: ISaveable?) {
        when (name) {
            "attractorBaseColor" -> attractorBaseColor.copyFrom(value)
            else -> super.readObject(name, value)
        }
    }

    override fun createInspector(
        list: PanelListY,
        style: Style,
        getGroup: (title: String, id: String) -> SettingCategory
    ) {
        super.createInspector(list, style, getGroup)
        list += VI("Attractor Base Color", "Base color for manipulation", attractorBaseColor, style)
    }

    open fun transformLocally(pos: Vector3f, time: Double): Vector3f {
        return pos
    }

    fun uploadAttractors(shader: Shader, time: Double) {

        uploadUVAttractors(shader, time)
        uploadColorAttractors(shader, time)

    }

    fun uploadUVAttractors(shader: Shader, time: Double) {

        var attractors = children
            .filterIsInstance<UVAttractor>()

        attractors.forEach {
            it.lastLocalTime = it.getLocalTime(time)
            it.lastInfluence = it.influence[it.lastLocalTime]
        }

        attractors = attractors.filter {
            it.lastInfluence != 0f
        }

        if (attractors.size > maxColorForceFields)
            attractors = attractors
                .sortedByDescending { it.lastInfluence }
                .subList(0, maxColorForceFields)

        shader.v1("forceFieldUVCount", attractors.size)
        if (attractors.isNotEmpty()) {
            val loc1 = shader["forceFieldUVs"]
            val buffer = uvForceFieldBuffer
            if(loc1 > -1){
                buffer.position(0)
                for (attractor in attractors) {
                    val localTime = attractor.lastLocalTime
                    val position = transformLocally(attractor.position[localTime], time)
                    buffer.put(position.x * 0.5f + 0.5f)
                    buffer.put(position.y * 0.5f + 0.5f)
                    buffer.put(position.z)
                }
                buffer.position(0)
                glUniform3fv(loc1, buffer)
            }
            val loc2 = shader["forceFieldUVSpecs"]
            if(loc2 > -1){
                buffer.position(0)
                val sx = if(this is Video) 1f/w else 1f
                val sy = if(this is Video) 1f/h else 1f
                for (attractor in attractors) {
                    val localTime = attractor.lastLocalTime
                    val weight = attractor.lastInfluence
                    val sharpness = attractor.sharpness[localTime]
                    val scale = attractor.scale[localTime]
                    buffer.put(sqrt(sy/sx) * weight * scale.z / scale.x)
                    buffer.put(sqrt(sx/sy) * weight * scale.z / scale.y)
                    buffer.put(10f / (scale.z * weight * weight))
                    buffer.put(sharpness)
                }
                buffer.position(0)
                glUniform4fv(loc2, buffer)
            }
        }

    }

    fun uploadColorAttractors(shader: Shader, time: Double) {

        var attractors = children
            .filterIsInstance<ColorAttractor>()

        attractors.forEach {
            it.lastLocalTime = it.getLocalTime(time)
            it.lastInfluence = it.influence[it.lastLocalTime]
        }

        if (attractors.size > maxColorForceFields)
            attractors = attractors
                .sortedByDescending { it.lastInfluence }
                .subList(0, maxColorForceFields)

        shader.v1("forceFieldColorCount", attractors.size)
        if (attractors.isNotEmpty()) {
            shader.v4("forceFieldBaseColor", attractorBaseColor[time])
            val buffer = colorForceFieldBuffer
            buffer.position(0)
            for (attractor in attractors) {
                val localTime = attractor.lastLocalTime
                val color = attractor.color[localTime]
                buffer.put(color)
            }
            buffer.position(0)
            glUniform4fv(shader["forceFieldColors"], buffer)
            buffer.position(0)
            for (attractor in attractors) {
                val localTime = attractor.lastLocalTime
                val position = transformLocally(attractor.position[localTime], time)
                val weight = attractor.lastInfluence
                buffer.put(position)
                buffer.put(weight)
            }
            buffer.position(0)
            glUniform4fv(shader["forceFieldPositionsNWeights"], buffer)
            buffer.position(0)
            val sx = if(this is Video) 1f/w else 1f
            val sy = if(this is Video) 1f/h else 1f
            for (attractor in attractors) {
                val localTime = attractor.lastLocalTime
                val scale = attractor.scale[localTime]
                val power = attractor.sharpness[localTime]
                buffer.put(abs(sy/sx/scale.x))
                buffer.put(abs(sx/sy/scale.y))
                buffer.put(abs(1f/scale.z))
                buffer.put(power)
            }
            buffer.position(0)
            glUniform4fv(shader["forceFieldColorPowerSizes"], buffer)
        }

    }

}