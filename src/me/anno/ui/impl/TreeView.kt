package me.anno.ui.impl

import me.anno.config.DefaultStyle.black
import me.anno.gpu.GFX
import me.anno.io.text.TextReader
import me.anno.utils.clamp
import me.anno.objects.Image
import me.anno.objects.Transform
import me.anno.objects.Video
import me.anno.objects.geometric.Circle
import me.anno.objects.geometric.Polygon
import me.anno.ui.base.*
import me.anno.ui.base.components.Padding
import me.anno.ui.base.constraints.WrapAlign
import me.anno.ui.base.groups.PanelList
import me.anno.ui.style.Style
import org.joml.Vector4f
import java.io.File
import kotlin.concurrent.thread

// todo support for multiple cameras? -> just use scenes?
// todo switch back and forth? how -> multiple cameras... how?

class TreeView(var root: Transform, style: Style):
    ScrollPanel(style.getChild("treeView"), Padding(1), WrapAlign.AxisAlignment.MIN) {

    val list = child as PanelList

    val transformByIndex = ArrayList<Transform>()
    var inset = style.getSize("treeView.inset", style.getSize("textSize", 12)/3)

    var index = 0

    var clickedTransform: Transform? = null

    fun Vector4f.toRGB(scale: Int = 255): Int {
        return clamp((x * scale).toInt(), 0, 255).shl(16) or
                clamp((y * scale).toInt(), 0, 255).shl(8) or
                clamp((z * scale).toInt(), 0, 255) or
                clamp((w * 255).toInt(), 0, 255).shl(24)
    }

    fun updateTree(){
        val todo = ArrayList<Pair<Transform, Int>>()
        todo.add(root to 0)
        index = 0
        while(todo.isNotEmpty()){
            val (transform, depth) = todo.removeAt(todo.lastIndex)
            val panel = getOrCreateChild(index++, transform)
            panel.padding.left = inset * depth + panel.padding.right
            panel.text = transform.name
            if(!transform.isCollapsed){
                todo.addAll(transform.children.map { it to (depth+1) })
            }
        }
        for(i in index until list.children.size){
            list.children[i].visibility = Visibility.GONE
        }
    }

    override fun draw(x0: Int, y0: Int, x1: Int, y1: Int) {
        super.draw(x0, y0, x1, y1)

        updateTree()

        if(focused?.isInFocus != true){
            takenElement = null
        }

        val focused = focused
        if(focused != null && takenElement != null){
            val x = focused.x
            val y = focused.y
            val mx = GFX.mx.toInt()
            val my = GFX.my.toInt()
            focused.x = mx - focused.w / 2
            focused.y = my - focused.h / 2
            focused.draw(x0, y0, x1, y1)
            focused.x = x
            focused.y = y
        }

    }

    var focused: Panel? = null
    var takenElement: Transform? = null

    fun getOrCreateChild(index: Int, transform0: Transform): TextPanel {
        if(index < list.children.size){
            transformByIndex[index] = transform0
            return list.children[index] as TextPanel
        }
        val child = object: TextPanel("", style){

            var isClickedCtr = 0
            var isClicked = 0
            // val textColor0 = textColor
            val accentColor = style.getColor("accentColor", black or 0xff0000)

            override fun draw(x0: Int, y0: Int, x1: Int, y1: Int) {
                super.draw(x0, y0, x1, y1)
                val transform = transformByIndex[index]
                textColor = black or (transform.getLocalColor().toRGB(180))
                if(isInFocus) textColor = textColor and accentColor
                GFX.drawText(x + padding.left, y + padding.top, fontSize, text, textColor, backgroundColor)
            }

            override fun onMouseDown(x: Float, y: Float, button: Int) {
                requestFocus()
                if(button == 0){
                    // left
                    // todo drag this element, if the mouse is down long enough/moves enough
                    focused = this
                    val ctr = ++isClickedCtr
                    isClicked = ctr
                    thread {
                        Thread.sleep(350)
                        if(ctr == isClicked){
                            takenElement = transform0
                        }
                    }
                }
            }

            override fun onMouseClicked(x: Float, y: Float, button: Int, long: Boolean) {
                focused = null
                isClicked = 0
                val transform = transformByIndex[index]
                when(button){
                    0 -> {
                        GFX.selectedTransform = transform
                        clickedTransform = transform
                    }
                    1 -> {// right click
                        clickedTransform = transform
                        fun add(action: (Transform) -> Transform): (Int, Boolean) -> Boolean {
                            return { b, l ->
                                if(b == 0){
                                    clickedTransform?.apply { GFX.selectedTransform = action(this) }
                                    true
                                } else false
                            }
                        }
                        GFX.openMenu(GFX.mx, GFX.my, "Add Component",
                            "Video" to add { Video(File(""), it) },
                            "Folder" to add { Transform(it) },
                            "Circle" to add { Circle(it) },
                            "Polygon" to add { Polygon(it) },
                            "Image" to add { Image(File(""), it) }
                        )
                    }
                }
            }

            override fun onCopyRequested(x: Float, y: Float): String? {
                return transformByIndex[index].stringify()
            }

            override fun onPaste(x: Float, y: Float, pasted: String) {
                val child = TextReader.fromText(pasted).firstOrNull { it is Transform } as? Transform ?: return super.onPaste(x, y, pasted)
                transformByIndex[index].addChild(child)
            }

            override fun onDeleteKey(x: Float, y: Float) {
                val transform = transformByIndex[index]
                val parent = transform.parent
                if(parent != null){
                    GFX.selectedTransform = parent
                    parent.removeChild(transform)
                }
            }

            override fun onBackKey(x: Float, y: Float) = onDeleteKey(x,y)

        }
        transformByIndex += transform0
        list += child
        return child
    }

    override fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float) {
        val mouseDownElement = takenElement
        if(focused?.isInFocus == true){
            // todo display, where we'd move it
            // todo between vs at the end vs at the start
            // todo use the arrow keys to move elements left, right, up, down?
            // todo always give the user hints? :D
            // todo we'd need a selection mode with the arrow keys, too...
        }
    }

    override fun getClassName(): String = "TreeView"


}