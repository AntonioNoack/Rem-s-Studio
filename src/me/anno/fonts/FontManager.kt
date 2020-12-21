package me.anno.fonts

import me.anno.gpu.GFX.loadTexturesSync
import me.anno.gpu.texture.ITexture2D
import me.anno.cache.Cache
import me.anno.gpu.TextureLib
import me.anno.gpu.TextureLib.whiteTexture
import me.anno.utils.FloatFormat.f3
import me.anno.utils.toInt
import org.apache.logging.log4j.LogManager
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.lang.RuntimeException
import kotlin.concurrent.thread
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.round

// todo spell check xD
// todo grammar check lol
object FontManager {

    val LOGGER = LogManager.getLogger(FontManager::class)!!

    private var hasFonts = false
    private val awtFontList = ArrayList<String>()
    private val awtFonts = HashMap<FontKey, Font>()

    private val fonts = HashMap<FontKey, AWTFont>()

    fun requestFontList(callback: (List<String>) -> Unit){
        if(hasFonts) callback(awtFontList)
        else {
            thread {
                synchronized(awtFontList){
                    hasFonts = true
                    val t0 = System.nanoTime()
                    val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    val fontNames = ge.availableFontFamilyNames
                    awtFontList.clear()
                    awtFontList += fontNames
                    val t1 = System.nanoTime()
                    // 0.17s on Win 10, R5 2600, a few extra fonts
                    // this lag would not be acceptable :)
                    // worst-case-scenario: list too long, and no fonts are returned
                    // (because of that, the already used one is added)
                    LOGGER.info("Used ${((t1-t0)*1e-9f).f3()} to get font list")
                    callback(awtFontList)
                }
            }
        }
    }

    private fun getFontSizeIndex(fontSize: Float): Int = round(100.0 * ln(fontSize)).toInt()
    private fun getAvgFontSize(fontSizeIndex: Int): Float = exp(fontSizeIndex * 0.01f)

    data class TextCacheKey(val text: String, val fontName: String, val properties: Int, val widthLimit: Int)

    fun getString(font: me.anno.ui.base.Font, text: String, widthLimit: Int) =
        getString(font.name, font.size, text, font.isBold, font.isItalic, widthLimit)

    fun getString(fontName: String, fontSize: Float, text: String, bold: Boolean, italic: Boolean, widthLimit: Int): ITexture2D? {
        if(text.isEmpty()) return null
        val fontSizeIndex = getFontSizeIndex(fontSize)
        val sub = fontSizeIndex * 4 + bold.toInt(1) + italic.toInt(2)
        val widthLimit2 = if(widthLimit < 0) -1 else {
            loadTexturesSync.push(true)
            val w = getString(fontName, fontSize, text, bold, italic, -1)!!.w
            loadTexturesSync.pop()
            val step = fontSize.toInt()
            min(w, widthLimit/step*step)
        }
        val key = TextCacheKey(text, fontName, sub, widthLimit2)
        return Cache.getEntry(key, fontTimeout, asyncGenerator = !loadTexturesSync.peek()){
            val font = getFont(fontName, fontSize, fontSizeIndex, italic, bold)
            val averageFontSize = getAvgFontSize(fontSizeIndex)
            val texture = font.generateTexture(text, averageFontSize, widthLimit2)
            texture ?: TextureLib.nullTexture
        } as? ITexture2D
    }

    fun getFont(font: me.anno.ui.base.Font): AWTFont = getFont(font.name, font.size, font.isBold, font.isItalic)

    fun getFont(name: String, fontSize: Float, bold: Boolean, italic: Boolean): AWTFont {
        val fontSizeIndex = getFontSizeIndex(fontSize)
        val averageFontSize = getAvgFontSize(fontSizeIndex)
        return getFont(name, averageFontSize, fontSizeIndex, bold, italic)
    }

    data class FontKey(val name: String, val sizeIndex: Int, val bold: Boolean, val italic: Boolean)

    private fun getFont(name: String, fontSize: Float, fontSizeIndex: Int, bold: Boolean, italic: Boolean): AWTFont {
        val key = FontKey(name, fontSizeIndex, bold, italic)//"$name:$fontSizeIndex:$boldItalicStyle"
        val font = fonts[key]
        if(font != null) return font
        val boldItalicStyle = (if(italic) Font.ITALIC else 0) or (if(bold) Font.BOLD else 0)
        val font2 = AWTFont(awtFonts[key] ?: getDefaultFont(name)?.deriveFont(boldItalicStyle, fontSize) ?: throw RuntimeException("Font $name was not found"))
        fonts[key] = font2
        return font2
    }

    private fun getDefaultFont(name: String): Font? {
        val key = FontKey(name, Int.MIN_VALUE, false, false)
        val cached = awtFonts[key]
        if(cached != null) return cached
        val font = Font.decode(name) ?: return null
        awtFonts[key] = font
        return font
    }

    val fontTimeout = 1000L

}