package me.anno.gpu.shader

import me.anno.cache.data.ICacheData
import me.anno.gpu.GFX
import me.anno.gpu.framebuffer.Frame
import me.anno.io.files.FileReference.Companion.getReference
import me.anno.ui.editor.files.toAllowedFilename
import me.anno.utils.OS
import me.anno.utils.types.Strings.isBlank2
import org.apache.logging.log4j.LogManager
import org.joml.*
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL21.glUniformMatrix4x3fv
import java.nio.FloatBuffer

open class Shader(
    val shaderName: String,
    val vertex: String,
    val varying: String,
    val fragment: String,
    private val disableShorts: Boolean = false
) : ICacheData {

    companion object {
        private var logShaders = false
        private val LOGGER = LogManager.getLogger(Shader::class)
        private const val attributeName = "in"
        private val matrixBuffer = BufferUtils.createFloatBuffer(16)
        private val identity3: Matrix3fc = Matrix3f()
        private val identity4: Matrix4fc = Matrix4f()
        private val identity4x3: Matrix4x3fc = Matrix4x3f()
        const val DefaultGLSLVersion = 150
        const val UniformCacheSize = 256
        const val UniformCacheSizeX4 = UniformCacheSize * 4
        var safeShaderBinding = false
        var lastProgram = -1
    }

    val safeShaderBinding = Companion.safeShaderBinding

    var glslVersion = DefaultGLSLVersion

    private var program = -1

    private val uniformLocations = HashMap<String, Int>()
    private val attributeLocations = HashMap<String, Int>()
    private val uniformCache = FloatArray(UniformCacheSizeX4) { Float.NaN }

    val pointer get() = program
    private val ignoredNames = HashSet<String>()

    // shader compile time doesn't really matter... -> move it to the start to preserve ram use?
    // isn't that much either...
    fun init() {

        // LOGGER.debug("$shaderName\nVERTEX:\n$vertex\nVARYING:\n$varying\nFRAGMENT:\n$fragment")

        program = glCreateProgram()
        // the shaders are like a C compilation process, .o-files: after linking, they can be removed
        val vertexSource = ("" +
                "#version $glslVersion\n" +
                "${varying.replace("varying", "out")} $vertex").replaceShortCuts()
        val vertexShader = compile(GL_VERTEX_SHADER, vertexSource)
        val fragmentSource = ("" +
                "#version $glslVersion\n" +
                "precision mediump float; ${varying.replace("varying", "in")} ${
                    if (fragment.contains("gl_FragColor") && glslVersion == DefaultGLSLVersion) {
                        "out vec4 glFragColor;" +
                                fragment.replace("gl_FragColor", "glFragColor")
                    } else fragment
                }").replaceShortCuts()

        val fragmentShader = compile(GL_FRAGMENT_SHADER, fragmentSource)
        glLinkProgram(program)
        glDeleteShader(vertexShader)
        glDeleteShader(fragmentShader)
        logShader(vertexSource, fragmentSource)

        GFX.check()

    }

    fun setTextureIndices(textures: List<String>?) {
        use()
        if (textures == null) return
        for ((index, name) in textures.withIndex()) {
            val texName = getUniformLocation(name)
            if (texName >= 0) glUniform1i(texName, index)
        }
    }

    fun logShader(vertex: String, fragment: String) {
        if (logShaders) {
            val folder = OS.desktop.getChild("shaders")!!
            folder.mkdirs()
            fun print(ext: String, data: String) {
                val name = "$shaderName.$ext".toAllowedFilename() ?: return
                getReference(folder, name).writeText(data)
            }
            print("vert", vertex)
            print("frag", fragment)
        }
    }

    // this function probably could be made more efficient...
    fun String.replaceShortCuts() = if (disableShorts) this else this
        .replace("\n", " \n ")
        .replace(";", " ; ")
        .replace(" u1i ", " uniform int ")
        .replace(" u2i ", " uniform ivec2 ")
        .replace(" u3i ", " uniform ivec3 ")
        .replace(" u4i ", " uniform ivec4 ")
        .replace(" u1 ", " uniform float ")
        .replace(" u2 ", " uniform vec2 ")
        .replace(" u3 ", " uniform vec3 ")
        .replace(" u4 ", " uniform vec4 ")
        .replace(" u1f ", " uniform float ")
        .replace(" u2f ", " uniform vec2 ")
        .replace(" u3f ", " uniform vec3 ")
        .replace(" u4f ", " uniform vec4 ")
        .replace(" u2x2 ", " uniform mat2 ")
        .replace(" u3x3 ", " uniform mat3 ")
        .replace(" u4x4 ", " uniform mat4 ")
        .replace(" u4x3 ", " uniform mat4x3 ")
        .replace(" u3x4 ", " uniform mat3x4 ")
        .replace(" a1 ", " $attributeName float ")
        .replace(" a2 ", " $attributeName vec2 ")
        .replace(" a3 ", " $attributeName vec3 ")
        .replace(" a4 ", " $attributeName vec4 ")
        .replace(" ai1 ", " $attributeName int ")
        .replace(" ai2 ", " $attributeName ivec2 ")
        .replace(" ai3 ", " $attributeName ivec3 ")
        .replace(" ai4 ", " $attributeName ivec4 ")
        .replace(" v1 ", " float ")
        .replace(" v2 ", " vec2 ")
        .replace(" v3 ", " vec3 ")
        .replace(" v4 ", " vec4 ")
        .replace(" m2 ", " mat2 ")
        .replace(" m3 ", " mat3 ")
        .replace(" m4 ", " mat4 ")

    private fun compile(type: Int, source: String): Int {
        // ("$shaderName/$type: $source")
        val shader = glCreateShader(type)
        glShaderSource(shader, source)
        glCompileShader(shader)
        glAttachShader(program, shader)
        postPossibleError(shader, source)
        return shader
    }

    private fun postPossibleError(shader: Int, source: String) {
        val log = glGetShaderInfoLog(shader)
        if (!log.isBlank2()) {
            LOGGER.warn(
                "$log by $shaderName\n\n${
                    source
                        .split('\n')
                        .mapIndexed { index, line ->
                            "${"%1\$3s".format(index + 1)}: $line"
                        }.joinToString("\n")
                }"
            )
            /*if(!log.contains("deprecated", true)){
                throw RuntimeException()
            }*/
        }
    }

    fun ignoreUniformWarnings(names: Collection<String>) {
        ignoredNames += names
    }

    fun getUniformLocation(name: String): Int {
        val old = uniformLocations[name]
        if (old != null) return old
        if (safeShaderBinding) use()
        val loc = glGetUniformLocation(program, name)
        uniformLocations[name] = loc
        if (loc < 0 && name !in ignoredNames) {
            LOGGER.warn("Uniform location \"$name\" not found in shader $shaderName")
        }
        return loc
    }

    fun getAttributeLocation(name: String): Int {
        val old = attributeLocations[name]
        if (old != null) return old
        if (safeShaderBinding) use()
        val loc = glGetAttribLocation(program, name)
        attributeLocations[name] = loc
        if (loc < 0 && name !in ignoredNames) {
            LOGGER.warn("Attribute location \"$name\" not found in shader $shaderName")
        }
        return loc
    }

    fun use(): Boolean {
        GFX.check()
        Frame.bindMaybe()
        GFX.check()
        if (program == -1) init()
        return if (program != lastProgram) {
            glUseProgram(program)
            lastProgram = program
            true
        } else false
    }

    fun potentiallyUse() {
        if (safeShaderBinding) {
            if (use()) {
                throw IllegalStateException("Shader $shaderName wasn't bound!")
            }
        }
    }

    fun v1(name: String, x: Int) = v1(getUniformLocation(name), x)
    fun v1(loc: Int, x: Int) {
        if (loc > -1) {
            val asFloat = x.toFloat()
            when {
                asFloat.toInt() != x -> {
                    // cannot be represented as a float -> cannot currently be cached
                    if (loc < UniformCacheSize) uniformCache[loc * 4] = Float.NaN
                    potentiallyUse()
                    glUniform1i(loc, x)
                }
                loc >= UniformCacheSize -> {
                    potentiallyUse()
                    glUniform1i(loc, x)
                }
                uniformCache[loc * 4] != asFloat -> {
                    // it has changed
                    uniformCache[loc * 4] = asFloat
                    potentiallyUse()
                    glUniform1i(loc, x)
                }
            }
        }
    }

    fun v1(name: String, x: Float) = v1(getUniformLocation(name), x)
    fun v1(loc: Int, x: Float) {
        if (loc > -1) {
            if (loc >= UniformCacheSize) {
                potentiallyUse()
                glUniform1f(loc, x)
            } else {
                val index0 = loc * 4
                if (uniformCache[index0 + 0] != x) {
                    uniformCache[index0 + 0] = x
                    potentiallyUse()
                    glUniform1f(loc, x)
                }
            }
        }
    }

    fun v2(name: String, x: Float, y: Float) = v2(getUniformLocation(name), x, y)
    fun v2(loc: Int, x: Float, y: Float) {
        if (loc > -1) {
            if (loc >= UniformCacheSize) {
                potentiallyUse()
                glUniform2f(loc, x, y)
            } else {
                val index0 = loc * 4
                if (
                    uniformCache[index0 + 0] != x ||
                    uniformCache[index0 + 1] != y
                ) {
                    uniformCache[index0 + 0] = x
                    uniformCache[index0 + 1] = y
                    potentiallyUse()
                    glUniform2f(loc, x, y)
                }
            }
        }
    }

    fun v3(name: String, x: Float, y: Float, z: Float) = v3(getUniformLocation(name), x, y, z)
    fun v3(loc: Int, x: Float, y: Float, z: Float) {
        if (loc > -1) {
            if (loc >= UniformCacheSize) {
                potentiallyUse()
                glUniform3f(loc, x, y, z)
            } else {
                val index0 = loc * 4
                if (
                    uniformCache[index0 + 0] != x ||
                    uniformCache[index0 + 1] != y ||
                    uniformCache[index0 + 2] != z
                ) {
                    uniformCache[index0 + 0] = x
                    uniformCache[index0 + 1] = y
                    uniformCache[index0 + 2] = z
                    potentiallyUse()
                    glUniform3f(loc, x, y, z)
                }
            }
        }
    }

    fun v3X(loc: Int, x: Float, y: Float, z: Float, w: Float) = v3(loc, x / w, y / w, z / w)
    fun v3X(name: String, x: Float, y: Float, z: Float, w: Float) = v3(name, x / w, y / w, z / w)
    fun v3X(loc: Int, v: Vector4f) = v3(loc, v.x / v.w, v.y / v.w, v.z / v.w)
    fun v3X(name: String, v: Vector4f) = v3(name, v.x / v.w, v.y / v.w, v.z / v.w)

    fun v3X(loc: Int, v: Vector4fc) = v3X(loc, v.x(), v.y(), v.z(), v.w())
    fun v3X(name: String, v: Vector4fc) = v3X(name, v.x(), v.y(), v.z(), v.w())

    fun v3(name: String, color: Int) = v3(getUniformLocation(name), color)
    fun v3(loc: Int, color: Int) {
        v3(
            loc,
            (color.shr(16) and 255) / 255f,
            (color.shr(8) and 255) / 255f,
            color.and(255) / 255f
        )
    }

    fun v4(name: String, x: Float, y: Float, z: Float, w: Float) = v4(getUniformLocation(name), x, y, z, w)
    fun v4(loc: Int, x: Float, y: Float, z: Float, w: Float) {
        if (loc > -1) {
            if (loc >= UniformCacheSize) {
                potentiallyUse()
                glUniform4f(loc, x, y, z, w)
            } else {
                val index0 = loc * 4
                if (
                    uniformCache[index0 + 0] != x ||
                    uniformCache[index0 + 1] != y ||
                    uniformCache[index0 + 2] != z ||
                    uniformCache[index0 + 3] != w
                ) {
                    uniformCache[index0 + 0] = x
                    uniformCache[index0 + 1] = y
                    uniformCache[index0 + 2] = z
                    uniformCache[index0 + 3] = w
                    potentiallyUse()
                    glUniform4f(loc, x, y, z, w)
                }
            }
        }
    }

    fun v4(name: String, color: Int) = v4(getUniformLocation(name), color)
    fun v4(loc: Int, color: Int) {
        v4(
            loc,
            (color.shr(16) and 255) / 255f,
            (color.shr(8) and 255) / 255f,
            color.and(255) / 255f,
            (color.shr(24) and 255) / 255f
        )
    }

    fun v4(name: String, color: Int, alpha: Float) = v4(getUniformLocation(name), color, alpha)
    fun v4(loc: Int, color: Int, alpha: Float) {
        v4(
            loc,
            color.shr(16).and(255) / 255f,
            color.shr(8).and(255) / 255f,
            color.and(255) / 255f, alpha
        )
    }

    fun v2(loc: Int, all: Float) = v2(loc, all, all)
    fun v3(loc: Int, all: Float) = v3(loc, all, all, all)
    fun v4(loc: Int, all: Float) = v4(loc, all, all, all, all)

    fun v2(loc: Int, v: Vector2fc) = v2(loc, v.x(), v.y())
    fun v3(loc: Int, v: Vector3fc) = v3(loc, v.x(), v.y(), v.z())
    fun v4(loc: Int, v: Vector4fc) = v4(loc, v.x(), v.y(), v.z(), v.w())

    fun v2(name: String, all: Float) = v2(name, all, all)
    fun v3(name: String, all: Float) = v3(name, all, all, all)
    fun v4(name: String, all: Float) = v4(name, all, all, all, all)

    fun v2(name: String, v: Vector2fc) = v2(name, v.x(), v.y())
    fun v3(name: String, v: Vector3fc) = v3(name, v.x(), v.y(), v.z())
    fun v4(name: String, v: Vector4fc) = v4(name, v.x(), v.y(), v.z(), v.w())

    fun m3x3(name: String, value: Matrix3fc = identity3) = m3x3(getUniformLocation(name), value)
    fun m3x3(loc: Int, value: Matrix3fc = identity3) {
        if (loc > -1) {
            potentiallyUse()
            value.get(matrixBuffer)
            glUniformMatrix3fv(loc, false, matrixBuffer)
        }
    }

    fun m4x3(name: String, value: Matrix4x3fc = identity4x3) = m4x3(getUniformLocation(name), value)
    fun m4x3(loc: Int, value: Matrix4x3fc = identity4x3) {
        if (loc > -1) {
            potentiallyUse()
            value.get(matrixBuffer)
            glUniformMatrix4x3fv(loc, false, matrixBuffer)
        }
    }

    fun m4x4(name: String, value: Matrix4fc? = identity4) = m4x4(getUniformLocation(name), value ?: identity4)
    fun m4x4(loc: Int, value: Matrix4fc = identity4) {
        if (loc > -1) {
            potentiallyUse()
            value.get(matrixBuffer)
            glUniformMatrix4fv(loc, false, matrixBuffer)
        }
    }

    fun v1Array(name: String, value: FloatBuffer) = v1Array(getUniformLocation(name), value)
    fun v1Array(loc: Int, value: FloatBuffer) {
        if (loc > -1) {
            potentiallyUse()
            glUniform1fv(loc, value)
        }
    }

    fun v2Array(name: String, value: FloatBuffer) = v2Array(getUniformLocation(name), value)
    fun v2Array(loc: Int, value: FloatBuffer) {
        if (loc > -1) {
            potentiallyUse()
            glUniform2fv(loc, value)
        }
    }

    fun v3Array(name: String, value: FloatBuffer) = v3Array(getUniformLocation(name), value)
    fun v3Array(loc: Int, value: FloatBuffer) {
        if (loc > -1) {
            potentiallyUse()
            glUniform3fv(loc, value)
        }
    }

    fun v4Array(name: String, value: FloatBuffer) = v4Array(getUniformLocation(name), value)
    fun v4Array(loc: Int, value: FloatBuffer) {
        if (loc > -1) {
            potentiallyUse()
            glUniform4fv(loc, value)
        }
    }

    fun check() = GFX.check()

    operator fun get(name: String) = getUniformLocation(name)

    override fun destroy() {
        if (program > -1) glDeleteProgram(program)
    }

}