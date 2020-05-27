package me.anno.objects.blending

import org.lwjgl.opengl.GL30.*

// todo custom blend modes?
class BlendMode(
    val displayName: String,
    val id: String
){

    var src = GL_SRC_ALPHA
    var dst = GL_ONE_MINUS_SRC_ALPHA
    var srcAlpha = GL_SRC_ALPHA
    var dstAlpha = GL_ONE_MINUS_SRC_ALPHA
    var func = BlendFunc.ADD
    var funcAlpha = BlendFunc.ADD

    init {
        modes[id] = this
    }

    fun set(src: Int, dst: Int) = set(src, dst, srcAlpha, dstAlpha)
    fun set(src: Int, dst: Int, srcAlpha: Int, dstAlpha: Int): BlendMode {
        this.src = src
        this.dst = dst
        this.srcAlpha = srcAlpha
        this.dstAlpha = dstAlpha
        return this
    }

    fun set(func: BlendFunc, funcAlpha: BlendFunc = func): BlendMode {
        this.func = func
        this.funcAlpha = funcAlpha
        return this
    }

    fun apply(){
        if(this != UNSPECIFIED){
            if(lastFunc != func || lastFuncAlpha != funcAlpha){
                lastFunc = func
                lastFuncAlpha = funcAlpha
                glBlendEquationSeparate(func.mode, funcAlpha.mode)
            }
            if(lastMode !== this && (func.hasParams || funcAlpha.hasParams)){
                glBlendFuncSeparate(src, dst, srcAlpha, dstAlpha)
                lastMode = this
            }
        }
    }

    fun copy(displayName: String, id: String): BlendMode {
        val mode = BlendMode(displayName, id)
        mode.set(src, dst, srcAlpha, dstAlpha)
        mode.set(func, funcAlpha)
        return mode
    }

    companion object {

        var lastFunc: BlendFunc? = null
        var lastFuncAlpha: BlendFunc? = null
        var lastMode: BlendMode? = null

        /*
        DEFAULT("Default", 0, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA),
        ADD("Add", 1, GL_SRC_ALPHA, GL_ONE),
        SUBTRACT("Subtract", 2, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        */

        val UNSPECIFIED = BlendMode("Parent", "")
        val DEFAULT = BlendMode("Default", "def")
        val ADD = BlendMode("Add", "add")
            .set(GL_SRC_ALPHA, GL_ONE)
        val SUB = ADD.copy("Sub", "sub")
            .set(BlendFunc.REV_SUB)

        operator fun get(code: String) = modes[code] ?: UNSPECIFIED
    }

}