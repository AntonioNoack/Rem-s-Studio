package me.anno.objects

import me.anno.gpu.GFX
import me.anno.gpu.GFX.toRadians
import me.anno.io.ISaveable
import me.anno.io.Saveable
import me.anno.io.base.BaseWriter
import me.anno.io.text.TextReader
import me.anno.io.text.TextWriter
import me.anno.utils.clamp
import me.anno.objects.animation.AnimatedProperty
import me.anno.objects.blending.BlendMode
import me.anno.objects.blending.blendModes
import me.anno.objects.particles.ParticleSystem
import me.anno.ui.base.Panel
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.input.*
import me.anno.ui.style.Style
import org.joml.*
import java.lang.RuntimeException
import kotlin.math.max

// pivot? nah, always use the center to make things easy;
// or should we do it?... idk for sure...
// just make the tree work perfectly <3

// todo load 3D meshes :D
// todo gradients?

open class Transform(var parent: Transform? = null): Saveable(){

    init {
        parent?.addChild(this)
    }

    var isVisibleInTimeline = false

    var position = AnimatedProperty.pos()
    var scale = AnimatedProperty.scale()
    var rotationYXZ = AnimatedProperty.rotYXZ()
    var rotationQuaternion: AnimatedProperty<Quaternionf>? = null
    var skew = AnimatedProperty.skew()
    var color = AnimatedProperty.color()
    var colorMultiplier = AnimatedProperty.floatPlus().set(1f)

    var blendMode = BlendMode.UNSPECIFIED

    var timeOffset = 0f
    var timeDilation = 1f

    // todo make this animatable, calculate the integral to get a mapping
    var timeAnimated = AnimatedProperty.float()

    var name = getDefaultDisplayName()
    var comment = "this is a comment\n    with indent and multiple lines"

    open fun getDefaultDisplayName() = if(getClassName() == "Transform") "Folder" else getClassName()

    val rightPointingTriangle = "▶"
    val bottomPointingTriangle = "▼"
    val folder = "\uD83D\uDCC1"

    val children = ArrayList<Transform>()
    var isCollapsed = false

    var lastLocalTime = 0f

    var weight = 1f

    fun putValue(list: AnimatedProperty<*>, value: Any){
        list.addKeyframe(if(list.isAnimated) lastLocalTime else 0f, value, 0.1f)
    }

    val usesEuler get() = rotationQuaternion == null

    fun show(anim: AnimatedProperty<*>?){
        GFX.selectedProperty = anim
    }

    open fun createInspector(list: PanelListY, style: Style){

        // todo update by time :)

        list += TextInput("Name (${getClassName()})", style, name)
            .setChangeListener { name = if(it.isEmpty()) "-" else it }
            .setIsSelectedListener { GFX.selectedProperty = null }
        list += TextInputML("Comment", style, comment)
            .setChangeListener { comment = it }
            .setIsSelectedListener { GFX.selectedProperty = null }

        list += VI("Position", "Location of this object", position, lastLocalTime, style)
        list += VI("Scale", "Makes it bigger/smaller", scale, lastLocalTime, style)

        if(usesEuler){
            list += VectorInput(style, "Rotation (YXZ)", rotationYXZ[lastLocalTime], AnimatedProperty.Type.ROT_YXZ, rotationYXZ)
                .setChangeListener { x, y, z, _ -> putValue(rotationYXZ, Vector3f(x,y,z)) }
                .setIsSelectedListener { show(rotationYXZ) }
        } else {
            list += VectorInput(style, "Rotation (Quaternion)", rotationQuaternion?.get(lastLocalTime) ?: Quaternionf())
                .setChangeListener { x, y, z, w ->
                    if(rotationQuaternion == null) rotationQuaternion = AnimatedProperty.quat()
                    putValue(rotationQuaternion!!, Quaternionf(x,y,z,w+1e-9f).normalize()) }
                .setIsSelectedListener { show(rotationQuaternion) }
        }

        list += VectorInput(style, "Skew", skew[lastLocalTime], AnimatedProperty.Type.SKEW_2D, skew)
            .setChangeListener { x, y, _, _ -> putValue(skew, Vector2f(x,y)) }
            .setIsSelectedListener { show(skew) }
        list += ColorInput(style, "Color", color[lastLocalTime], color)
            .setChangeListener { x, y, z, w -> putValue(color, Vector4f(max(0f, x), max(0f, y), max(0f, z), clamp(w, 0f, 1f))) }
            .setIsSelectedListener { show(color) }
        list += FloatInput("Color Multiplier", colorMultiplier, lastLocalTime, style)
            .setChangeListener { putValue(colorMultiplier, it) }
            .setIsSelectedListener { show(colorMultiplier) }
        list += FloatInput("Start Time", timeOffset, style)
            .setChangeListener { timeOffset = it }
            .setIsSelectedListener { GFX.selectedProperty = null }
        list += FloatInput("Time Multiplier", timeDilation, style)
            .setChangeListener { timeDilation = it }
            .setIsSelectedListener { GFX.selectedProperty = null }
        list += FloatInput("Advanced Time", timeAnimated, lastLocalTime, style)
            .setChangeListener {  x -> putValue(timeAnimated, x) }
            .setIsSelectedListener { show(timeAnimated) }
        list += EnumInput("Blend Mode", true, blendMode.id, blendModes.keys.toList().sorted(), style)
            .setChangeListener { blendMode = BlendMode[it] }
            .setIsSelectedListener { GFX.selectedProperty = null }

        if(parent?.acceptsWeight() == true){
            list += FloatInput("Weight", weight, AnimatedProperty.Type.FLOAT_PLUS, style)
                .setChangeListener {
                    weight = it
                    (parent as? ParticleSystem)?.apply {
                        if(children.size > 1) clearCache()
                    }
                }
                .setIsSelectedListener { show(null) }
        }
        list += BooleanInput("Visible In Timeline?", isVisibleInTimeline, style)
            .setChangeListener { isVisibleInTimeline = it }
            .setIsSelectedListener { show(null) }


    }

    fun getLocalTime(parentTime: Float): Float {
        var localTime0 = (parentTime - timeOffset) * timeDilation
        localTime0 += timeAnimated[localTime0]
        return localTime0
    }

    fun getLocalColor(): Vector4f = getLocalColor(parent?.getLocalColor() ?: Vector4f(1f,1f,1f,1f), lastLocalTime)
    fun getLocalColor(parentColor: Vector4f, time: Float): Vector4f {
        val col = color.getValueAt(time)
        val mul = colorMultiplier[time]
        return Vector4f(col).mul(parentColor).mul(mul, mul, mul, 1f)
    }

    fun applyTransformLT(transform: Matrix4f, time: Float){

        val position = position[time]
        val scale = scale[time]
        val euler = rotationYXZ[time]
        val rotationQuat = rotationQuaternion
        val usesEuler = usesEuler
        val skew = skew[time]

        if(position.x != 0f || position.y != 0f || position.z != 0f){
            transform.translate(position)
        }

        if(usesEuler){// y x z
            if(euler.y != 0f) transform.rotate(toRadians(euler.y), yAxis)
            if(euler.x != 0f) transform.rotate(toRadians(euler.x), xAxis)
            if(euler.z != 0f) transform.rotate(toRadians(euler.z), zAxis)
        } else {
            if(rotationQuat != null) transform.rotate(rotationQuat[time])
        }

        if(scale.x != 1f || scale.y != 1f || scale.z != 1f) transform.scale(scale)

        if(skew.x != 0f || skew.y != 0f) transform.mul3x3(// works
            1f, skew.y, 0f,
            skew.x, 1f, 0f,
            0f, 0f, 1f
        )

    }

    fun applyTransformPT(transform: Matrix4f, parentTime: Float) = applyTransformLT(transform, getLocalTime(parentTime))

    /**
     * stack with camera already included
     * */
    fun draw(stack: Matrix4fStack, parentTime: Float, parentColor: Vector4f){

        val time = getLocalTime(parentTime)
        val color = getLocalColor(parentColor, time)

        if(color.w > 0.00025f){ // 12 bit = 4k
            applyTransformLT(stack, time)
            onDraw(stack, time, color)
            if(drawChildrenAutomatically()){
                drawChildren(stack, time, color)
            }
        }

    }

    open fun drawChildrenAutomatically() = true

    fun drawChildren(stack: Matrix4fStack, time: Float, color: Vector4f){
        children.forEach { child ->
            drawChild(stack, time, color, child)
        }
    }

    fun drawChild(stack: Matrix4fStack, time: Float, color: Vector4f, child: Transform?){
        if(child != null){
            child.getParentBlendMode(BlendMode.DEFAULT).apply()
            stack.pushMatrix()
            child.draw(stack, time, color)
            stack.popMatrix()
        }
    }

    open fun onDraw(stack: Matrix4fStack, time: Float, color: Vector4f){

        // draw a small symbol to indicate pivot
        if(!GFX.isFinalRendering){
            stack.pushMatrix()
            stack.scale(0.02f)
            GFX.draw3DCircle(stack, 0.7f, 0f, 360f, color, 1f)
            stack.popMatrix()
        }

    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(this, "parent", parent)
        writer.writeString("name", name)
        writer.writeObject(this, "position", position)
        writer.writeObject(this, "scale", scale)
        writer.writeObject(this, "rotationYXZ", rotationYXZ)
        writer.writeObject(this, "rotationQuat", rotationQuaternion)
        writer.writeObject(this, "skew", skew)
        writer.writeFloat("timeOffset", timeOffset)
        writer.writeFloat("timeDilation", timeDilation)
        writer.writeObject(this, "timeAnimated", timeAnimated)
        writer.writeObject(this, "color", color)
        writer.writeString("blendMode", blendMode.id)
        writer.writeList(this, "children", children)
        writer.writeBool("isVisibleInTimeline", isVisibleInTimeline, true)
    }

    override fun readBool(name: String, value: Boolean) {
        when(name){
            "isVisibleInTimeline" -> isVisibleInTimeline = value
            else -> super.readBool(name, value)
        }
    }

    override fun readObject(name: String, value: ISaveable?) {
        when(name){
            "parent" -> {
                if(value is Transform){
                    value.addChild(this)
                }
            }
            "children" -> {
                if(value is Transform){
                    addChild(value)
                }
            }
            "position" -> position.copyFrom(value)
            "scale" -> scale.copyFrom(value)
            "rotationYXZ" -> rotationYXZ.copyFrom(value)
            "rotationQuat" -> {
                rotationQuaternion?.copyFrom(value) ?: {
                    if(value is AnimatedProperty<*> && value.type == AnimatedProperty.Type.QUATERNION){
                        rotationQuaternion = value as AnimatedProperty<Quaternionf>
                    }
                }()
            }
            "skew" -> skew.copyFrom(value)
            "timeAnimated" -> timeAnimated.copyFrom(value)
            "color" -> color.copyFrom(value)
            else -> super.readObject(name, value)
        }
    }

    override fun readFloat(name: String, value: Float) {
        when(name){
            "timeDilation" -> timeDilation = value
            "timeOffset" -> timeOffset = value
            else -> super.readFloat(name, value)
        }
    }

    override fun readString(name: String, value: String) {
        when(name){
            "name" -> this.name = value
            "blendMode" -> this.blendMode = BlendMode[value]
            else -> super.readString(name, value)
        }
    }

    fun contains(t: Transform): Boolean {
        if(t === this) return true
        if(children != null){// can be null on init
            for(child in children){
                if(child === t || child.contains(t)) return true
            }
        }
        return false
    }

    override fun getClassName(): String = "Transform"
    override fun getApproxSize(): Int = 50

    fun addChild(child: Transform){
        if(child.contains(this)) throw RuntimeException("this cannot contain its parent!")
        child.parent?.removeChild(child)
        child.parent = this
        children += child
    }

    fun removeChild(child: Transform){
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

    fun getGlobalTransform(time: Float): Pair<Matrix4f, Float> {
        val (parentTransform, parentTime) = parent?.getGlobalTransform(time) ?: Matrix4f() to time
        val localTime = getLocalTime(parentTime)
        applyTransformLT(parentTransform, localTime)
        return parentTransform to localTime
    }

    fun removeFromParent(){
        parent?.removeChild(this)
    }

    fun getParentBlendMode(default: BlendMode): BlendMode =
        if(blendMode == BlendMode.UNSPECIFIED) parent?.getParentBlendMode(default) ?: default else blendMode

    override fun isDefaultValue() = false

    fun clone() = TextWriter.toText(this, false).toTransform()
    open fun acceptsWeight() = false

    fun VI(title: String, ttt: String, values: AnimatedProperty<*>, time: Float, style: Style): Panel {
        return when(val value = values[time]){
            is Float -> FloatInput(title, values, time, style)
                .setChangeListener { putValue(values, it) }
                .setIsSelectedListener { show(values) }
                .setTooltip(ttt)
            is Vector2f -> VectorInput(title, values, time, style)
                .setChangeListener { x, y, z, w -> putValue(values, Vector2f(x, y)) }
                .setIsSelectedListener { show(values) }
                .setTooltip(ttt)
            is Vector3f -> VectorInput(title, values, time, style)
                .setChangeListener { x, y, z, w -> putValue(values, Vector3f(x, y, z)) }
                .setIsSelectedListener { show(values) }
                .setTooltip(ttt)
            is Vector4f -> VectorInput(title, values, time, style)
                .setChangeListener { x, y, z, w -> putValue(values, Vector4f(x, y, z, w)) }
                .setIsSelectedListener { show(values) }
                .setTooltip(ttt)
            is Quaternionf -> VectorInput(title, values, time, style)
                .setChangeListener { x, y, z, w -> putValue(values, Quaternionf(x, y, z, w)) }
                .setIsSelectedListener { show(values) }
                .setTooltip(ttt)
            else -> throw RuntimeException("Type $value not yet implemented!")
        }
    }

    companion object {
        // these values MUST NOT be changed
        // they are universal constants, and are used
        // within shaders, too
        val xAxis = Vector3f(1f,0f,0f)
        val yAxis = Vector3f(0f,1f,0f)
        val zAxis = Vector3f(0f, 0f, 1f)
        fun String.toTransform() = TextReader.fromText(this).first() as Transform
    }


}