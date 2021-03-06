package me.anno.gpu.hidden

import me.anno.gpu.GFXBase0
import me.anno.utils.Clock
import org.apache.logging.log4j.LogManager
import org.lwjgl.Version
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GLCapabilities
import org.lwjgl.system.MemoryUtil

/**
 * a class, which allows us to use OpenGL without visible window
 * */
object HiddenOpenGLContext {

    private var window = 0L
    private var width = 1
    private var height = 1

    private var errorCallback: GLFWErrorCallback? = null

    private val LOGGER = LogManager.getLogger(HiddenOpenGLContext::class)

    var capabilities: GLCapabilities? = null

    fun createOpenGL() {

        LOGGER.info("Using LWJGL Version " + Version.getVersion())

        val tick = Clock()
        GLFW.glfwSetErrorCallback(GLFWErrorCallback.createPrint(System.err).also { errorCallback = it })
        tick.stop("error callback")

        check(GLFW.glfwInit()) { "Unable to initialize GLFW" }

        tick.stop("GLFW initialization")

        GLFW.glfwDefaultWindowHints()
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
        GLFW.glfwWindowHint(GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE)

        // removes scaling options -> how could we replace them?
        window = GLFW.glfwCreateWindow(width, height, GFXBase0.projectName, MemoryUtil.NULL, MemoryUtil.NULL)
        if (window == MemoryUtil.NULL) throw RuntimeException("Failed to create the GLFW window")

        tick.stop("create window")

        GLFW.glfwMakeContextCurrent(window)
        GLFW.glfwSwapInterval(1)
        capabilities = GL.createCapabilities()

    }

}