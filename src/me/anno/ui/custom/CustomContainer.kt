package me.anno.ui.custom

import me.anno.cache.Cache
import me.anno.config.DefaultStyle.white
import me.anno.gpu.GFX
import me.anno.gpu.GFXx2D.drawTexture
import me.anno.gpu.TextureLib.whiteTexture
import me.anno.input.MouseButton
import me.anno.language.translation.Dict
import me.anno.ui.base.Panel
import me.anno.ui.base.components.Padding
import me.anno.ui.base.groups.PanelContainer
import me.anno.ui.custom.data.CustomPanelData
import me.anno.ui.custom.data.ICustomDataCreator
import me.anno.ui.editor.sceneView.SceneView
import me.anno.ui.style.Style

class CustomContainer(default: Panel, style: Style) : PanelContainer(default, Padding(0), style), ICustomDataCreator {

    override fun getLayoutState(): Any? = Pair(super.getLayoutState(), child)

    override fun calculateSize(w: Int, h: Int) {
        child.calculateSize(w, h)
        minW = child.minW
        minH = child.minH
    }

    override fun placeInParent(x: Int, y: Int) {
        child.placeInParent(x, y)
        this.x = x
        this.y = y
    }

    override fun invalidateLayout() {
        window!!.needsLayout += this
    }

    override fun drawsOverlaysOverChildren(lx0: Int, ly0: Int, lx1: Int, ly1: Int): Boolean {
        return this.lx1 - lx1 < customContainerCrossSize && ly0 - this.ly0 < customContainerCrossSize
    }

    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        super.onDraw(x0, y0, x1, y1)
        val icon = Cache.getInternalTexture("cross.png", true) ?: whiteTexture
        drawTexture(x + w - 14, y + 2, 12, 12, icon, white, null)
    }

    private fun changeType() {
        fun action(action: () -> Panel): () -> Unit = { changeTo(action()) }
        val options = TypeLibrary.typeList
            .map { GFX.MenuOption(it.displayName, "", action { it.constructor() }) }
            .toMutableList()
        options += GFX.MenuOption("Remove This Element", "", "ui.customize.remove") {
            (parent as? CustomList)?.apply {
                remove(indexInParent)
            }
            Unit
        }
        options += GFX.MenuOption("Add Panel Before", "", "ui.customize.addBefore") {
            val parent = parent!!
            val index = indexInParent
            if (parent is CustomListX) {
                val children = parent.children
                val view = CustomContainer(SceneView(style), style)
                val bar = CustomizingBar(0, 3, 0, style)
                bar.parent = parent
                view.parent = parent
                children.add(index, view)
                children.add(index + 1, bar)
                view.weight = 1f
                parent.update()
            } else {
                parent as CustomListY
                val children = parent.children
                val replaced = CustomListX(style)
                replaced.parent = parent
                children[index] = replaced
                replaced.weight = this.weight
                replaced.add(CustomContainer(SceneView(style), style))
                replaced.add(this)
            }
            Unit
        }
        options += GFX.MenuOption("Add Panel After", "", "ui.customize.addAfter") {
            val parent = parent!!
            val index = indexInParent
            if (parent is CustomListX) {
                val children = parent.children
                val view = CustomContainer(SceneView(style), style)
                val bar = CustomizingBar(0, 3, 0, style)
                bar.parent = parent
                view.parent = parent
                children.add(index + 1, bar)
                children.add(index + 2, view)
                view.weight = 1f
                parent.update()
            } else {
                parent as CustomListY
                val children = parent.children
                val replaced = CustomListX(style)
                replaced.parent = parent
                children[index] = replaced
                replaced.weight = this.weight
                replaced.add(this)
                replaced.add(CustomContainer(SceneView(style), style))
                parent.update()
            }
            Unit
        }
        options += GFX.MenuOption("Add Panel Above", "", "ui.customize.addAbove") {
            val parent = parent!!
            val index = indexInParent
            if (parent is CustomListY) {
                val children = parent.children
                val view = CustomContainer(SceneView(style), style)
                val bar = CustomizingBar(0, 0, 3, style)
                bar.parent = parent
                view.parent = parent
                children.add(index, view)
                children.add(index + 1, bar)
                view.weight = 1f
                parent.update()
            } else {
                parent as CustomListX
                val children = parent.children
                val replaced = CustomListY(style)
                replaced.parent = parent
                children[index] = replaced
                replaced.weight = this.weight
                replaced.add(CustomContainer(SceneView(style), style))
                replaced.add(this)
                parent.update()
            }
            Unit
        }
        options += GFX.MenuOption("Add Panel Below", "", "ui.customize.addBelow") {
            val parent = parent!!
            val index = indexInParent
            if (parent is CustomListY) {
                val children = parent.children
                val view = CustomContainer(SceneView(style), style)
                val bar = CustomizingBar(0, 0, 3, style)
                bar.parent = parent
                view.parent = parent
                children.add(index + 1, bar)
                children.add(index + 2, view)
                view.weight = 1f
                parent.update()
            } else {
                parent as CustomListX
                val children = parent.children
                val replaced = CustomListY(style)
                replaced.parent = parent
                children[index] = replaced
                replaced.weight = this.weight
                replaced.add(this)
                replaced.add(CustomContainer(SceneView(style), style))
                parent.update()
            }
            Unit
        }
        GFX.openMenu(x + w - 16, y, Dict["Customize UI", "ui.customize.title"], options)
    }

    private fun changeTo(panel: Panel) {
        child = panel
        child.parent = this
    }

    override fun onGotAction(x: Float, y: Float, dx: Float, dy: Float, action: String, isContinuous: Boolean): Boolean {
        when (action) {
            "ChangeType" -> changeType()
            "ChangeType(SceneView)" -> changeTo(SceneView(style))
            else -> return super.onGotAction(x, y, dx, dy, action, isContinuous)
        }
        return true
    }

    override fun onMouseClicked(x: Float, y: Float, button: MouseButton, long: Boolean) {
        clicked(x, y)
    }

    fun clicked(x: Float, y: Float): Boolean {
        return if (isCross(x, y)) {
            changeType()
            true
        } else false
    }

    override fun toData() = CustomPanelData(child)

    companion object {
        val customContainerCrossSize = 16f
        fun Panel.isCross(x: Float, y: Float) =
            x - (this.x + w - 16f) in 0f..customContainerCrossSize && y - this.y in 0f..customContainerCrossSize
    }

}