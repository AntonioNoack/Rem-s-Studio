package me.anno.ui.editor.color

import me.anno.config.DefaultConfig
import me.anno.gpu.GFX
import me.anno.gpu.GFXx2D.posSize
import me.anno.objects.animation.AnimatedProperty
import me.anno.studio.rems.RemsStudio.editorTime
import me.anno.ui.base.Panel
import me.anno.ui.base.SpacePanel
import me.anno.ui.base.Visibility
import me.anno.ui.base.groups.PanelListX
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.color.spaces.HSLuv
import me.anno.ui.input.EnumInput
import me.anno.ui.input.components.ColorPalette
import me.anno.ui.style.Style
import me.anno.utils.Color.toHexColor
import me.anno.utils.ColorParsing.parseColorComplex
import me.anno.utils.Maths.clamp
import me.anno.utils.types.AnyToFloat.get
import me.anno.utils.types.Floats.f3
import org.apache.logging.log4j.LogManager
import org.hsluv.HSLuvColorSpace
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.min

class ColorChooser(style: Style, val withAlpha: Boolean, val owningProperty: AnimatedProperty<*>?) : PanelListY(style) {

    // color section
    // bottom section:
    // hue
    // check box for hsl/hsluv
    // field(?) to enter rgb codes

    private val maxWidth = style.getSize("colorChooser.maxWidth", 500)
    override fun calculateSize(w: Int, h: Int) {
        super.calculateSize(min(w, maxWidth), h)
    }

    override fun getVisualState(): Any? = Pair(super.getVisualState(), Triple(hue, saturation, lightness))

    val rgba get() = Vector4f(colorSpace.toRGB(Vector3f(hue, saturation, lightness)), opacity)
    var visualisation = lastVisualisation ?: ColorVisualisation.WHEEL
    var colorSpace = lastColorSpace ?: ColorSpace[DefaultConfig["default.colorSpace", "HSLuv"]] ?: HSLuv

    var isDownInRing = false
    private val hslBox = HSVBoxMain(this, Vector3f(), Vector3f(0f, 1f, 0f), Vector3f(0f, 0f, 1f), style)

    private val hueChooserSpace = SpacePanel(0, 2, style)
    private val hueChooser = HueBar(this, style)
    private val alphaBar = if (withAlpha) AlphaBar(this, style) else null

    private val colorSpaceInput = EnumInput(
        "Color Space",
        "Color Layout: which colors are where?, e.g. color circle", "ui.input.color.colorSpace",
        colorSpace.naming.name,
        ColorSpace.list.map { it.naming }, style
    )
        .setChangeListener { it, _, _ ->
            val newColorSpace = ColorSpace[it]
            lastColorSpace = newColorSpace
            if (newColorSpace != null && newColorSpace != colorSpace) {
                val rgb = colorSpace.toRGB(Vector3f(hue, saturation, lightness))
                val newHSL = newColorSpace.fromRGB(rgb)
                setHSL(newHSL.x, newHSL.y, newHSL.z, opacity, newColorSpace, true)
                // onSmallChange("color-space")
            }
        }

    private val styleInput = EnumInput(
        "", false,
        visualisation.naming.name,
        ColorVisualisation.values().map { it.naming },
        style
    ).setChangeListener { _, index, _ ->
        visualisation = ColorVisualisation.values()[index]
        lastVisualisation = visualisation
    }.setTooltip("Style, does not change values")

    private val colorPalette = ColorPalette(8, 4, style)

    init {
        val spaceBox = PanelListX(style)
        this += spaceBox
        spaceBox += colorSpaceInput.setWeight(1f)
        spaceBox += styleInput
        this += SpacePanel(0, 2, style)
        this += hslBox
        this += hueChooserSpace
        this += hueChooser
        if (alphaBar != null) {
            this += SpacePanel(0, 2, style)
            this += alphaBar
        }
        this += colorPalette
        colorPalette.onColorSelected = { setARGB(it, true) }
    }

    var lastTime = editorTime
    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
        if (lastTime != editorTime && owningProperty != null) {
            lastTime = editorTime
            setRGBA(
                when (val c = owningProperty[editorTime]) {
                    is Vector3f -> Vector4f(c, 1f)
                    is Vector4f -> c
                    else -> throw RuntimeException()
                }, false
            )
        }
        val needsHueChooser = Visibility[visualisation.needsHueChooser]
        hueChooser.visibility = needsHueChooser
        hueChooserSpace.visibility = needsHueChooser
        super.onDraw(x0, y0, x1, y1)
    }

    var hue = 0.5f
    var saturation = 0.5f
    var lightness = 0.5f
    var opacity = 1.0f

    fun setARGB(argb: Int, notify: Boolean) {
        setRGBA(
            (argb.shr(16) and 255) / 255f,
            (argb.shr(8) and 255) / 255f,
            (argb and 255) / 255f,
            (argb.shr(24) and 255) / 255f,
            notify
        )
    }

    fun setRGBA(v: Vector4f, notify: Boolean) = setRGBA(v.x, v.y, v.z, v.w, notify)
    fun setRGBA(r: Float, g: Float, b: Float, a: Float, notify: Boolean) {
        val hsluv = HSLuvColorSpace.rgbToHsluv(
            doubleArrayOf(
                r.toDouble(), g.toDouble(), b.toDouble()
            )
        )
        setHSL(
            hsluv[0].toFloat() / 360f,
            hsluv[1].toFloat() / 100f,
            hsluv[2].toFloat() / 100f,
            clamp(a, 0f, 1f),
            colorSpace, notify
        )
    }

    fun setHSL(h: Float, s: Float, l: Float, a: Float, newColorSpace: ColorSpace, notify: Boolean) {
        hue = h
        saturation = s
        lightness = l
        opacity = clamp(a, 0f, 1f)
        this.colorSpace = newColorSpace
        val rgb = colorSpace.toRGB(Vector3f(hue, saturation, lightness))
        if (notify) {
            changeRGBListener(rgb.x, rgb.y, rgb.z, opacity)
        }
    }

    fun drawColorBox(element: Panel, d0: Vector3f, du: Vector3f, dv: Vector3f, dh: Float, mainBox: Boolean) {
        drawColorBox(
            element.x, element.y, element.w, element.h,
            d0, du, dv, dh,
            if (mainBox) visualisation else ColorVisualisation.BOX
        )
    }

    fun drawColorBox(
        x: Int, y: Int, w: Int, h: Int,
        d0: Vector3f, du: Vector3f, dv: Vector3f, dh: Float,
        spaceStyle: ColorVisualisation
    ) {
        val shader = colorSpace.getShader(spaceStyle)
        shader.use()
        posSize(shader, x, y + h, w, -h)
        val sharpness = min(w, h) * 0.25f + 1f
        when (spaceStyle) {
            ColorVisualisation.WHEEL -> {
                shader.v3("v0", d0.x + hue * dh, d0.y, d0.z)
                shader.v3("du", du)
                shader.v3("dv", dv)
                val hue0 = colorSpace.hue0
                shader.v2("ringSL", hue0.y, hue0.z)
                shader.v1("sharpness", sharpness)
                GFX.flat01.draw(shader)
            }
            ColorVisualisation.CIRCLE -> {
                shader.v1("lightness", lightness)
                shader.v1("sharpness", sharpness)
                GFX.flat01.draw(shader)
            }
            ColorVisualisation.BOX -> {
                shader.v3("v0", d0.x + hue * dh, d0.y, d0.z)
                shader.v3("du", du)
                shader.v3("dv", dv)
                // shader.v1("sharpness", sharpness)
                GFX.flat01.draw(shader)
            }
        }
    }

    var changeRGBListener: (x: Float, y: Float, z: Float, w: Float) -> Unit = { _, _, _, _ -> }
    fun setChangeRGBListener(listener: (x: Float, y: Float, z: Float, w: Float) -> Unit): ColorChooser {
        changeRGBListener = listener
        return this
    }

    override fun onCopyRequested(x: Float, y: Float): String {
        val cssCompatible = DefaultConfig["editor.copyColorsCSS-compatible", false]
        val useAlpha = DefaultConfig["editor.copyColorsCSS-withAlpha", true]
        return if (cssCompatible) {
            colorSpace.toRGB(hue, saturation, lightness, if(useAlpha) opacity else 1f).toHexColor()
        } else {
            "${colorSpace.serializationName}(${hue.f3()},${saturation.f3()},${lightness.f3()},${opacity.f3()})"
        }
    }

    override fun onPaste(x: Float, y: Float, data: String, type: String) {
        when (val color = parseColorComplex(data)) {
            is Int -> setARGB(color, true)
            is Vector4f -> setRGBA(color, true)
            null -> LOGGER.warn("Didn't understand color $data")
            else -> throw RuntimeException("Color type $data -> $color isn't yet supported for ColorChooser")
        }
    }

    override fun onEmpty(x: Float, y: Float) {
        val default = owningProperty?.defaultValue ?: Vector4f(0f)
        setRGBA(default[0], default[1], default[2], default[3], true)
    }

    companion object {
        private val LOGGER = LogManager.getLogger(ColorChooser::class)
        val CircleBarRatio = 0.2f
        var lastVisualisation: ColorVisualisation? = null
        var lastColorSpace: ColorSpace? = null
    }

}