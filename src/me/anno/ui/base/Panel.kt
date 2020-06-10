package me.anno.ui.base

import me.anno.gpu.GFX
import me.anno.input.Input
import me.anno.io.Saveable
import me.anno.ui.base.constraints.Margin
import me.anno.ui.base.groups.PanelGroup
import me.anno.ui.style.Style
import me.anno.utils.Tabs
import java.io.File
import java.lang.RuntimeException

open class Panel(val style: Style): Saveable(){

    var minW = 1
    var minH = 1

    var visibility = Visibility.VISIBLE

    var weight = 0f

    var backgroundColor = style.getColor("background", -1)

    var parent: PanelGroup? = null
    val layoutConstraints = ArrayList<Constraint>()

    var w = 258
    var h = 259
    var x = 0
    var y = 0

    val isInFocus get() = GFX.inFocus === this
    val canBeSeen get() = canBeSeen(0,0,GFX.width,GFX.height)
    val canBeSeenCurrently get() = canBeSeen(GFX.windowX, GFX.windowY, GFX.windowWidth, GFX.windowHeight)
    val isHovered get() = Input.mouseX.toInt()-x in 0 until w && Input.mouseY.toInt()-y in 0 until h
    fun canBeSeen(x0: Int, y0: Int, w0: Int, h0: Int): Boolean {
        return x + w > x0 && y + h > y0 && x < x0+w0 && y < y0+h0
    }

    var tooltip: String? = null
    val isVisible get() = visibility == Visibility.VISIBLE && canBeSeen

    fun requestFocus() = GFX.requestFocus(this)

    fun drawBackground(){
        GFX.drawRect(x,y,w,h,backgroundColor)
    }

    open fun draw(x0: Int, y0: Int, x1: Int, y1: Int){
        drawBackground()
    }

    fun setWeight(w: Float): Panel {
        weight = w
        return this
    }

    operator fun plusAssign(c: Constraint){
        layoutConstraints += c
        layoutConstraints.sortBy { it.order }
    }

    fun addPadding(left: Int, top: Int, right: Int, bottom: Int){
        layoutConstraints.add(Margin(left, top, right, bottom))
        layoutConstraints.sortBy { it.order }
    }

    fun addPadding(x: Int, y: Int) = addPadding(x,y,x,y)
    fun addPadding(p: Int) = addPadding(p,p,p,p)

    fun assert(b: Boolean, msg: String?){
        if(!b) throw RuntimeException(msg)
    }

    open fun placeInParent(x: Int, y: Int){
        this.x = x
        this.y = y
    }

    open fun applyConstraints(){
        for(c in layoutConstraints){
            c.apply(this)
        }
    }

    open fun calculateSize(w: Int, h: Int){
        this.w = w
        this.h = h
    }

    fun add(c: Constraint): Panel {
        this += c
        return this
    }

    fun removeFromParent(){
        parent?.remove(this)
    }

    var onClickListener: ((Float, Float, Int, Boolean) -> Unit)? = null

    fun setOnClickListener(onClickListener: ((x: Float, y: Float, button: Int, long: Boolean) -> Unit)): Panel {
        this.onClickListener = onClickListener
        return this
    }

    open fun onMouseDown(x: Float, y: Float, button: Int){ parent?.onMouseDown(x,y,button) }
    open fun onMouseUp(x: Float, y: Float, button: Int){ parent?.onMouseUp(x,y,button) }
    open fun onMouseClicked(x: Float, y: Float, button: Int, long: Boolean){
        onClickListener?.invoke(x,y,button,long) ?: parent?.onMouseClicked(x,y,button,long)
    }

    open fun onDoubleClick(x: Float, y: Float, button: Int){ parent?.onDoubleClick(x,y,button)}
    open fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float){ parent?.onMouseMoved(x,y,dx,dy) }
    open fun onMouseWheel(x: Float, y: Float, dx: Float, dy: Float){ parent?.onMouseWheel(x,y,dx,dy) }

    open fun onKeyDown(x: Float, y: Float, key: Int){ parent?.onKeyDown(x,y,key) }
    open fun onKeyUp(x: Float, y: Float, key: Int){ parent?.onKeyUp(x,y,key) }
    open fun onKeyTyped(x: Float, y: Float, key: Int){ parent?.onKeyTyped(x,y,key) }
    open fun onCharTyped(x: Float, y: Float, key: Int){ parent?.onCharTyped(x,y,key) }

    open fun onEmpty(x: Float, y: Float) { parent?.onEmpty(x,y) }
    open fun onPaste(x: Float, y: Float, data: String, type: String){ parent?.onPaste(x,y,data,type) }
    open fun onPasteFiles(x: Float, y: Float, files: List<File>){ parent?.onPasteFiles(x,y,files) ?: println("Paste Ignored! $files") }
    open fun onCopyRequested(x: Float, y: Float): String? = parent?.onCopyRequested(x,y)

    open fun onSelectAll(x: Float, y: Float){ parent?.onSelectAll(x,y) }

    open fun onGotAction(x: Float, y: Float, dx: Float, dy: Float, action: String, isContinuous: Boolean): Boolean = false

    open fun onBackKey(x: Float, y: Float){ parent?.onBackKey(x,y) }
    open fun onEnterKey(x: Float, y: Float){ parent?.onEnterKey(x,y) }
    open fun onDeleteKey(x: Float, y: Float){ parent?.onDeleteKey(x,y) }

    override fun getClassName(): String = "Panel"
    override fun getApproxSize(): Int = 1

    open fun getCursor(): Long? = parent?.getCursor() ?: 0L

    open fun getTooltipText(x: Float, y: Float): String? = tooltip ?: parent?.getTooltipText(x, y)

    fun setTooltip(tooltipText: String?): Panel {
        tooltip = tooltipText
        return this
    }

    open fun printLayout(depth: Int){
        println("${Tabs.spaces(depth*2)}${javaClass.simpleName}($weight) $x $y += $w $h ($minW $minH)")
    }

    override fun isDefaultValue() = false
    open fun isKeyInput() = false

    val listOfAll: Sequence<Panel> get() = sequence {
        yield(this@Panel)
        (this@Panel as? PanelGroup)?.children?.forEach { child ->
            yieldAll(child.listOfAll)
        }
    }

}