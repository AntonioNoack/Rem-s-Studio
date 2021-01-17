package me.anno.gpu.buffer

import me.anno.cache.data.ICacheData
import me.anno.gpu.GFX
import me.anno.gpu.shader.Shader
import org.apache.logging.log4j.LogManager
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL33.glDrawArraysInstanced
import org.lwjgl.opengl.GL33.glVertexAttribDivisor
import java.nio.ByteBuffer

abstract class Buffer(val attributes: List<Attribute>, val stride: Int, val usage: Int = GL15.GL_STATIC_DRAW):
    ICacheData {

    constructor(attributes: List<Attribute>, usage: Int) : this(attributes, attributes.sumBy { it.byteSize }, usage)
    constructor(attributes: List<Attribute>) : this(attributes, attributes.sumBy { it.byteSize })

    init {
        var offset = 0L
        attributes.forEach {
            it.offset = offset
            offset += it.byteSize
        }
    }

    var drawMode = GL_TRIANGLES
    var nioBuffer: ByteBuffer? = null

    var drawLength = 0

    var buffer = -1
    var isUpToDate = false

    fun getName() = getName(0)
    fun getName(index: Int) = attributes[index].name

    fun upload() {
        if (!isUpToDate) {
            if (buffer < 0) buffer = glGenBuffers()
            if (buffer < 0) throw RuntimeException()
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer)
            if (nioBuffer == null) {
                createNioBuffer()
                LOGGER.info("called create nio buffer")
            }
            val nio = nioBuffer!!
            drawLength = nio.position() / stride
            nio.position(0)
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, nio, usage)
            isUpToDate = true
        }
    }

    abstract fun createNioBuffer()

    fun quads(): Buffer {
        drawMode = GL11.GL_QUADS
        return this
    }

    fun lines(): Buffer {
        drawMode = GL11.GL_LINES
        return this
    }

    private fun bindBufferAttributes(shader: Shader, instanced: Boolean) {
        val instanceDivisor = if(instanced) 1 else 0
        shader.use()
        attributes.forEach { attr ->
            val index = shader.getAttributeLocation(attr.name)
            if (index > -1) {
                val type = attr.type
                if (attr.isNativeInt) {
                    glVertexAttribIPointer(index, attr.components, type.glType, stride, attr.offset)
                } else {
                    glVertexAttribPointer(index, attr.components, type.glType, type.normalized, stride, attr.offset)
                }
                if(isInstanced[index] != instanced){
                    isInstanced[index] = instanced
                    glVertexAttribDivisor(index, instanceDivisor)
                }
                glEnableVertexAttribArray(index)
            }
        }
    }

    open fun draw(shader: Shader) = draw(shader, drawMode)
    open fun draw(shader: Shader, mode: Int) {
        bind(shader)
        draw(mode, 0, drawLength)
    }

    open fun drawInstanced(shader: Shader, base: Buffer) = drawInstanced(shader, base, drawMode)
    open fun drawInstanced(shader: Shader, base: Buffer, mode: Int) {
        base.bind(shader)
        bindInstanced(shader)
        glDrawArraysInstanced(mode, 0, base.drawLength, drawLength)
    }

    fun bind(shader: Shader) {
        if (!isUpToDate) upload()
        else if (drawLength > 0) glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer)
        if (drawLength > 0) {
            bindBufferAttributes(shader, false)
        }
    }

    fun bindInstanced(shader: Shader) {
        if (!isUpToDate) upload()
        else if (drawLength > 0) glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer)
        if (drawLength > 0) {
            bindBufferAttributes(shader, true)
        }
    }

    fun draw(first: Int, length: Int) {
        glDrawArrays(drawMode, first, length)
    }

    fun draw(mode: Int, first: Int, length: Int) {
        glDrawArrays(mode, first, length)
    }

    override fun destroy() {
        val buffer = buffer
        if (buffer > -1) {
            GFX.addGPUTask(1){
                GL15.glDeleteBuffers(buffer)
            }
        }
        this.buffer = -1
    }

    companion object {
        private val LOGGER = LogManager.getLogger(Buffer::class.java)
        private val isInstanced = BooleanArray(128)
    }

}