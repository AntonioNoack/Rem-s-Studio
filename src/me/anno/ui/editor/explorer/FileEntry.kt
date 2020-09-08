package me.anno.ui.editor.explorer

import me.anno.config.DefaultStyle.black
import me.anno.gpu.GFX
import me.anno.gpu.TextureLib.whiteTexture
import me.anno.input.MouseButton
import me.anno.objects.cache.Cache
import me.anno.objects.modes.LoopingState
import me.anno.studio.Studio
import me.anno.ui.base.Panel
import me.anno.ui.base.TextPanel
import me.anno.ui.dragging.Draggable
import me.anno.ui.style.Style
import me.anno.utils.*
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

class FileEntry(val explorer: FileExplorer, val isParent: Boolean, val file: File, style: Style) :
    Panel(style.getChild("fileEntry")) {

    val size = 100
    val importType = file.extension.getImportType()
    var iconPath = if (file.isDirectory) {
        if (file.listFiles2().isNotEmpty())
            "file/folder.png" else "file/empty_folder.png"
    } else {
        when (importType) {
            "Image", "Cubemap" -> "file/image.png"
            "Text" -> "file/text.png"
            // todo dark/bright styled images
            // todo dark image for video -> one of the first frames? :)
            "Audio", "Video" -> "file/music.png"
            else -> "file/document.png"
        }
    }

    val title = TextPanel(if (isParent) ".." else if (file.name.isEmpty()) file.toString() else file.name, style)

    init {
        title.backgroundColor = black
        title.breaksIntoMultiline = true
    }

    var wasInFocus = false
    val originalBackgroundColor = backgroundColor
    val darkerBackgroundColor = mixARGB(0, originalBackgroundColor, 0.7f)

    override fun calculateSize(w: Int, h: Int) {
        super.calculateSize(w, h)
        minW = size
        minH = size
        this.w = size
        this.h = size
    }

    override fun draw(x0: Int, y0: Int, x1: Int, y1: Int) {
        wasInFocus = isInFocus
        backgroundColor = if (isInFocus) darkerBackgroundColor else originalBackgroundColor
        drawBackground()
        // todo tiles on background to show transparency? ofc only in the area of the image
        if (file.extension.equals("svg", true)) {

        } else {
            val needsDefault = when (importType) {
                // todo audio preview???
                "Video" -> {
                    // todo faster calculation of preview images
                    // todo maybe just cache them (statically, in files), once they were downloaded?
                    val image = Cache.getVideoFrame(file, 16, 0, 1, 10.0, 1000, LoopingState.PLAY_LOOP)
                    if(image != null && image.isLoaded){
                        var iw = image.w
                        var ih = image.h
                        val scale = (size - 20) / max(iw, ih).toFloat()
                        iw = (iw * scale).roundToInt()
                        ih = (ih * scale).roundToInt()
                        // image.ensureFiltering(false)
                        GFX.drawTexture(x + (size - iw) / 2, y + (size - ih) / 2, iw, ih, image, -1, null)
                        false
                    } else true
                }
                "Image" -> {
                    val image = if (file.length() < 10e6) Cache.getImage(file, 1000, true) else null
                    if(image != null){
                        var iw = image.w
                        var ih = image.h
                        val scale = (size - 20) / max(iw, ih).toFloat()
                        iw = (iw * scale).roundToInt()
                        ih = (ih * scale).roundToInt()
                        image.ensureFiltering(false)
                        GFX.drawTexture(x + (size - iw) / 2, y + (size - ih) / 2, iw, ih, image, -1, null)
                    }
                    image == null
                }
                else -> true
            }
            if(needsDefault){
                val image = Cache.getIcon(iconPath, true) ?: whiteTexture
                var iw = image.w
                var ih = image.h
                val scale = (size - 20) / max(iw, ih).toFloat()
                iw = (iw * scale).roundToInt()
                ih = (ih * scale).roundToInt()
                image.ensureFiltering(false)
                GFX.drawTexture(x + (size - iw) / 2, y + (size - ih) / 2, iw, ih, image, -1, null)
            }

        }
        title.x = x
        title.y = y
        title.w = 1
        title.h = 1
        title.draw(x0, y0, x1, y1)
    }

    override fun onGotAction(x: Float, y: Float, dx: Float, dy: Float, action: String, isContinuous: Boolean): Boolean {
        when (action) {
            "DragStart" -> {
                if (Studio.dragged?.getOriginal() != file) {
                    Studio.dragged =
                        Draggable(file.toString(), "File", file, TextPanel(file.nameWithoutExtension, style))
                }
            }
            "Enter" -> {
                if (file.isDirectory) {
                    explorer.folder = file
                    explorer.invalidate()
                } else {// todo check if it's a compressed thing we can enter
                    return false
                }
            }
            else -> return super.onGotAction(x, y, dx, dy, action, isContinuous)
        }
        return true
    }

    override fun onMouseClicked(x: Float, y: Float, button: MouseButton, long: Boolean) {
        when (button) {
            MouseButton.RIGHT -> {
                // todo get all meta data you ever need
                // todo or get more options? probably better... delete, new folder, new file,
                // todo rename, open in explorer, open in editor, ...
            }
            else -> super.onMouseClicked(x, y, button, long)
        }
    }

    override fun onDoubleClick(x: Float, y: Float, button: MouseButton) {
        when (button) {
            MouseButton.RIGHT -> {
                // todo open the file in the editor, or add it to the scene?
                // todo or open it using windows?
            }
            else -> super.onDoubleClick(x, y, button)
        }
    }

    override fun onDeleteKey(x: Float, y: Float) {
        if (GFX.inFocus.size == 1) {
            // ask, then delete (or cancel)
            GFX.openMenu(x, y, "Delete this file? (${file.length().formatFileSize()})", listOf(
                "Yes" to {
                    // todo put history state...
                    file.deleteRecursively()
                    explorer.invalidate()
                },
                "No" to {},
                "Yes, permanently" to {
                    file.deleteRecursively()
                    explorer.invalidate()
                }
            ))
        } else if (GFX.inFocus.firstOrNull() == this) {
            // ask, then delete all (or cancel)
            GFX.openMenu(x, y, "Delete these files? (${GFX.inFocus.size}x, ${
            GFX.inFocus
                .sumByDouble { (it as? FileEntry)?.file?.length()?.toDouble() ?: 0.0 }
                .toLong()
                .formatFileSize()
            })", listOf(
                "Yes" to {
                    // todo put history state...
                    GFX.inFocus.forEach { (it as? FileEntry)?.file?.deleteRecursively() }
                    explorer.invalidate()
                },
                "No" to {},
                "Yes, permanently" to {
                    GFX.inFocus.forEach { (it as? FileEntry)?.file?.deleteRecursively() }
                    explorer.invalidate()
                }
            ))
        }
    }

    override fun getMultiSelectablePanel() = this

    override fun printLayout(tabDepth: Int) {
        super.printLayout(tabDepth)
        println("${Tabs.spaces(tabDepth * 2 + 2)} ${file.name}")
    }

    override fun getClassName() = "FileEntry"

}