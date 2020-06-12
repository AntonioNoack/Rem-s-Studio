package me.anno.input

import me.anno.config.DefaultConfig
import me.anno.gpu.GFX
import me.anno.gpu.GFX.getClickedPanel
import me.anno.gpu.GFX.getClickedPanelAndWindow
import me.anno.gpu.GFX.inFocus
import me.anno.gpu.GFX.window
import me.anno.gpu.GFX.openMenu
import me.anno.gpu.GFX.requestExit
import me.anno.gpu.GFX.windowStack
import me.anno.studio.Studio.addEvent
import me.anno.ui.editor.sceneView.SceneView
import me.anno.utils.length
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWDropCallback
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.util.HashSet
import kotlin.math.abs

object Input {

    fun copy() {
        val copied = inFocus?.onCopyRequested(mouseX, mouseY)
        if (copied != null) {
            val selection = StringSelection(copied)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        }
    }

    fun paste() {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        try {
            val data = clipboard.getData(DataFlavor.stringFlavor) as? String
            if (data != null) inFocus?.onPaste(mouseX, mouseY, data, "")
            return
        } catch (e: UnsupportedFlavorException){ }
        try {
            val data = clipboard.getData(DataFlavor.javaFileListFlavor) as? List<File>
            if (data != null) inFocus?.onPasteFiles(mouseX, mouseY, data)
            return
        } catch (e: UnsupportedFlavorException){ }
        println("Unsupported Data Flavor")
    }

    fun save() {
        // todo save the project
    }

    val keysDown = HashMap<Int, Long>()

    var mouseX = 0f
    var mouseY = 0f

    var mouseDownX = 0f
    var mouseDownY = 0f
    var mouseKeysDown = HashSet<Int>()
    
    var lastClickX = 0f
    var lastClickY = 0f
    var lastClickTime = 0L
    var keyModState = 0

    val isControlDown get() = (keyModState and GLFW.GLFW_MOD_CONTROL) != 0
    val isShiftDown get() = (keyModState and GLFW.GLFW_MOD_SHIFT) != 0
    val isCapsLockDown get() = (keyModState and GLFW.GLFW_MOD_CAPS_LOCK) != 0
    val isAltDown get() = (keyModState and GLFW.GLFW_MOD_ALT) != 0
    val isSuperDown get() = (keyModState and GLFW.GLFW_MOD_SUPER) != 0

    fun initForGLFW(){

        GLFW.glfwSetDropCallback(window){ _: Long, count: Int, names: Long ->
            if(count > 0) addEvent {
                inFocus = getClickedPanel(mouseX, mouseY)
                inFocus?.apply {
                    val files = Array(count){ nameIndex ->
                        try {
                            File(GLFWDropCallback.getName(names, nameIndex))
                        } catch (e: Exception){ null }
                    }
                    onPasteFiles(mouseX, mouseY, files.toList().filterNotNull())
                }
            }
        }

        GLFW.glfwSetCharCallback(window) { _, codepoint ->
            addEvent {
                // println("char event $codepoint")
            }
        }

        GLFW.glfwSetCharModsCallback(window) { _, codepoint, mods ->
            addEvent {
                inFocus?.onCharTyped(mouseX, mouseY, codepoint)
                keyModState = mods
                // println("char mods event $codepoint $mods")
            }
        }
        GLFW.glfwSetCursorPosCallback(window) { _, xpos, ypos ->
            addEvent {

                val newX = xpos.toFloat()
                val newY = ypos.toFloat()

                val dx = newX - mouseX
                val dy = newY - mouseY

                mouseX = newX
                mouseY = newY

                inFocus?.onMouseMoved(mouseX, mouseY, dx, dy)
                ActionManager.onMouseMoved(dx, dy)

            }
        }
        var mouseStart = 0L
        GLFW.glfwSetMouseButtonCallback(window) { _, button, action, mods ->
            addEvent {
                when (action) {
                    GLFW.GLFW_PRESS -> {
                        // find the clicked element
                        mouseDownX = mouseX
                        mouseDownY = mouseY
                        val panelWindow = getClickedPanelAndWindow(mouseX, mouseY)
                        if(panelWindow != null){
                            while(panelWindow.second != windowStack.peek()){
                                windowStack.pop()
                            }
                        }
                        inFocus = panelWindow?.first
                        inFocus?.onMouseDown(mouseX, mouseY, button)
                        ActionManager.onKeyDown(button)
                        mouseStart = System.nanoTime()
                        mouseKeysDown.add(button)
                        keysDown[button] = GFX.lastTime
                    }
                    GLFW.GLFW_RELEASE -> {

                        inFocus?.onMouseUp(mouseX, mouseY, button)

                        ActionManager.onKeyUp(button)
                        ActionManager.onKeyTyped(button)

                        val longClickMillis = DefaultConfig["longClick", 300]
                        val currentNanos = System.nanoTime()
                        val isDoubleClick = abs(lastClickTime - currentNanos) / 1_000_000 < longClickMillis && length(mouseX - lastClickX, mouseY - lastClickY) < 5f
                        val mouseDuration = currentNanos - mouseStart
                        val isLongClick = mouseDuration / 1_000_000 < longClickMillis

                        if(isDoubleClick){
                            ActionManager.onKeyDoubleClick(button)
                            inFocus?.onDoubleClick(mouseX, mouseY, button)
                        } else {
                            inFocus?.onMouseClicked(mouseX, mouseY, button, isLongClick)
                        }

                        lastClickX = mouseX
                        lastClickY = mouseY
                        lastClickTime = currentNanos
                        mouseKeysDown.remove(button)
                        keysDown.remove(button)

                    }
                }
                keyModState = mods
            }
        }
        GLFW.glfwSetScrollCallback(window) { window, xoffset, yoffset ->
            addEvent {
                val clicked = getClickedPanel(mouseX, mouseY)
                clicked?.onMouseWheel(mouseX, mouseY, xoffset.toFloat(), yoffset.toFloat())
            }
        }
        GLFW.glfwSetKeyCallback(window) { window, key, scancode, action, mods ->
            addEvent {
                fun keyTyped(key: Int) {

                    ActionManager.onKeyTyped(key)

                    when (key) {
                        GLFW.GLFW_KEY_ENTER -> {
                            if(isShiftDown || isControlDown){
                                inFocus?.onCharTyped(mouseX, mouseY, '\n'.toInt())
                            } else {
                                inFocus?.onEnterKey(mouseX, mouseY)
                            }
                        }
                        GLFW.GLFW_KEY_DELETE -> inFocus?.onDeleteKey(mouseX, mouseY)
                        GLFW.GLFW_KEY_BACKSPACE -> inFocus?.onBackKey(mouseX, mouseY)
                        GLFW.GLFW_KEY_ESCAPE -> {
                            if (inFocus is SceneView) {
                                if (windowStack.size < 2) {
                                    openMenu(mouseX, mouseY, "Exit?",
                                        listOf(
                                            "Save" to { b, l -> true },
                                            "Save & Exit" to { b, l -> true },
                                            "Exit" to { _, _ -> requestExit(); true }
                                        ))
                                } else windowStack.pop()
                            } else {
                                inFocus = windowStack.mapNotNull {
                                    it.panel.listOfAll.firstOrNull { panel -> panel is SceneView }
                                }.firstOrNull()
                            }
                        }
                        // GLFW.GLFW_KEY_PRINT_SCREEN -> { Layout.printLayout() }
                        // GLFW.GLFW_KEY_F11 -> addEvent { GFX.toggleFullscreen() }
                        // GLFW.GLFW_KEY_F12 -> addEvent {}
                        else -> {

                            if (isControlDown) {
                                if (action == GLFW.GLFW_PRESS) {
                                    when (key) {
                                        GLFW.GLFW_KEY_S -> save()
                                        GLFW.GLFW_KEY_V -> paste()
                                        GLFW.GLFW_KEY_C -> copy()
                                        GLFW.GLFW_KEY_X -> {
                                            copy()
                                            inFocus?.onEmpty(mouseX, mouseY)
                                        }
                                        GLFW.GLFW_KEY_A -> inFocus?.onSelectAll(mouseX, mouseY)
                                    }
                                }
                            }
                            // println("typed by $action")
                            inFocus?.onKeyTyped(mouseX, mouseY, key)
                            // inFocus?.onCharTyped(mx,my,key)
                        }
                    }
                }
                when (action) {
                    GLFW.GLFW_PRESS -> {
                        keysDown[key] = GFX.lastTime
                        inFocus?.onKeyDown(mouseX, mouseY, key) // 264
                        ActionManager.onKeyDown(key)
                        keyTyped(key)
                    }
                    GLFW.GLFW_RELEASE -> {
                        inFocus?.onKeyUp(mouseX, mouseY, key)
                        ActionManager.onKeyUp(key)
                        keysDown.remove(key)
                    } // 265
                    GLFW.GLFW_REPEAT -> keyTyped(key)
                }
                // println("event $key $scancode $action $mods")
                keyModState = mods
            }
        }


    }


}