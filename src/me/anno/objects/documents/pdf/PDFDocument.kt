package me.anno.objects.documents.pdf

import me.anno.gpu.GFX.isFinalRendering
import me.anno.gpu.GFX.windowHeight
import me.anno.gpu.GFX.windowWidth
import me.anno.gpu.GFXx3D
import me.anno.gpu.TextureLib.colorShowTexture
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.Filtering
import me.anno.io.ISaveable
import me.anno.io.base.BaseWriter
import me.anno.objects.GFXTransform
import me.anno.objects.Transform
import me.anno.objects.animation.AnimatedProperty
import me.anno.objects.animation.Type
import me.anno.objects.documents.SiteSelection.parseSites
import me.anno.objects.documents.pdf.PDFCache.getTexture
import me.anno.objects.modes.UVProjection
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.style.Style
import me.anno.io.FileReference
import me.anno.utils.files.LocalFile.toGlobalFile
import me.anno.utils.types.Floats.toRadians
import me.anno.utils.types.Lists.median
import me.anno.utils.types.Strings.isBlank2
import org.apache.logging.log4j.LogManager
import org.apache.pdfbox.pdmodel.PDDocument
import org.joml.*
import kotlin.math.*

// todo different types of lists (x list, y list, grid, linear particle system, random particle system, ...)
// todo different types of iterators (pdf pages, parts of images, )
// todo re-project UV textures onto stuff to animate an image exploding (gets UVs from first frame, then just is a particle system or sth else)
// todo interpolation between lists and sets? could be interesting :)

open class PDFDocument(var file: FileReference, parent: Transform?) : GFXTransform(parent) {

    constructor() : this(FileReference(""), null)

    var selectedSites = ""

    var padding = AnimatedProperty.float()

    var direction = AnimatedProperty.rotY()

    var editorQuality = 3f
    var renderQuality = 3f

    override fun getDefaultDisplayName(): String {
        return if (file == null || file.name.isBlank2()) "PDF"
        else file.name
    }

    override fun getClassName(): String = "PDFDocument"
    override fun getSymbol(): String = "\uD83D\uDDCE"

    fun getSelectedSitesList() = parseSites(selectedSites)

    val meta get() = getMeta(file, true)
    val forcedMeta get() = getMeta(file, false)!!

    fun getMeta(src: FileReference, async: Boolean): PDDocument? {
        return PDFCache.getDocument(src, async)
    }

    // rather heavy...
    override fun getRelativeSize(): Vector3f {
        val doc = forcedMeta
        val pageCount = doc.numberOfPages
        val referenceScale = (0 until min(10, pageCount)).map {
            doc.getPage(it).mediaBox.run {
                if (windowWidth > windowHeight) height else width
            }
        }.median(0f)
        if (pageCount < 1) return Vector3f(1f)
        val firstPage = getSelectedSitesList().firstOrNull()?.first ?: return Vector3f(1f)
        return doc.getPage(firstPage).mediaBox.run { Vector3f(width/referenceScale, height/referenceScale, 1f) }
    }

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4fc) {

        val file = file
        val doc = meta
        if (doc == null) {
            super.onDraw(stack, time, color)
            return checkFinalRendering()
        }
        val quality = if (isFinalRendering) renderQuality else editorQuality
        val numberOfPages = doc.numberOfPages
        val pages = getSelectedSitesList()
        val direction = -direction[time].toRadians()
        // find reference scale: median height (or width, or height if height > width?)
        val referenceScale = (0 until min(10, numberOfPages)).map {
            doc.getPage(it).mediaBox.run {
                height//if (windowWidth > windowHeight) height else width
            }
        }.median(0f)
        var wasDrawn = false
        val padding = padding[time]
        val cos = cos(direction)
        val sin = sin(direction)
        val normalizer = 1f / max(abs(cos), abs(sin))
        val scale = (1f + padding) * normalizer / referenceScale
        stack.next {
            pages.forEach {
                for (pageNumber in max(it.first, 0)..min(it.last, numberOfPages - 1)) {
                    var texture = getTexture(file, doc, quality, pageNumber)
                    if (texture == null) {
                        checkFinalRendering()
                        texture = colorShowTexture
                    }
                    // find out the correct size for the image
                    // find also the correct y offset...
                    val mediaBox = doc.getPage(pageNumber).mediaBox
                    val w = mediaBox.width * scale
                    val h = mediaBox.height * scale
                    if (wasDrawn) {
                        stack.translate(cos * w, sin * h, 0f)
                    }
                    if(texture === colorShowTexture){
                        stack.next {
                            stack.scale(w/h, 1f, 1f)
                            GFXx3D.draw3DVideo(
                                this, time, stack, texture, color,
                                Filtering.NEAREST, Clamping.CLAMP, null, UVProjection.Planar
                            )
                        }
                    } else {
                        GFXx3D.draw3DVideo(
                            this, time, stack, texture, color,
                            Filtering.LINEAR, Clamping.CLAMP, null, UVProjection.Planar
                        )
                    }
                    wasDrawn = true
                    stack.translate(cos * w, sin * h, 0f)
                }
            }
        }
        if (!wasDrawn) {
            super.onDraw(stack, time, color)
        }
    }

    override fun createInspector(
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        super.createInspector(list, style, getGroup)
        val doc = getGroup("Document", "", "docs")
        doc += vi("Path", "", null, file, style) { file = it }
        doc += vi("Pages", "", null, selectedSites, style) { selectedSites = it }
        doc += vi("Padding", "", padding, style)
        doc += vi("Direction", "Top-Bottom/Left-Right in Degrees", direction, style)
        doc += vi("Editor Quality", "", Type.FLOAT_PLUS, editorQuality, style) { editorQuality = it }
        doc += vi("Render Quality", "", Type.FLOAT_PLUS, renderQuality, style) { renderQuality = it }
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeFile("file", file)
        writer.writeObject(this, "padding", padding)
        writer.writeString("selectedSites", selectedSites)
        writer.writeObject(this, "direction", direction)
        writer.writeFloat("editorQuality", editorQuality)
        writer.writeFloat("renderQuality", renderQuality)
    }

    override fun readFloat(name: String, value: Float) {
        when (name) {
            "editorQuality" -> editorQuality = value
            "renderQuality" -> renderQuality = value
            else -> super.readFloat(name, value)
        }
    }

    override fun readString(name: String, value: String) {
        when (name) {
            "file" -> file = value.toGlobalFile()
            "selectedSites" -> selectedSites = value
            else -> super.readString(name, value)
        }
    }

    override fun readObject(name: String, value: ISaveable?) {
        when (name) {
            "padding" -> padding.copyFrom(value)
            "direction" -> direction.copyFrom(value)
            else -> super.readObject(name, value)
        }
    }

    companion object {
        private val LOGGER = LogManager.getLogger(PDFDocument::class)
        val timeout = 20_000L
    }

}