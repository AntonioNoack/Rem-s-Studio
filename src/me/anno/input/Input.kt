package me.anno.input

import me.anno.config.DefaultConfig
import me.anno.gpu.GFX
import me.anno.gpu.GFX.getPanelAndWindowAt
import me.anno.gpu.GFX.getPanelAt
import me.anno.gpu.GFX.inFocus
import me.anno.gpu.GFX.inFocus0
import me.anno.gpu.GFX.openMenu
import me.anno.gpu.GFX.requestExit
import me.anno.gpu.GFX.requestFocus
import me.anno.gpu.GFX.window
import me.anno.gpu.GFX.windowStack
import me.anno.input.Touch.Companion.onTouchDown
import me.anno.input.Touch.Companion.onTouchMove
import me.anno.input.Touch.Companion.onTouchUp
import me.anno.studio.RemsStudio.project
import me.anno.studio.StudioBase.Companion.addEvent
import me.anno.ui.editor.treeView.TreeViewPanel
import me.anno.utils.length
import org.apache.logging.log4j.LogManager
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWDropCallback
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.List
import kotlin.collections.filterIsInstance
import kotlin.collections.filterNotNull
import kotlin.collections.firstOrNull
import kotlin.collections.forEach
import kotlin.collections.isNotEmpty
import kotlin.collections.listOf
import kotlin.collections.minusAssign
import kotlin.collections.plusAssign
import kotlin.collections.set
import kotlin.collections.toList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object Input {

    private val LOGGER = LogManager.getLogger(Input::class)

    var mouseX = 0f
    var mouseY = 0f

    var mouseDownX = 0f
    var mouseDownY = 0f
    var mouseKeysDown = HashSet<Int>()

    val keysDown = HashMap<Int, Long>()
    
    var lastClickX = 0f
    var lastClickY = 0f
    var lastClickTime = 0L
    var keyModState = 0

    var mouseMovementSinceMouseDown = 0f

    val isControlDown get() = (keyModState and GLFW.GLFW_MOD_CONTROL) != 0
    val isShiftDown get() = (keyModState and GLFW.GLFW_MOD_SHIFT) != 0
    val isCapsLockDown get() = (keyModState and GLFW.GLFW_MOD_CAPS_LOCK) != 0
    val isAltDown get() = (keyModState and GLFW.GLFW_MOD_ALT) != 0
    val isSuperDown get() = (keyModState and GLFW.GLFW_MOD_SUPER) != 0

    var framesSinceLastInteraction = 0
    val layoutFrameCount = 10

    fun needsLayoutUpdate() = framesSinceLastInteraction < layoutFrameCount

    fun invalidateLayout(){
        framesSinceLastInteraction = 0
    }

    fun Int.toMouseButton() = when(this){
        0 -> MouseButton.LEFT
        1 -> MouseButton.RIGHT
        2 -> MouseButton.MIDDLE
        else -> MouseButton.UNKNOWN
    }

    fun initForGLFW(){

        GLFW.glfwSetDropCallback(window){ _: Long, count: Int, names: Long ->
            if(count > 0) {
                // it's important to be executed here, because the strings may be GCed otherwise
                val files = Array(count){ nameIndex ->
                    try {
                        File(GLFWDropCallback.getName(names, nameIndex))
                    } catch (e: Exception){ null }
                }
                addEvent {
                    framesSinceLastInteraction = 0
                    requestFocus(getPanelAt(mouseX, mouseY), true)
                    inFocus0?.apply {
                        onPasteFiles(mouseX, mouseY, files.toList().filterNotNull())
                    }
                }
            }
        }

        /*GLFW.glfwSetCharCallback(window) { _, _ ->
            addEvent {
                // LOGGER.info("char event $codepoint")
            }
        } */

        // key typed callback
        GLFW.glfwSetCharModsCallback(window) { _, codepoint, mods ->
            addEvent {
                framesSinceLastInteraction = 0
                inFocus0?.onCharTyped(mouseX, mouseY, codepoint)
                keyModState = mods
            }
        }

        GLFW.glfwSetCursorPosCallback(window) { _, xpos, ypos ->
            addEvent {

                if(keysDown.isNotEmpty()) framesSinceLastInteraction = 0

                val newX = xpos.toFloat()
                val newY = ypos.toFloat()

                val dx = newX - mouseX
                val dy = newY - mouseY

                mouseMovementSinceMouseDown += length(dx, dy)

                mouseX = newX
                mouseY = newY

                inFocus0?.onMouseMoved(mouseX, mouseY, dx, dy)
                ActionManager.onMouseMoved(dx, dy)

            }
        }

        var mouseStart = 0L
        GLFW.glfwSetMouseButtonCallback(window) { _, button, action, mods ->
            addEvent {
                framesSinceLastInteraction = 0
                when (action) {
                    GLFW.GLFW_PRESS -> {

                        // find the clicked element
                        mouseDownX = mouseX
                        mouseDownY = mouseY
                        mouseMovementSinceMouseDown = 0f

                        val panelWindow = getPanelAndWindowAt(mouseX, mouseY)
                        if(panelWindow != null){
                            val mouseButton = button.toMouseButton()
                            while(true){
                                val peek = windowStack.peek()
                                if(panelWindow.second == peek || !peek.acceptsClickAway(mouseButton)) break
                                windowStack.pop()
                            }
                        }

                        val singleSelect = isControlDown
                        val multiSelect = isShiftDown

                        val mouseTarget = getPanelAt(mouseX, mouseY)
                        val selectionTarget = mouseTarget?.getMultiSelectablePanel()
                        val inFocusTarget = inFocus0?.getMultiSelectablePanel()
                        val joinedParent = inFocusTarget?.parent

                        if((singleSelect || multiSelect) && selectionTarget != null && joinedParent == selectionTarget.parent){
                            if(inFocus0 != inFocusTarget) requestFocus(inFocusTarget, true)
                            if(singleSelect){
                                if(selectionTarget in inFocus) inFocus -= selectionTarget
                                else inFocus += selectionTarget
                            } else {
                                val index0 = inFocusTarget!!.indexInParent
                                val index1 = selectionTarget.indexInParent
                                // todo we should use the last selected as reference point...
                                val minIndex = min(index0, index1)
                                val maxIndex = max(index0, index1)
                                for(index in minIndex .. maxIndex){
                                    inFocus += joinedParent!!.children[index]
                                }
                            }
                            // LOGGER.info(inFocus)
                        } else {
                            requestFocus(panelWindow?.first, true)
                        }

                        inFocus0?.onMouseDown(mouseX, mouseY, button.toMouseButton())
                        ActionManager.onKeyDown(button)
                        mouseStart = System.nanoTime()
                        mouseKeysDown.add(button)
                        keysDown[button] = GFX.lastTime
                    }
                    GLFW.GLFW_RELEASE -> {

                        inFocus0?.onMouseUp(mouseX, mouseY, button.toMouseButton())

                        ActionManager.onKeyUp(button)
                        ActionManager.onKeyTyped(button)

                        val longClickMillis = DefaultConfig["longClick", 300]
                        val currentNanos = System.nanoTime()
                        val maxClickDistance = 5f
                        val isClick = mouseMovementSinceMouseDown < maxClickDistance

                        if(isClick){

                            val isDoubleClick = abs(lastClickTime - currentNanos) / 1_000_000 < longClickMillis &&
                                    length(mouseX - lastClickX, mouseY - lastClickY) < maxClickDistance

                            if(isDoubleClick){

                                ActionManager.onKeyDoubleClick(button)
                                inFocus0?.onDoubleClick(mouseX, mouseY, button.toMouseButton())

                            } else {

                                val mouseDuration = currentNanos - mouseStart
                                val isLongClick = mouseDuration / 1_000_000 >= longClickMillis
                                inFocus0?.onMouseClicked(mouseX, mouseY, button.toMouseButton(), isLongClick)

                            }

                            lastClickX = mouseX
                            lastClickY = mouseY
                            lastClickTime = currentNanos

                        }

                        mouseKeysDown.remove(button)
                        keysDown.remove(button)

                    }
                }
                keyModState = mods
            }
        }

        GLFW.glfwSetScrollCallback(window) { _, xoffset, yoffset ->
            addEvent {
                framesSinceLastInteraction = 0
                val clicked = getPanelAt(mouseX, mouseY)
                clicked?.onMouseWheel(mouseX, mouseY, xoffset.toFloat(), yoffset.toFloat())
            }
        }

        GLFW.glfwSetKeyCallback(window) { window, key, scancode, action, mods ->
            if(window != GFX.window){
                // val pressure = max(1, mods)
                val x = scancode * 0.01f
                val y = action * 0.01f
                addEvent {
                    when(mods){
                        -1 -> onTouchDown(key, x, y)
                        -2 -> onTouchUp(key, x, y)
                        else -> onTouchMove(key, x, y)
                    }
                }
            } else
            addEvent {
                framesSinceLastInteraction = 0
                fun keyTyped(key: Int) {

                    ActionManager.onKeyTyped(key)

                    when (key) {
                        GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                            if(isShiftDown || isControlDown){
                                inFocus0?.onCharTyped(mouseX, mouseY, '\n'.toInt())
                            } else {
                                inFocus0?.onEnterKey(mouseX, mouseY)
                            }
                        }
                        GLFW.GLFW_KEY_DELETE -> {
                            // tree view selections need to be removed, because they would be illogical to keep
                            // (because the underlying Transform changes)
                            val inFocusTreeViews = inFocus.filterIsInstance<TreeViewPanel>()
                            inFocus.forEach { it.onDeleteKey(mouseX, mouseY) }
                            inFocus.removeAll(inFocusTreeViews)
                        }
                        GLFW.GLFW_KEY_BACKSPACE -> {
                            inFocus0?.onBackSpaceKey(mouseX, mouseY)
                        }
                        GLFW.GLFW_KEY_TAB -> {
                            val inFocus0 = inFocus0
                            if(inFocus0 != null){
                                if(isShiftDown || isControlDown || !inFocus0.isKeyInput() || !inFocus0.acceptsChar('\t'.toInt())){
                                    // switch between input elements
                                    val root = inFocus0.rootPanel
                                    // todo only choose effectively visible tabs...
                                    // todo make groups, which are not empty, inputs?
                                    val list = root.listOfAll.toList()
                                    val index = list.indexOf(inFocus0)
                                    if(index > -1){
                                        var next = list
                                            .subList(index+1, list.size)
                                            .firstOrNull { it.isKeyInput() }
                                        if(next == null){
                                            // println("no more text input found, starting from top")
                                            // restart from top
                                            next = list.firstOrNull { it.isKeyInput() }
                                        }// else println(next)
                                        if(next != null){
                                            inFocus.clear()
                                            inFocus += next
                                        }
                                    }// else error, child missing
                                } else {
                                    inFocus0.onCharTyped(mouseX, mouseY, '\t'.toInt())
                                }
                            }
                        }
                        GLFW.GLFW_KEY_ESCAPE -> {
                            // val inFocus = inFocus.firstOrNull()
                            if (windowStack.size < 2) {
                                openMenu(mouseX, mouseY, "Exit?",
                                    listOf(
                                        "Save" to {  },
                                        "Save & Exit" to {  },
                                        "Exit" to { requestExit() }
                                    ))
                            } else windowStack.pop()
                            /*if (true || inFocus is SceneView) {
                                if (windowStack.size < 2) {
                                    openMenu(mouseX, mouseY, "Exit?",
                                        listOf(
                                            "Save" to { b, l -> true },
                                            "Save & Exit" to { b, l -> true },
                                            "Exit" to { _, _ -> requestExit(); true }
                                        ))
                                } else windowStack.pop()
                            } else {
                                requestFocus(windowStack.mapNotNull {
                                    it.panel.listOfAll.firstOrNull { panel -> panel is SceneView }
                                }.firstOrNull(), true)
                            }*/
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
                                            inFocus0?.onEmpty(mouseX, mouseY)
                                        }
                                        GLFW.GLFW_KEY_A -> inFocus0?.onSelectAll(mouseX, mouseY)
                                    }
                                }
                            }
                            // LOGGER.info("typed by $action")
                            inFocus0?.onKeyTyped(mouseX, mouseY, key)
                            // inFocus?.onCharTyped(mx,my,key)
                        }
                    }
                }
                when (action) {
                    GLFW.GLFW_PRESS -> {
                        keysDown[key] = GFX.lastTime
                        inFocus0?.onKeyDown(mouseX, mouseY, key) // 264
                        ActionManager.onKeyDown(key)
                        keyTyped(key)
                    }
                    GLFW.GLFW_RELEASE -> {
                        inFocus0?.onKeyUp(mouseX, mouseY, key)
                        ActionManager.onKeyUp(key)
                        keysDown.remove(key)
                    } // 265
                    GLFW.GLFW_REPEAT -> keyTyped(key)
                }
                // LOGGER.info("event $key $scancode $action $mods")
                keyModState = mods
            }
        }


    }

    fun copy() {
        // todo combine them into an array?
        val copied = inFocus0?.onCopyRequested(mouseX, mouseY)
        if (copied != null) {
            val selection = StringSelection(copied)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        }
    }

    fun paste() {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        try {
            val data = clipboard.getData(DataFlavor.stringFlavor) as? String
            if (data != null) inFocus0?.onPaste(mouseX, mouseY, data, "")
            return
        } catch (e: UnsupportedFlavorException){ }
        try {
            val data = clipboard.getData(DataFlavor.javaFileListFlavor) as? List<File>
            if (data != null) inFocus0?.onPasteFiles(mouseX, mouseY, data)
            return
        } catch (e: UnsupportedFlavorException){ }
        LOGGER.warn("Unsupported Data Flavor")
    }

    fun pasteFiles(files: List<File>){
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(object: Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor?>? {
                return arrayOf(DataFlavor.javaFileListFlavor)
            }
            override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean {
                return DataFlavor.javaFileListFlavor.equals(flavor)
            }
            @Throws(UnsupportedFlavorException::class, IOException::class)
            override fun getTransferData(flavor: DataFlavor?): Any? {
                if (isDataFlavorSupported(flavor)) return files
                throw UnsupportedFlavorException(flavor)
            }
        }, null)
    }

    fun save() {
        // save the project
        project?.save()
    }



}