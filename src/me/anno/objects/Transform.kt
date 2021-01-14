package me.anno.objects

import me.anno.config.DefaultConfig
import me.anno.gpu.GFX
import me.anno.gpu.GFX.glThread
import me.anno.gpu.GFX.isFinalRendering
import me.anno.gpu.GFX.toRadians
import me.anno.gpu.GFXx3D.draw3DCircle
import me.anno.gpu.blending.BlendDepth
import me.anno.gpu.blending.BlendMode
import me.anno.gpu.blending.blendModes
import me.anno.gpu.shader.ShaderPlus
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.Filtering
import me.anno.io.ISaveable
import me.anno.io.Saveable
import me.anno.io.base.BaseWriter
import me.anno.io.text.TextReader
import me.anno.io.text.TextWriter
import me.anno.language.Language
import me.anno.language.translation.Dict
import me.anno.language.translation.NameDesc
import me.anno.objects.animation.AnimatedProperty
import me.anno.objects.animation.Type
import me.anno.objects.effects.MaskType
import me.anno.objects.effects.ToneMappers
import me.anno.objects.inspectable.Inspectable
import me.anno.objects.modes.ArraySelectionMode
import me.anno.objects.modes.LoopingState
import me.anno.objects.modes.TransformVisibility
import me.anno.objects.modes.UVProjection
import me.anno.objects.particles.ParticleSystem
import me.anno.objects.text.TextRenderMode
import me.anno.studio.rems.RemsStudio
import me.anno.studio.rems.RemsStudio.editorTime
import me.anno.studio.rems.RemsStudio.root
import me.anno.studio.rems.Scene
import me.anno.studio.rems.Selection.select
import me.anno.studio.rems.Selection.selectTransform
import me.anno.studio.rems.Selection.selectedTransform
import me.anno.ui.base.Panel
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.editor.TimelinePanel
import me.anno.ui.editor.TimelinePanel.Companion.global2Kf
import me.anno.ui.editor.stacked.Option
import me.anno.ui.input.*
import me.anno.ui.style.Style
import me.anno.utils.Color.toHexColor
import me.anno.utils.MatrixHelper.skew
import me.anno.utils.structures.ValueWithDefault
import me.anno.utils.structures.ValueWithDefault.Companion.writeMaybe
import me.anno.utils.structures.ValueWithDefaultFunc
import org.apache.logging.log4j.LogManager
import org.joml.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

// pivot? nah, always use the center to make things easy;
// or should we do it?... idk for sure...
// just make the tree work perfectly <3

// gradients? -> can be done using the mask layer
// done select by clicking

// todo option to copy css compliant rgba colors?

open class Transform(var parent: Transform? = null) : Saveable(),
    Inspectable {

    // todo generally "play" the animation of a single transform for testing purposes?
    // todo maybe only for video or audio? for audio it would be simple :)
    // useful for audio, video, particle systems, generally animations
    // only available if the rest is stopped? yes.

    init {
        parent?.addChild(this)
    }

    val clickId = nextClickId.incrementAndGet()

    val timelineSlot = ValueWithDefault(-1)

    var visibility = TransformVisibility.VISIBLE
    var uuid = nextUUID.incrementAndGet()

    var position = AnimatedProperty.pos()
    var scale = AnimatedProperty.scale()
    var rotationYXZ = AnimatedProperty.rotYXZ()

    var skew = AnimatedProperty.skew()
    var alignWithCamera = AnimatedProperty.float01()
    var color = AnimatedProperty.color()
    var colorMultiplier = AnimatedProperty.floatPlus(1f)

    var blendMode = BlendMode.UNSPECIFIED

    var timeOffset = 0.0
    var timeDilation = 1.0

    var timeAnimated = AnimatedProperty.double()

    private val nameI = ValueWithDefaultFunc { getDefaultDisplayName() }
    var name: String
        get() = nameI.value
        set(value) = nameI.set(value)

    var comment = ""

    open fun getSymbol() = DefaultConfig["ui.symbol.folder", "\uD83D\uDCC1"]
    open fun getDefaultDisplayName() =
        if (getClassName() == "Transform") Dict["Folder", "obj.folder"] else getClassName()

    open fun isVisible(localTime: Double) = true

    val rightPointingTriangle = "▶"
    val bottomPointingTriangle = "▼"
    val folder = "\uD83D\uDCC1"

    val children = ArrayList<Transform>()
    private val isCollapsedI = ValueWithDefault(false)
    var isCollapsed: Boolean
        get() = isCollapsedI.value
        set(value) = isCollapsedI.set(value)

    var lastLocalColor = Vector4f()
    var lastLocalTime = 0.0

    private val weightI = ValueWithDefault(1f)
    var weight: Float
        get() = weightI.value
        set(value) = weightI.set(value)

    fun putValue(list: AnimatedProperty<*>, value: Any, updateHistory: Boolean) {
        val time = global2Kf(editorTime)
        if (updateHistory) {
            RemsStudio.incrementalChange("Change Keyframe Value") {
                list.addKeyframe(time, value, TimelinePanel.propertyDt)
            }
        } else {
            list.addKeyframe(time, value, TimelinePanel.propertyDt)
        }
    }

    fun setChildAt(child: Transform, index: Int) {
        if (this in child.listOfAll) throw RuntimeException()
        if (index >= children.size) {
            children.add(child)
        } else children[index] = child
        child.parent = this
    }

    fun show(anim: AnimatedProperty<*>?) {
        select(this, anim)
    }

    open fun claimResources(pTime0: Double, pTime1: Double, pAlpha0: Float, pAlpha1: Float) {
        val lTime0 = getLocalTime(pTime0)
        val lAlpha0 = getLocalColor(Vector4f(0f, 0f, 0f, pAlpha0), lTime0).w
        val lTime1 = getLocalTime(pTime1)
        val lAlpha1 = getLocalColor(Vector4f(0f, 0f, 0f, pAlpha1), lTime0).w
        if (lAlpha0 > minAlpha || lAlpha1 > minAlpha) {
            claimLocalResources(lTime0, lTime1)
            children.forEach {
                it.claimResources(lTime0, lTime1, lAlpha0, lAlpha1)
            }
        }
    }

    open fun claimLocalResources(lTime0: Double, lTime1: Double) {
        // here is nothing to claim
        // only for things using video textures
    }

    override fun createInspector(
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {

        list += TextInput("Name (${getClassName()})", style, name)
            .setChangeListener { name = if (it.isEmpty()) "-" else it }
            .setIsSelectedListener { show(null) }
        list += TextInputML("Comment", style, comment)
            .setChangeListener { comment = it }
            .setIsSelectedListener { show(null) }

        // transforms
        val transform = getGroup("Transform", "Translation Scale, Rotation, Skewing", "transform")
        transform += vi("Position", "Location of this object", position, style)
        transform += vi("Scale", "Makes it bigger/smaller", scale, style)
        transform += vi("Rotation (YXZ)", "", rotationYXZ, style)
        transform += vi("Skew", "Transform it similar to a shear", skew, style)
        transform += vi(
            "Alignment with Camera", "0 = in 3D, 1 = looking towards the camera; billboards",
            alignWithCamera, style
        )

        // color
        val colorGroup = getGroup("Color", "", "color")
        colorGroup += vi("Color", "Tint, applied to this & children", color, style)
        colorGroup += vi("Color Multiplier", "To make things brighter than usually possible", colorMultiplier, style)

        // kind of color...
        colorGroup += vi("Blend Mode", "", null, blendMode, style) { blendMode = it }

        // time
        val timeGroup = getGroup("Time", "", "time")
        timeGroup += vi("Start Time", "Delay the animation", null, timeOffset, style) { timeOffset = it }
        timeGroup += vi("Time Multiplier", "Speed up the animation", null, timeDilation, style) { timeDilation = it }
        timeGroup += vi("Advanced Time", "Add acceleration/deceleration to your elements", timeAnimated, style)


        // todo automatically extend timeline panel or restrict moving it down

        val editorGroup = getGroup("Editor", "", "editor")
        editorGroup += vi(
            "Timeline Slot", "< 1 means invisible", Type.INT_PLUS, timelineSlot.value, style
        ) { timelineSlot.value = it }
        // todo warn of invisible elements somehow!...
        editorGroup += vi("Visibility", "", null, visibility, style) { visibility = it }

        if (parent?.acceptsWeight() == true) {
            val psGroup = getGroup("Particle System Child", "", "particles")
            psGroup += vi("Weight", "For particle systems", Type.FLOAT_PLUS, weight, style) {
                weight = it
                (parent as? ParticleSystem)?.apply {
                    if (children.size > 1) clearCache()
                }
            }
        }

    }

    open fun getLocalTime(parentTime: Double): Double {
        var localTime0 = (parentTime - timeOffset) * timeDilation
        localTime0 += timeAnimated[localTime0]
        return localTime0
    }

    fun getLocalColor(): Vector4f = getLocalColor(parent?.getLocalColor() ?: Vector4f(1f, 1f, 1f, 1f), lastLocalTime)
    fun getLocalColor(parentColor: Vector4f, localTime: Double): Vector4f {
        val col = color.getValueAt(localTime)
        val mul = colorMultiplier[localTime]
        return Vector4f(col).mul(parentColor).mul(mul, mul, mul, 1f)
    }

    fun applyTransformLT(transform: Matrix4f, time: Double) {

        val position = position[time]
        val scale = scale[time]
        val euler = rotationYXZ[time]
        val skew = skew[time]
        val alignWithCamera = alignWithCamera[time]

        if (position.x != 0f || position.y != 0f || position.z != 0f) {
            transform.translate(position)
        }

        if (euler.y != 0f) transform.rotate(toRadians(euler.y), yAxis)
        if (euler.x != 0f) transform.rotate(toRadians(euler.x), xAxis)
        if (euler.z != 0f) transform.rotate(toRadians(euler.z), zAxis)

        if (scale.x != 1f || scale.y != 1f || scale.z != 1f) transform.scale(scale)

        if (skew.x != 0f || skew.y != 0f) transform.skew(skew.x, skew.y)

        if (alignWithCamera != 0f) {
            transform.alignWithCamera(alignWithCamera)
        }


    }

    fun Matrix4f.alignWithCamera(alignWithCamera: Float) {
        // lerp rotation instead of full transform?
        if (alignWithCamera != 0f) {
            val local = Scene.lGCTInverted
            val up = local.transformDirection(Vector3f(0f, 1f, 0f))
            val forward = local.transformDirection(Vector3f(0f, 0f, -1f))
            if (alignWithCamera == 1f) {
                lookAlong(forward, up)
            } else {
                lerp(Matrix4f(this).lookAlong(forward, up), alignWithCamera)
            }
        }
    }

    fun applyTransformPT(transform: Matrix4f, parentTime: Double) =
        applyTransformLT(transform, getLocalTime(parentTime))

    /**
     * stack with camera already included
     * */
    fun draw(stack: Matrix4fArrayList, parentTime: Double, parentColor: Vector4f) {

        val time = getLocalTime(parentTime)
        val color = getLocalColor(parentColor, time)

        if (color.w > minAlpha && visibility.isVisible) {
            applyTransformLT(stack, time)
            GFX.drawnTransform = this
            val doBlending = when (GFX.drawMode) {
                ShaderPlus.DrawMode.COLOR_SQUARED, ShaderPlus.DrawMode.COLOR -> true
                else -> false
            }
            if (doBlending) {
                BlendDepth(blendMode, GFX.currentCamera.useDepth) {
                    onDraw(stack, time, color)
                    drawChildren(stack, time, color, parentColor)
                }
            } else {
                onDraw(stack, time, color)
                drawChildren(stack, time, color, parentColor)
            }
        }

    }

    fun drawChildren(stack: Matrix4fArrayList, time: Double, color: Vector4f, parentColor: Vector4f) {
        val passesOnColor = passesOnColor()
        val childColor = if (passesOnColor) color else parentColor
        if (drawChildrenAutomatically()) {
            drawChildren(stack, time, childColor)
        }
    }

    open fun drawChildrenAutomatically() = true

    fun drawChildren(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        children.forEach { child ->
            drawChild(stack, time, color, child)
        }
    }

    fun drawChild(stack: Matrix4fArrayList, time: Double, color: Vector4f, child: Transform?) {
        if (child != null) {
            // if(child is MaskLayer) println("0 ${GFX.drawMode}")
            stack.pushMatrix()
            child.draw(stack, time, color)
            stack.popMatrix()
            // if(child is MaskLayer) println("1 ${GFX.drawMode}")
        }
    }

    fun drawUICircle(stack: Matrix4fArrayList, scale: Float, inner: Float, color: Vector4f) {
        // draw a small symbol to indicate pivot
        if (!isFinalRendering) {
            stack.pushMatrix()
            if (scale != 1f) stack.scale(scale)
            stack.alignWithCamera(1f)
            draw3DCircle(null, 0.0, stack, inner, 0f, 360f, color)
            stack.popMatrix()
        }
    }

    open fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        drawUICircle(stack, 0.02f, 0.7f, color)
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        // many properties are only written if they changed;
        // to reduce file sizes
        writer.writeObject(this, "parent", parent)
        writer.writeMaybe(this, "name", nameI)
        writer.writeString("comment", comment)
        writer.writeMaybe(this, "collapsed", isCollapsedI)
        writer.writeMaybe(this, "weight", weightI)
        writer.writeObject(this, "position", position)
        writer.writeObject(this, "scale", scale)
        writer.writeObject(this, "rotationYXZ", rotationYXZ)
        writer.writeObject(this, "skew", skew)
        writer.writeObject(this, "alignWithCamera", alignWithCamera)
        writer.writeDouble("timeOffset", timeOffset)
        if (timeDilation != 1.0) writer.writeDouble("timeDilation", timeDilation, true)
        writer.writeObject(this, "timeAnimated", timeAnimated)
        writer.writeObject(this, "color", color)
        writer.writeObject(this, "colorMultiplier", colorMultiplier)
        if (blendMode !== BlendMode.UNSPECIFIED) writer.writeString("blendMode", blendMode.id)
        writer.writeObjectList(this, "children", children)
        writer.writeMaybe(this, "timelineSlot", timelineSlot)
        writer.writeInt("visibility", visibility.id, false)
        writer.writeLong("uuid", uuid, true)
    }

    override fun readBoolean(name: String, value: Boolean) {
        when (name) {
            "collapsed" -> isCollapsed = value
            else -> super.readBoolean(name, value)
        }
    }

    override fun readInt(name: String, value: Int) {
        when (name) {
            "timelineSlot" -> timelineSlot.value = value
            "visibility" -> visibility = TransformVisibility[value]
            else -> super.readInt(name, value)
        }
    }

    override fun readLong(name: String, value: Long) {
        when (name) {
            "uuid" -> uuid = value
            else -> super.readLong(name, value)
        }
    }

    override fun readFloat(name: String, value: Float) {
        when (name) {
            "weight" -> weight = value
            else -> super.readFloat(name, value)
        }
    }

    override fun readDouble(name: String, value: Double) {
        when (name) {
            "timeDilation" -> timeDilation = value
            "timeOffset" -> timeOffset = value
            else -> super.readDouble(name, value)
        }
    }

    override fun readString(name: String, value: String) {
        when (name) {
            "name" -> this.name = value
            "comment" -> comment = value
            "blendMode" -> blendMode = BlendMode[value]
            else -> super.readString(name, value)
        }
    }

    override fun readObject(name: String, value: ISaveable?) {
        when (name) {
            "parent" -> {
                if (value is Transform) {
                    try {
                        value.addChild(this)
                    } catch (e: RuntimeException) {
                        LOGGER.warn(e.message.toString())
                    }
                }
            }
            "children" -> {
                if (value is Transform) {
                    addChild(value)
                }
            }
            "position" -> position.copyFrom(value)
            "scale" -> scale.copyFrom(value)
            "rotationYXZ" -> rotationYXZ.copyFrom(value)
            "skew" -> skew.copyFrom(value)
            "alignWithCamera" -> alignWithCamera.copyFrom(value)
            "timeAnimated" -> timeAnimated.copyFrom(value)
            "color" -> color.copyFrom(value)
            "colorMultiplier" -> colorMultiplier.copyFrom(value)
            else -> super.readObject(name, value)
        }
    }

    fun contains(t: Transform): Boolean {
        if (t === this) return true
        if (children != null) {// can be null on init
            for (child in children) {
                if (child === t || child.contains(t)) return true
            }
        }
        return false
    }

    override fun getClassName(): String = "Transform"
    override fun getApproxSize(): Int = 50 + listOfAll.count()

    fun addBefore(child: Transform) {
        val p = parent!!
        val index = p.children.indexOf(this)
        p.children.add(index, child)
        child.parent = p
    }

    fun addAfter(child: Transform) {
        val p = parent!!
        val index = p.children.indexOf(this)
        p.children.add(index + 1, child)
        child.parent = p
    }

    fun addChild(child: Transform) {
        if (
            glThread != null &&
            Thread.currentThread() != glThread &&
            this in root.listOfAll
        ) throw RuntimeException("Called from wrong thread!")
        if (child.contains(this)) throw RuntimeException("this cannot contain its parent!")
        child.parent?.removeChild(child)
        child.parent = this
        children += child
    }

    fun removeChild(child: Transform) {
        child.parent = null
        children.remove(child)
    }

    fun stringify(): String {
        val myParent = parent
        parent = null
        val data = TextWriter.toText(this, false)
        parent = myParent
        return data
    }

    fun setName(name: String): Transform {
        this.name = name
        return this
    }

    fun getGlobalTransform(globalTime: Double): Pair<Matrix4f, Double> {
        val (parentTransform, parentTime) = parent?.getGlobalTransform(globalTime) ?: Matrix4f() to globalTime
        val localTime = getLocalTime(parentTime)
        applyTransformLT(parentTransform, localTime)
        return parentTransform to localTime
    }

    fun removeFromParent() {
        parent?.removeChild(this)
        parent = null
    }

    override fun isDefaultValue() = false

    fun clone() = TextWriter.toText(this, false).toTransform()
    open fun acceptsWeight() = false
    open fun passesOnColor() = true

    fun <V> vi(
        title: String, ttt: String, dictPath: String,
        type: Type?, value: V,
        style: Style, setValue: (V) -> Unit
    ): Panel {
        return vi(Dict[title, "obj.$dictPath"], Dict[ttt, "obj.$dictPath.desc"], type, value, style, setValue)
    }

    /**
     * creates a panel with the correct input for the type, and sets the default values:
     * title, tool tip text, type, start value
     * callback is used to adjust the value
     * */
    @Suppress("UNCHECKED_CAST") // all casts are checked in all known use-cases ;)
    fun <V> vi(
        title: String, ttt: String,
        type: Type?, value: V,
        style: Style, setValue: (V) -> Unit
    ): Panel {
        return when (value) {
            is Boolean -> BooleanInput(title, value, style)
                .setChangeListener {
                    RemsStudio.largeChange("Set $title to $it") {
                        setValue(it as V)
                    }
                }
                .setIsSelectedListener { show(null) }
                .setTooltip(ttt)
            is Int -> IntInput(title, value, type ?: Type.INT, style)
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to $it", title) {
                        setValue(it.toInt() as V)
                    }
                }
                .setIsSelectedListener { show(null) }
                .setTooltip(ttt)
            is Long -> IntInput(title, value, type ?: Type.LONG, style)
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to $it", title) {
                        setValue(it as V)
                    }
                }
                .setIsSelectedListener { show(null) }
                .setTooltip(ttt)
            is Float -> FloatInput(title, value, type ?: Type.FLOAT, style)
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to $it", title) {
                        setValue(it.toFloat() as V)
                    }
                }
                .setIsSelectedListener { show(null) }
                .setTooltip(ttt)
            is Double -> FloatInput(title, value, type ?: Type.DOUBLE, style)
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to $it", title) {
                        setValue(it as V)
                    }
                }
                .setIsSelectedListener { show(null) }
                .setTooltip(ttt)
            is Vector2f -> VectorInput(style, title, value, type ?: Type.VEC2)
                .setChangeListener { x, y, _, _ ->
                    RemsStudio.incrementalChange("Set $title to ($x,$y)", title) {
                        setValue(Vector2f(x, y) as V)
                    }
                }
                .setIsSelectedListener { show(null) }
                .setTooltip(ttt)
            is Vector3f ->
                if (type == Type.COLOR3) {
                    ColorInput(style, title, Vector4f(value, 1f), false, null)
                        .setChangeListener { r, g, b, _ ->
                            RemsStudio.incrementalChange("Set $title to ${Vector3f(r, g, b).toHexColor()}", title) {
                                setValue(Vector3f(r, g, b) as V)
                            }
                        }
                        .setIsSelectedListener { show(null) }
                        .setTooltip(ttt)
                } else {
                    VectorInput(style, title, value, type ?: Type.VEC3)
                        .setChangeListener { x, y, z, _ ->
                            RemsStudio.incrementalChange("Set $title to ($x,$y,$z)", title) {
                                setValue(Vector3f(x, y, z) as V)
                            }
                        }
                        .setIsSelectedListener { show(null) }
                        .setTooltip(ttt)
                }
            is Vector4f -> {
                if (type == null || type == Type.COLOR) {
                    ColorInput(style, title, value, true, null)
                        .setChangeListener { r, g, b, a ->
                            RemsStudio.incrementalChange("Set $title to ${Vector4f(r, g, b, a).toHexColor()}", title) {
                                setValue(Vector4f(r, g, b, a) as V)
                            }
                        }
                        .setIsSelectedListener { show(null) }
                        .setTooltip(ttt)
                } else {
                    VectorInput(style, title, value, type)
                        .setChangeListener { x, y, z, w ->
                            RemsStudio.incrementalChange("Set $title to ($x,$y,$z,$w)", title) {
                                setValue(Vector4f(x, y, z, w) as V)
                            }
                        }
                        .setIsSelectedListener { show(null) }
                        .setTooltip(ttt)
                }
            }
            is Quaternionf -> VectorInput(style, title, value, type ?: Type.QUATERNION)
                .setChangeListener { x, y, z, w ->
                    RemsStudio.incrementalChange(title) {
                        setValue(Quaternionf(x, y, z, w) as V)
                    }
                }
                .setIsSelectedListener { show(null) }
                .setTooltip(ttt)
            is String -> TextInput(title, style, value)
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to \"$it\"", title) {
                        setValue(it as V)
                    }
                }
                .setIsSelectedListener { show(null) }
                .setTooltip(ttt)
            is File -> FileInput(title, style, value)
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to \"$it\"", title) {
                        setValue(it as V)
                    }
                }
                .setIsSelectedListener { show(null) }
                .setTooltip(ttt)
            is BlendMode -> {
                val values = blendModes.values
                val valueNames = values.map { it to it.naming }
                EnumInput(
                    title, true, valueNames.first { it.first == value }.second.name,
                    valueNames.map { it.second }, style
                )
                    .setChangeListener { name, index, _ ->
                        RemsStudio.incrementalChange("Set $title to $name", title) {
                            setValue(valueNames[index].first as V)
                        }
                    }
                    .setIsSelectedListener { show(null) }
                    .setTooltip(ttt)
            }
            is Enum<*> -> {
                val values = when (value) {
                    is LoopingState -> LoopingState.values()
                    is ToneMappers -> ToneMappers.values()
                    is MaskType -> MaskType.values()
                    is Filtering -> Filtering.values()
                    is ArraySelectionMode -> ArraySelectionMode.values()
                    is UVProjection -> UVProjection.values()
                    is Clamping -> Clamping.values()
                    is TransformVisibility -> TransformVisibility.values()
                    is TextRenderMode -> TextRenderMode.values()
                    is Language -> Language.values()
                    else -> throw RuntimeException("Missing enum .values() implementation for UI in Transform.kt for $value")
                }
                val valueNames: List<Pair<Any, NameDesc>> = values.map {
                    it to when (it) {
                        is LoopingState -> it.naming
                        is ToneMappers -> it.naming
                        is MaskType -> it.nameing
                        is Filtering -> it.naming
                        is ArraySelectionMode -> it.naming
                        is UVProjection -> it.naming
                        is Clamping -> it.naming
                        is TransformVisibility -> it.naming
                        is TextRenderMode -> it.naming
                        is Language -> it.naming
                        else -> NameDesc(it.name, "", "")
                    }
                }
                EnumInput(
                    title, true, valueNames.first { it.first == value }.second.name,
                    valueNames.map { it.second }, style
                )
                    .setChangeListener { name, index, _ ->
                        RemsStudio.incrementalChange("Set $title to $name") {
                            setValue(values[index] as V)
                        }
                    }
                    .setIsSelectedListener { show(null) }
                    .setTooltip(ttt)
            }
            else -> throw RuntimeException("Type $value not yet implemented!")
        }
    }

    fun vi(title: String, ttt: String, dictSubPath: String, values: AnimatedProperty<*>, style: Style): Panel {
        return vi(Dict[title, "obj.$dictSubPath"], Dict[ttt, "obj.$dictSubPath.desc"], values, style)
    }

    /**
     * creates a panel with the correct input for the type, and sets the default values:
     * title, tool tip text, type, start value
     * modifies the AnimatedProperty-Object, so no callback is needed
     * */
    fun vi(title: String, ttt: String, values: AnimatedProperty<*>, style: Style): Panel {
        val time = lastLocalTime
        return when (val value = values[time]) {
            is Int -> IntInput(title, values, 0, time, style)
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to $it", title) {
                        putValue(values, it.toInt(), false)
                    }
                }
                .setIsSelectedListener { show(values) }
                .setTooltip(ttt)
            is Long -> IntInput(title, values, 0, time, style)
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to $it", title) {
                        putValue(values, it, false)
                    }
                }
                .setIsSelectedListener { show(values) }
                .setTooltip(ttt)
            is Float -> FloatInput(title, values, 0, time, style)
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to $it", title) {
                        putValue(values, it.toFloat(), false)
                    }
                }
                .setIsSelectedListener { show(values) }
                .setTooltip(ttt)
            is Double -> FloatInput(title, values, 0, time, style)
                .setChangeListener {
                    RemsStudio.incrementalChange("Set $title to $it", title) {
                        putValue(values, it, false)
                    }
                }
                .setIsSelectedListener { show(values) }
                .setTooltip(ttt)
            is Vector2f -> VectorInput(title, values, time, style)
                .setChangeListener { x, y, _, _ ->
                    RemsStudio.incrementalChange("Set $title to ($x,$y)", title) {
                        putValue(values, Vector2f(x, y), false)
                    }
                }
                .setIsSelectedListener { show(values) }
                .setTooltip(ttt)
            is Vector3f ->
                if (values.type == Type.COLOR3) {
                    ColorInput(style, title, Vector4f(value, 1f), false, values)
                        .setChangeListener { r, g, b, _ ->
                            RemsStudio.incrementalChange("Set $title to ${Vector3f(r, g, b).toHexColor()}", title) {
                                putValue(values, Vector3f(r, g, b), false)
                            }
                        }
                        .setIsSelectedListener { show(values) }
                        .setTooltip(ttt)
                } else {
                    VectorInput(title, values, time, style)
                        .setChangeListener { x, y, z, _ ->
                            RemsStudio.incrementalChange("Set $title to ($x,$y,$z)", title) {
                                putValue(values, Vector3f(x, y, z), false)
                            }
                        }
                        .setIsSelectedListener { show(values) }
                        .setTooltip(ttt)
                }
            is Vector4f -> {
                if (values.type == Type.COLOR) {
                    ColorInput(style, title, value, true, values)
                        .setChangeListener { r, g, b, a ->
                            RemsStudio.incrementalChange("Set $title to ${Vector4f(r, g, b, a).toHexColor()}", title) {
                                putValue(values, Vector4f(r, g, b, a), false)
                            }
                        }
                        .setIsSelectedListener { show(values) }
                        .setTooltip(ttt)
                } else {
                    VectorInput(title, values, time, style)
                        .setChangeListener { x, y, z, w ->
                            RemsStudio.incrementalChange("Set $title to ($x,$y,$z,$w)", title) {
                                putValue(values, Vector4f(x, y, z, w), false)
                            }
                        }
                        .setIsSelectedListener { show(values) }
                        .setTooltip(ttt)
                }
            }
            is Quaternionf -> VectorInput(title, values, time, style)
                .setChangeListener { x, y, z, w ->
                    RemsStudio.incrementalChange("Set $title to ($x,$y,$z,$w)", title) {
                        putValue(values, Quaternionf(x, y, z, w), false)
                    }
                }
                .setIsSelectedListener { show(values) }
                .setTooltip(ttt)
            else -> throw RuntimeException("Type $value not yet implemented!")
        }
    }

    open fun onDestroy() {}
    open fun destroy() {
        if (selectedTransform === this) {
            selectTransform(null)
        }
        removeFromParent()
        onDestroy()
    }

    val listOfAll: Sequence<Transform>
        get() = sequence {
            yield(this@Transform)
            children.forEach { child ->
                yieldAll(child.listOfAll)
            }
        }

    val listOfInheritance: Sequence<Transform>
        get() = sequence {
            yield(this@Transform)
            val parent = parent
            if (parent != null) {
                yieldAll(parent.listOfInheritance)
            }
        }

    open fun getAdditionalChildrenOptions(): List<Option> = emptyList()

    companion object {
        // these values MUST NOT be changed
        // they are universal constants, and are used
        // within shaders, too
        val xAxis = Vector3f(1f, 0f, 0f)
        val yAxis = Vector3f(0f, 1f, 0f)
        val zAxis = Vector3f(0f, 0f, 1f)
        val nextClickId = AtomicInteger()
        val nextUUID = AtomicLong()
        fun String.toTransform() = TextReader.fromText(this).first() as? Transform
        const val minAlpha = 0.5f / 255f
        private val LOGGER = LogManager.getLogger(Transform::class)
    }


}