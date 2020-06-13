package me.anno.input

import me.anno.config.DefaultConfig
import me.anno.gpu.GFX
import me.anno.gpu.GFX.hoveredPanel
import me.anno.gpu.GFX.inFocus
import me.anno.gpu.GFX.inFocus0
import me.anno.io.utils.StringMap
import me.anno.objects.Video
import me.anno.studio.UILayouts
import me.anno.studio.RemsStudio
import me.anno.studio.Studio
import me.anno.studio.Studio.dragged
import me.anno.studio.Studio.editorTimeDilation
import me.anno.ui.base.Panel
import java.io.File
import kotlin.math.abs

object ActionManager {

    val keyDragDelay = DefaultConfig["keyDragDelay", 0.5f]

    val localActions = HashMap<Pair<String, KeyCombination>, List<String>>()

    val globalActions = HashMap<KeyCombination, List<String>>()

    fun init(){

        /**
         * types:
         * - typed -> typed
         * - down -> down
         * - while down -> press
         * - up -> up
         * */

        val defaultValue = StringMap()
        defaultValue["global.space.down.${Modifiers[false, false]}"] = "Play|Pause"
        defaultValue["global.space.down.${Modifiers[false, true]}"] = "PlaySlow|Pause"
        defaultValue["global.space.down.${Modifiers[true, false]}"] = "PlayReversed|Pause"
        defaultValue["global.space.down.${Modifiers[true, true]}"] = "PlayReversedSlow|Pause"
        defaultValue["global.f11.down"] = "ToggleFullscreen"
        defaultValue["global.print.down"] = "PrintLayout"
        defaultValue["global.left.up"] = "DragEnd"
        defaultValue["global.f5.down.${Modifiers[true, false]}"] = "ClearCache"

        // press instead of down for the delay
        defaultValue["FileEntry.left.press"] = "DragStart"
        defaultValue["FileEntry.left.double"] = "Enter|Open"
        defaultValue["FileExplorer.right.down"] = "Back"
        defaultValue["TreeViewPanel.left.press"] = "DragStart"

        defaultValue["HSVBox.left.down"] = "selectColor"
        defaultValue["HSVBox.left.press-unsafe"] = "selectColor"

        defaultValue["SceneView.right.p"] = "Turn"
        defaultValue["SceneView.left.p"] = "MoveObject"
        defaultValue["SceneView.left.p.${Modifiers[false, true]}"] = "MoveObjectAlternate"
        defaultValue["SceneView.numpad0.down"] = "ResetCamera"
        defaultValue["SceneView.w.p"] = "MoveForward"
        defaultValue["SceneView.a.p"] = "MoveLeft"
        defaultValue["SceneView.s.p"] = "MoveBackward"
        defaultValue["SceneView.d.p"] = "MoveRight"
        defaultValue["SceneView.q.p"] = "MoveDown"
        defaultValue["SceneView.e.p"] = "MoveUp"

        // todo somehow not working
        defaultValue["GraphEditorBody.arrowLeft.press"] = "MoveLeft"
        defaultValue["GraphEditorBody.arrowRight.press"] = "MoveRight"

        defaultValue["PureTextInput.leftArrow.down"] = "MoveLeft"
        defaultValue["PureTextInput.rightArrow.down"] = "MoveRight"

        parseConfig(defaultValue)

    }

    fun parseConfig(config: StringMap){

        config.entries.forEach { (key, value) ->
            val keys = key.split('.')
            val namespace = keys[0]
            val button = keys[1]
            val buttonEvent = keys[2]
            val modifiers = keys.getOrElse(3){ "" }
            val keyComb = KeyCombination.parse(button, buttonEvent, modifiers)
            if(keyComb != null){
                val values = value.toString().split('|')
                if(namespace.equals("global", true)){
                    globalActions[keyComb] = values
                } else {
                    println("$namespace,$keyComb=$values")
                    localActions[namespace to keyComb] = values
                }
            }
        }

    }

    fun onKeyTyped(key: Int){
        onEvent(0f, 0f, KeyCombination(key, Input.keyModState, KeyCombination.Type.TYPED), false)
    }

    fun onKeyUp(key: Int){
        onEvent(0f, 0f, KeyCombination(key, Input.keyModState, KeyCombination.Type.UP), false)
    }

    fun onKeyDown(key: Int){
        onEvent(0f, 0f, KeyCombination(key, Input.keyModState, KeyCombination.Type.DOWN), false)
    }

    fun onKeyDoubleClick(key: Int){
        onEvent(0f, 0f, KeyCombination(key, Input.keyModState, KeyCombination.Type.DOUBLE), false)
    }

    fun onKeyHoldDown(dx: Float, dy: Float, key: Int, save: Boolean){
        onEvent(dx, dy, KeyCombination(key, Input.keyModState, if(save) KeyCombination.Type.PRESS else KeyCombination.Type.PRESS_UNSAFE), true)
    }

    fun onMouseIdle() = onMouseMoved(0f, 0f)

    fun onMouseMoved(dx: Float, dy: Float){
        Input.keysDown.forEach { (key, downTime) ->
            onKeyHoldDown(dx, dy, key, false)
            val deltaTime = abs(downTime - GFX.lastTime) * 1e-9f
            if(deltaTime >= keyDragDelay){
                onKeyHoldDown(dx, dy, key, true)
            }
        }
    }

    fun onEvent(dx: Float, dy: Float, combination: KeyCombination, isContinuous: Boolean){
        executeGlobally(0f, 0f, false, globalActions[combination])
        val x = Input.mouseX
        val y = Input.mouseY
        var panel = inFocus.firstOrNull()
        targetSearch@ while(panel != null){
            val clazz = panel.getClassName()
            val actions = localActions[clazz to combination] ?: localActions["*" to combination]
            if(actions != null){
                for(action in actions){
                    if(panel.onGotAction(x, y, dx, dy, action, isContinuous)){
                        break@targetSearch
                    }
                }
            }
            panel = panel.parent
        }
    }

    fun executeLocally(dx: Float, dy: Float, isContinuous: Boolean,
                       panel: Panel, actions: List<String>?){
        if(actions == null) return
        for(action in actions){
            if(panel.onGotAction(Input.mouseX, Input.mouseY, dx, dy, action, isContinuous)){
                break
            }
        }
    }

    fun executeGlobally(dx: Float, dy: Float, isContinuous: Boolean,
                        actions: List<String>?){
        if(actions == null) return
        // execute globally
        for(action in actions){
            fun setEditorTimeDilation(dilation: Float): Boolean {
                return if(dilation == editorTimeDilation || inFocus0?.isKeyInput() == true) false
                else {
                    editorTimeDilation = dilation
                    true
                }
            }
            if(when(action){
                    "Play" -> setEditorTimeDilation(1f)
                    "Pause" -> setEditorTimeDilation(0f)
                    "PlaySlow" -> setEditorTimeDilation(0.2f)
                    "PlayReversed" -> setEditorTimeDilation(-1f)
                    "PlayReversedSlow" -> setEditorTimeDilation(-0.2f)
                    "ToggleFullscreen" -> { GFX.toggleFullscreen(); true }
                    "PrintLayout" -> { UILayouts.printLayout();true }
                    "DragEnd" -> {
                        val dragged = dragged
                        if(dragged != null){

                            val data = dragged.getContent()
                            val type = dragged.getContentType()

                            when(type){
                                "File" -> {
                                    hoveredPanel?.onPasteFiles(Input.mouseX, Input.mouseY, listOf(File(data)))
                                }
                                else -> {
                                    hoveredPanel?.onPaste(Input.mouseX, Input.mouseY, data, type)
                                }
                            }

                            Studio.dragged = null

                            true
                        } else false
                    }
                    "ClearCache" -> {
                        Video.clearCache()
                        true
                    }
                    else -> false
                }) return
        }
        for(window in RemsStudio.windowStack){
            for(panel in window.panel.listOfAll){
                executeLocally(dx, dy, isContinuous, panel, actions)
            }
        }
    }

}