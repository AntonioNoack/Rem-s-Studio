package me.anno.ui.editor.treeView

import me.anno.config.DefaultConfig
import me.anno.config.DefaultStyle.black
import me.anno.config.DefaultStyle.midGray
import me.anno.gpu.Cursor
import me.anno.gpu.GFX.inFocus
import me.anno.gpu.GFXx2D.drawRect
import me.anno.input.Input
import me.anno.input.Input.mouseX
import me.anno.input.Input.mouseY
import me.anno.input.MouseButton
import me.anno.io.utils.StringMap
import me.anno.language.translation.Dict
import me.anno.language.translation.NameDesc
import me.anno.objects.Camera
import me.anno.objects.Rectangle
import me.anno.objects.Transform
import me.anno.objects.Transform.Companion.toTransform
import me.anno.objects.effects.MaskLayer
import me.anno.studio.StudioBase.Companion.dragged
import me.anno.studio.rems.RemsStudio
import me.anno.studio.rems.Selection.selectTransform
import me.anno.studio.rems.Selection.selectedTransform
import me.anno.ui.base.text.TextPanel
import me.anno.ui.base.menu.Menu.menuSeparator1
import me.anno.ui.base.menu.Menu.openMenu
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.dragging.Draggable
import me.anno.ui.editor.files.addChildFromFile
import me.anno.ui.style.Style
import me.anno.utils.Color.toARGB
import me.anno.utils.Maths.clamp
import org.apache.logging.log4j.LogManager
import java.io.File

class TreeViewPanel(val getElement: () -> Transform, style: Style) : TextPanel("", style) {

    // todo text shadow, if text color and background color are close

    private val accentColor = style.getColor("accentColor", black or 0xff0000)

    init {
        enableHoverColor = true
    }

    var showAddIndex: Int? = null

    override fun getVisualState(): Any? = Pair(super.getVisualState(), showAddIndex)

    override fun tickUpdate() {
        super.tickUpdate()
        val transform = getElement()
        val dragged = dragged
        textColor = black or (transform.getLocalColor().toARGB(180))
        showAddIndex = if (
            mouseX.toInt() in lx0..lx1 &&
            mouseY.toInt() in ly0..ly1 &&
            dragged is Draggable && dragged.getOriginal() is Transform
        ) {
            clamp(((mouseY - this.y) / this.h * 3).toInt(), 0, 2)
        } else null
        val isInFocus = isInFocus || selectedTransform == transform
        if (isHovered) textColor = hoverColor
        if (isInFocus) textColor = accentColor
    }

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        super.onDraw(x0, y0, x1, y1)
        val showAddIndex = showAddIndex
        if (showAddIndex != null) {
            val x = x + padding.left
            val textSize = font.sizeInt
            val indent = textSize
            val lineWidth = textSize * 7
            val lineColor = midGray
            when (showAddIndex) {
                0 -> drawRect(x, y, lineWidth, 1, lineColor)
                1 -> drawRect(x + indent, y + h - 1, lineWidth, 1, lineColor)
                2 -> drawRect(x, y + h - 1, lineWidth, 1, lineColor)
            }
        }
    }

    override val effectiveTextColor: Int get() = textColor

    override fun onMouseClicked(x: Float, y: Float, button: MouseButton, long: Boolean) {

        val transform = getElement()
        when {
            button.isLeft -> {
                if (Input.isShiftDown && inFocus.size < 2) {
                    RemsStudio.largeChange(if (transform.isCollapsed) "Expanded ${transform.name}" else "Collapsed ${transform.name}") {
                        val target = !transform.isCollapsed
                        // remove children from the selection???...
                        inFocus.filterIsInstance<TreeViewPanel>().forEach {
                            val transform2 = it.getElement()
                            transform2.isCollapsed = target
                        }
                        transform.isCollapsed = target
                    }
                } else {
                    selectTransform(transform)
                }
            }
            button.isRight -> openAddMenu(transform)
        }
    }

    override fun onCopyRequested(x: Float, y: Float): String? {
        /*BinaryWriter(DataOutputStream(File(OS.desktop, "raw.bin").outputStream()))
            .writeObject(null,null,getElement(),true)*/
        return getElement().stringify()
    }

    override fun onPaste(x: Float, y: Float, data: String, type: String) {
        try {
            val child = data.toTransform() ?: return super.onPaste(x, y, data, type)
            val original = (dragged as? Draggable)?.getOriginal() as? Transform
            val relativeY = (y - this.y) / this.h
            val e = getElement()
            RemsStudio.largeChange("Moved Component") {
                if (relativeY < 0.33f) {
                    // paste on top
                    if (e.parent != null) {
                        e.addBefore(child)
                    } else {
                        e.addChild(child)
                    }
                    // we can't remove the element, if it's the parent
                    if (original !in child.listOfAll) {
                        original?.removeFromParent()
                    }
                } else if (relativeY < 0.67f) {
                    // paste as child
                    e.addChild(child)
                    if (e != original) {
                        // we can't remove the element, if it's the parent
                        if (original !in child.listOfAll) {
                            original?.removeFromParent()
                        }
                    }
                } else {
                    // paste below
                    if (e.parent != null) {
                        e.addAfter(child)
                    } else {
                        e.addChild(child)
                    }
                    // we can't remove the element, if it's the parent
                    if (original !in child.listOfAll) {
                        original?.removeFromParent()
                    }
                }
                selectTransform(child)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            super.onPaste(x, y, data, type)
        }
    }

    override fun onPasteFiles(x: Float, y: Float, files: List<File>) {
        val transform = getElement()
        files.forEach { addChildFromFile(transform, it, null, true) {} }
    }

    override fun onGotAction(x: Float, y: Float, dx: Float, dy: Float, action: String, isContinuous: Boolean): Boolean {
        when (action) {
            "DragStart" -> {
                val transform = getElement()
                if (dragged?.getOriginal() != transform) {
                    dragged = Draggable(transform.stringify(), "Transform", transform,
                        TextPanel(transform.name, style)
                    )
                }
            }
            else -> return super.onGotAction(x, y, dx, dy, action, isContinuous)
        }
        return true
    }

    override fun onEmpty(x: Float, y: Float) {
        onDeleteKey(x, y)
    }

    override fun onDeleteKey(x: Float, y: Float) {
        RemsStudio.largeChange("Deleted Component ${getElement().name}") {
            getElement().destroy()
        }
    }

    override fun onBackSpaceKey(x: Float, y: Float) = onDeleteKey(x, y)
    override fun getCursor() = Cursor.drag

    override fun getTooltipText(x: Float, y: Float): String? {
        val transform = getElement()
        return if (transform is Camera)
            transform.getDefaultDisplayName() + Dict[", drag onto scene to view", "ui.treeView.dragCameraToView"]
        else transform.getDefaultDisplayName()
    }

    // multiple values can be selected
    override fun getMultiSelectablePanel() = this

    companion object {
        private val LOGGER = LogManager.getLogger(TreeViewPanel::class)
        fun openAddMenu(baseTransform: Transform) {
            fun add(action: (Transform) -> Transform): () -> Unit = { selectTransform(action(baseTransform)) }
            val options = DefaultConfig["createNewInstancesList"] as? StringMap
            if (options != null) {
                val extras = ArrayList<MenuOption>()
                if (baseTransform.parent != null) {
                    extras += menuSeparator1
                    extras += MenuOption(
                        NameDesc(
                            "Add Mask",
                            "Creates a mask component, which can be used for many effects",
                            "ui.objects.addMask"
                        )
                    ) {
                        val parent = baseTransform.parent!!
                        val i = parent.children.indexOf(baseTransform)
                        if (i < 0) throw RuntimeException()
                        val mask = MaskLayer.create(listOf(Rectangle.create()), listOf(baseTransform))
                        mask.isFullscreen = true
                        parent.setChildAt(mask, i)
                    }
                }
                val additional = baseTransform.getAdditionalChildrenOptions().map { option ->
                    MenuOption(NameDesc(option.title, option.description, "")) {
                        RemsStudio.largeChange("Added ${option.title}") {
                            val new = option.generator() as Transform
                            baseTransform.addChild(new)
                            selectTransform(new)
                        }
                    }
                }
                if (additional.isNotEmpty()) {
                    extras += menuSeparator1
                    extras += additional
                }
                openMenu(
                    mouseX, mouseY, NameDesc("Add Child", "", "ui.objects.add"),
                    options.entries
                        .sortedBy { (key, _) -> key.toLowerCase() }
                        .map { (key, value) ->
                            val sample = if (value is Transform) value.clone() else value.toString().toTransform()
                            MenuOption(NameDesc(key, sample?.getDefaultDisplayName() ?: "", ""), add {
                                val newT = if (value is Transform) value.clone() else value.toString().toTransform()
                                newT!!
                                it.addChild(newT)
                                newT
                            })
                        } + extras
                )
            } else LOGGER.warn(Dict["Reset the config to enable this menu!", "config.warn.needsReset.forMenu"])
        }
    }

}