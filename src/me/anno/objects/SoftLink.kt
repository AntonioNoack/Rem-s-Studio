package me.anno.objects

import me.anno.cache.instances.LastModifiedCache
import me.anno.io.base.BaseWriter
import me.anno.language.translation.Dict
import me.anno.objects.animation.Type
import me.anno.objects.text.Text
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.editor.files.addChildFromFile
import me.anno.ui.style.Style
import me.anno.utils.LocalFile.toGlobalFile
import org.joml.Matrix4f
import org.joml.Matrix4fArrayList
import org.joml.Vector4f
import java.io.File

class SoftLink(var file: File) : Transform() {

    constructor() : this(File(""))

    /**
     * which camera is chosen from the scene
     * */
    var cameraIndex = 0

    /**
     * to apply LUTs, effects and such
     * */
    var renderToTexture = false

    init { isCollapsedI.setDefault(true) }

    private var lastModified: Any? = null
    private var lastCamera: Camera? = null

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        super.onDraw(stack, time, color)

        if(renderToTexture){
            // todo option to render to texture;
        }

        val lm = LastModifiedCache[file] to cameraIndex
        if (lm != lastModified) {
            lastModified = lm
            load()
        }
        val camera = lastCamera
        if (camera != null) {
            val (cameraTransform, _) = camera.getLocalTransform(time, this)
            val inv = Matrix4f(cameraTransform).invert()
            stack.pushMatrix()
            stack.mul(inv)
            drawChildren(stack, time, color)
            stack.popMatrix()
        } else {
            drawChildren(stack, time, color)
        }
    }

    override fun drawChildrenAutomatically(): Boolean = false

    fun load() {
        children.clear()
        if (listOfInheritance.count { it is SoftLink } > maxDepth) {// preventing loops
            addChild(Text("Too many links!"))
        } else {
            if (file.exists()) {
                if (file.isDirectory) {
                    addChild(Text("Use scene files!"))
                } else {
                    addChildFromFile(this, file, false, false) { transform ->
                        lastCamera = transform.listOfAll
                            .filterIsInstance<Camera>()
                            .toList()
                            .getOrNull(cameraIndex - 1)// 1 = first, 0 = none
                    }
                }
            } else {
                addChild(Text("File Not Found!"))
            }
        }
    }

    override fun createInspector(
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        super.createInspector(list, style, getGroup)
        val link = getGroup("Link Data", "", "softLink")
        link += vi("File", "Where the data is to be loaded from", "", null, file, style) { file = it }
        link += vi(
            "Camera Index", "Which camera should be chosen, 0 = none, 1 = first, ...", "",
            Type.INT_PLUS, cameraIndex, style
        ) { cameraIndex = it }
    }

    override fun save(writer: BaseWriter) {
        synchronized(this) {// children are not saved
            super.save(writer)
            val c = ArrayList(children)
            children.clear()
            writer.writeFile("file", file)
            writer.writeInt("cameraIndex", cameraIndex)
            writer.writeBoolean("renderToTexture", renderToTexture)
            children.addAll(c)
        }
    }

    override fun readBoolean(name: String, value: Boolean) {
        when(name){
            "renderToTexture" -> renderToTexture = value
            else -> super.readBoolean(name, value)
        }
    }

    override fun readInt(name: String, value: Int) {
        when (name) {
            "cameraIndex" -> cameraIndex = value
            else -> super.readInt(name, value)
        }
    }

    override fun readString(name: String, value: String) {
        when (name) {
            "file" -> file = value.toGlobalFile()
            else -> super.readString(name, value)
        }
    }

    override val areChildrenImmutable: Boolean = true

    override fun getDefaultDisplayName(): String = Dict["Linked Object", "obj.softLink"]
    override fun getClassName() = "SoftLink"

    companion object {
        const val maxDepth = 5
    }

}