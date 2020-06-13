package me.anno.ui.editor.graphs

import me.anno.config.DefaultConfig
import me.anno.config.DefaultStyle.black
import me.anno.config.DefaultStyle.fontGray
import me.anno.config.DefaultStyle.white
import me.anno.gpu.GFX
import me.anno.input.Input.isShiftDown
import me.anno.input.Input.mouseKeysDown
import me.anno.objects.animation.AnimatedProperty
import me.anno.objects.animation.Keyframe
import me.anno.studio.Studio.targetFPS
import me.anno.utils.clamp
import me.anno.utils.pow
import me.anno.ui.base.Panel
import me.anno.ui.style.Style
import me.anno.utils.length
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.glfw.GLFW.*
import kotlin.math.*
import me.anno.input.Input.isControlDown as isControlDown


// todo select multiple keyframes
// todo copy keyframes
// todo paste keyframes
class GraphEditorBody(style: Style): Panel(style.getChild("deep")){

    val accentColor = style.getColor("accentColor", black)

    // time
    var dtHalfLength = 30f
    var centralTime = dtHalfLength

    var centralValue = 0f
    var dvHalfHeight = 1f

    var draggedKeyframe: Keyframe<*>? = null
    var draggedChannel = 0

    var lastUnitScale = 1f

    // style
    var dotSize = style.getSize("dotSize", 8)
    val tinyFontSize = style.getSize("tinyTextSize", 10)
    val fontColor = style.getColor("textColor", fontGray)
    val fontName = style.getString("textFont", DefaultConfig.defaultFont)
    val isBold = style.getBoolean("textBold", false)
    val isItalic = style.getBoolean("textItalic", false)

    fun normValue01(value: Float) = 0.5f - (value-centralValue)/dvHalfHeight * 0.5f
    fun normTime01(time: Float) = (time-centralTime)/dtHalfLength * 0.5f + 0.5f
    fun normAxis11(lx: Float, x0: Int, size: Int) = (lx-x0)/size * 2f - 1f

    fun getValueAt(my: Float) = centralValue - dvHalfHeight * normAxis11(my, y, h)
    fun getTimeAt(mx: Float) = centralTime + dtHalfLength * normAxis11(mx, x, w)
    fun getXAt(time: Float) = x + w * normTime01(time)
    fun getYAt(value: Float) = y + h * normValue01(value)

    override fun calculateSize(w: Int, h: Int) {
        super.calculateSize(w, h)
        val size = 5
        minW = size
        minH = size
    }

    fun get0XString(time: Int) = if(time < 10) "0$time" else "$time"
    fun get00XString(time: Int) = if(time < 100) "00$time" else if(time < 10) "0$time" else "$time"

    fun getTimeString(time: Float, step: Float): String {
        if(time < 0) return "-${getTimeString(-time, step)}"
        val s = time.toInt()
        val m = s / 60
        val h = m / 60
        val subTime = ((time % 1) * targetFPS).roundToInt()
        return if(h < 1) "${get0XString(m % 60)}:${get0XString(s % 60)}${if(step < 1f) "/${get0XString(subTime)}" else ""}"
        else "${get0XString(h)}:${get0XString(m % 60)}:${get0XString(s % 60)}${if(step < 1f) "/${get0XString(subTime)}" else ""}"
    }

    fun getValueString(value: Float, step: Float) = getValueString(abs(value), step, if(value < 0) '-' else '+')

    fun getValueString(value: Float, step: Float, sign: Char): String {
        val int = value.toInt()
        if(step >= 1f) return "$sign$int"
        val float = value % 1
        if(step >= 0.1f) return "$sign$int.${(float*10).roundToInt()}"
        if(step >= 0.01f) return "$sign$int.${get0XString((float*100).roundToInt())}"
        return "$sign$int.${get00XString((float*1000).roundToInt())}"
    }

    fun getTimeStep(time: Float): Float {
        return timeFractions.minBy { abs(it - time) }!!
    }

    fun getValueStep(value: Float): Float {
        return valueFractions.minBy { abs(it - value) }!!
    }

    val timeFractions = listOf(
        0.2f, 0.5f,
        1f, 2f, 5f, 10f, 20f, 30f, 60f,
        120f, 300f, 600f, 1200f, 1800f, 3600f,
        3600f * 1.5f, 3600f * 2f, 3600f * 5f,
        3600f * 6f, 3600f * 12f, 3600f * 24f
    )

    val valueFractions = listOf(
        0.1f, 0.2f, 0.5f, 1f,
        2f, 5f, 10f, 15f, 30f, 45f,
        90f, 120f, 180f, 360f, 720f
    )

    fun drawTimeAxis(x0: Int, y0: Int, x1: Int, y1: Int){

        val minFrame = centralTime - dtHalfLength
        val maxFrame = centralTime + dtHalfLength

        val deltaFrame = 2 * dtHalfLength
        val timeStep = getTimeStep(deltaFrame * 0.2f)

        val minStepIndex = (minFrame / timeStep).toInt() - 1
        val maxStepIndex = (maxFrame / timeStep).toInt() + 1

        for(stepIndex in maxStepIndex downTo minStepIndex){
            val time = stepIndex * timeStep
            val x = getXAt(time).roundToInt()
            if(x > x0+1 && x+2 < x1){
                val text = getTimeString(time, timeStep)
                val size = GFX.getTextSize(fontName, tinyFontSize, isBold, isItalic, text)
                val w = size.first
                GFX.drawRect(x, y0 + 2 + size.second, 1, y1-y0-4-size.second, fontColor and 0x3fffffff)
                GFX.drawText(x - w/2, y0, fontName, tinyFontSize, isBold, isItalic,
                    text, fontColor, backgroundColor)
            }
        }

        GFX.drawRect(getXAt(GFX.editorTime).roundToInt(), y0 + 2, 1, y1-y0-4, accentColor)

    }

    fun drawValueAxis(x0: Int, y0: Int, x1: Int, y1: Int){

        val minValue = centralValue - dvHalfHeight
        val maxValue = centralValue + dvHalfHeight

        val deltaValue = 2 * dvHalfHeight
        val valueStep = getValueStep(deltaValue * 0.2f)

        val minStepIndex = (minValue / valueStep).toInt() - 1
        val maxStepIndex = (maxValue / valueStep).toInt() + 1

        for(stepIndex in maxStepIndex downTo minStepIndex){
            val value = stepIndex * valueStep
            val y = getYAt(value).roundToInt()
            if(y > y0+1 && y+2 < y1){
                val text = getValueString(value, valueStep)
                val size = GFX.getTextSize(fontName, tinyFontSize, isBold, isItalic, text)
                val h = size.second
                GFX.drawRect(x0 + size.first + 2, y, x1-x0-size.first, 1, fontColor and 0x3fffffff)
                GFX.drawText(x0 + 2, y - h/2, fontName, tinyFontSize, isBold, isItalic,
                    text, fontColor, backgroundColor)
            }
        }

    }

    override fun draw(x0: Int, y0: Int, x1: Int, y1: Int) {
        super.draw(x0, y0, x1, y1)

        val targetUnitScale = GFX.selectedProperty?.type?.unitScale ?: lastUnitScale
        if(lastUnitScale != targetUnitScale){
            val scale = targetUnitScale / lastUnitScale
            centralValue *= scale
            dvHalfHeight *= scale
            lastUnitScale = targetUnitScale
            clampValues()
        }

        drawValueAxis(x0, y0, x1, y1)
        drawTimeAxis(x0, y0, x1, y1)

        val property = GFX.selectedProperty ?: return

        val type = property.type
        val halfSize = dotSize/2

        val blueish = 0x7799ff
        val red = 0xff0000
        val green = 0x00ff00
        val blue = 0x0000ff

        val valueColors = intArrayOf(
            red, green, blue, white
        )

        when(type){
            AnimatedProperty.Type.FLOAT -> {
                valueColors[0] = blueish
            }
            else -> {}
        }

        for(i in 0 until 4){
            valueColors[i] = (valueColors[i] or black) and 0x7fffffff
        }

        val channelCount = property.type.components
        val values = FloatArray(channelCount)

        fun drawDot(x: Int, value: Float, color: Int){
            val y = getYAt(value).roundToInt()
            GFX.drawTexture(x-halfSize, clamp(y-halfSize, y0-1, y1),
                dotSize, dotSize,
                GFX.whiteTexture, color)
        }

        property.keyframes.forEach {
            val keyTime = it.time
            val keyValue = it.value
            val x = getXAt(keyTime).roundToInt()
            // todo represent colors differently? yes.
            when(channelCount){
                1 -> {
                    values[0] = keyValue as Float
                }
                2 -> {
                    val v = keyValue as Vector2f
                    values[0] = v.x
                    values[1] = v.y
                }
                3 -> {
                    val v = keyValue as Vector3f
                    values[0] = v.x
                    values[1] = v.y
                    values[2] = v.z
                }
                4 -> {
                    val v = keyValue as Vector4f
                    values[0] = v.x
                    values[1] = v.y
                    values[2] = v.z
                    values[3] = v.w
                }
                else -> {}
            }

            for(i in 0 until channelCount){
                drawDot(x, values[i], valueColors[i])
            }

            // GFX.drawRect(x.toInt()-1, y+h/2, 2,2, black or 0xff0000)
        }

        // todo draw all data points <3
        // todo controls:
        // mouse wheel -> left/right, +control = zoom
        // todo double click = add point
        // todo select points
        // todo delete selected points
        // todo copy paste timeline pieces?
        // todo select multiple points by area -> via time?

    }

    fun jumpToX(x: Float){
        GFX.editorTime = getTimeAt(x)
    }

    fun Int.isChannelActive() = (this and activeChannels) != 0

    fun getKeyframeAt(x: Float, y: Float): Pair<Keyframe<*>, Int>? {
        val property = GFX.selectedProperty ?: return null
        var bestDragged: Keyframe<*>? = null
        var bestChannel = 0
        val maxMargin = dotSize*2f/3f + 1f
        var bestDistance = maxMargin
        property.keyframes.forEach { keyframe ->
            val dx = x - getXAt(keyframe.time)
            if(abs(dx) < maxMargin){// todo get best distance instead of this? yes :)
                for(channel in 0 until property.type.components){
                    if(channel.isChannelActive()){
                        val dy = y - getYAt(keyframe.getValue(channel))
                        if(abs(dy) < maxMargin){
                            val distance = length(dx, dy)
                            if(distance < bestDistance){
                                bestDragged = keyframe
                                bestChannel = channel
                                bestDistance = distance
                            }
                        }
                    }
                }
            }
        }
        return bestDragged?.to(bestChannel)
    }

    // todo add/remove keyframes from the selection
    val selectedKeyframes = HashSet<Keyframe<*>>()

    var isSelecting = false
    val select0 = Vector2f()

    var activeChannels = -1

    override fun onMouseDown(x: Float, y: Float, button: Int) {
        // find the dragged element
        draggedKeyframe = null
        if(button == 0){
            isSelecting = isShiftDown
            val keyframeChannel = getKeyframeAt(x, y)
            if(keyframeChannel != null){
                val (keyframe, channel) = keyframeChannel
                // todo only work on one channel, vs working on all?
                // todo this would allow us to copy only z for example
                draggedKeyframe = keyframe
                draggedChannel = channel
                if(isSelecting){
                    if(!selectedKeyframes.remove(keyframe)){
                        selectedKeyframes.add(keyframe) // was not found -> add it
                    }
                }
            } else {
                select0.x = x
                select0.y = y
            }
        }
    }

    // todo scale a group of selected keyframes
    // todo move a group of selected keyframes
    // todo select full keyframes, or partial keyframes?
    fun getAllKeyframes(minX: Float, maxX: Float, minY: Float, maxY: Float): List<Keyframe<*>> {
        if(minX > maxX || minY > maxY) return getAllKeyframes(min(minX, maxX), max(minX, maxX), min(minY, maxY), max(minY, maxY))
        val property = GFX.selectedProperty ?: return emptyList()
        val keyframes = ArrayList<Keyframe<*>>()
        keyframes@for(keyframe in property.keyframes){
            if(getXAt(keyframe.time) in minX .. maxX){
                for(channel in 0 until property.type.components){
                    if(channel.isChannelActive()){
                        if(getYAt(keyframe.getValue(channel)) in minY .. maxY){
                            keyframes += keyframe
                            continue@keyframes
                        }
                    }
                }
            }
        }
        return keyframes
    }

    // todo always show the other properties, too???
    override fun onMouseUp(x: Float, y: Float, button: Int) {
        draggedKeyframe = null
        if(isSelecting){
            // add all keyframes in that area
            selectedKeyframes += getAllKeyframes(select0.x, x, select0.y, y)
        }
    }

    override fun onDeleteKey(x: Float, y: Float) {
        val kf = getKeyframeAt(x, y)
        kf?.apply {
            GFX.selectedProperty?.remove(kf.first)
        }
    }

    override fun onKeyTyped(x: Float, y: Float, key: Int) {
        when(key){
            GLFW_KEY_LEFT -> moveRight(-1f)
            GLFW_KEY_RIGHT -> moveRight(1f)
            GLFW_KEY_UP -> moveUp(1f)
            GLFW_KEY_DOWN -> moveUp(-1f)
            else -> super.onKeyTyped(x, y, key)
        }
    }

    val movementSpeed get() = 0.05f * sqrt(w*h.toFloat())

    fun moveRight(sign: Float){
        val delta = sign * dtHalfLength * movementSpeed / w
        GFX.editorTime += delta
        centralTime += delta
        clampTime()
    }

    fun moveUp(sign: Float){
        val delta = sign * dvHalfHeight * movementSpeed / h
        centralValue += delta
        clampTime()
    }

    override fun onGotAction(x: Float, y: Float, dx: Float, dy: Float, action: String, isContinuous: Boolean): Boolean {
        println(action)
        when(action){
            "MoveLeft" -> moveRight(-1f)
            "MoveRight" -> moveRight(1f)
            "MoveUp" -> moveUp(1f)
            "MoveDown" -> moveUp(-1f)
            else -> return super.onGotAction(x, y, dx, dy, action, isContinuous)
        }
        return true
    }

    override fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float) {
        GFX.editorHoverTime = getTimeAt(x)
        val draggedKeyframe = draggedKeyframe
        if(draggedKeyframe != null){
            val time = getTimeAt(x)
            draggedKeyframe.time = time
            GFX.editorTime = time
            draggedKeyframe.setValue(draggedChannel, getValueAt(y))
            GFX.selectedProperty?.sort()
        } else {
            if(mouseKeysDown.isNotEmpty()){
                centralTime -= dx * dtHalfLength / (w/2)
                centralValue += dy * dvHalfHeight / (h/2)
                clampTime()
                clampValues()
            }
        }
    }

    override fun onDoubleClick(x: Float, y: Float, button: Int) {
        val property = GFX.selectedProperty
        property?.apply {
            property.addKeyframe(getTimeAt(x), property.type.defaultValue, 0.01f)
        } ?: println("Please select a property first!")
    }

    override fun onMouseClicked(x: Float, y: Float, button: Int, long: Boolean) {
        jumpToX(x)
    }

    fun clampTime(){
        dtHalfLength = clamp(dtHalfLength, 2f / targetFPS, timeFractions.last())
        centralTime = max(centralTime, dtHalfLength)
    }

    fun clampValues(){
        dvHalfHeight = clamp(dvHalfHeight, 0.001f * lastUnitScale, 1000f * lastUnitScale)
    }

    override fun onMouseWheel(x: Float, y: Float, dx: Float, dy: Float) {
        val delta = dx-dy
        val scale = pow(1.05f, delta)
        if(isShiftDown){
            if(isControlDown){ // zoom
                // set the center to the cursor
                // works great :D
                val normalizedY = (h/2-y)/(h/2)
                centralValue += normalizedY * dvHalfHeight * (1f - scale)
                dvHalfHeight *= scale
            } else { // move
                centralValue += dvHalfHeight * 20f * delta / h
            }
            clampValues()
        } else {
            if(isControlDown){ // zoom
                // set the center to the cursor
                // works great :D
                val normalizedX = (x-w/2)/(w/2)
                centralTime += normalizedX * dtHalfLength * (1f - scale)
                dtHalfLength *= scale
            } else { // move
                centralTime += dtHalfLength * 20f * delta / w
            }
            clampTime()
        }
    }

    override fun getClassName() = "GraphEditorBody"

}