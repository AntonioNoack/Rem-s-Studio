package me.anno.fonts

import me.anno.gpu.texture.Texture2D
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.lang.RuntimeException
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.round

// todo there are char-char relations in me.anno.fonts, which could improve the quality of the font
// todo use them instead of this class
object FontManagerV01 {

    //val fontMap = HashMap<String, Font>()

    init {
        // todo this is a bottleneck with 0.245s
        // todo therefore this should be parallized with other stuff...
        /*val t0 = System.nanoTime()
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val fontNames = ge.availableFontFamilyNames
        val t1 = System.nanoTime()
        for(fontName in fontNames){
            fontMap[fontName] = Font.decode(fontName)
        }
        val t2 = System.nanoTime()
        println("used ${(t1-t0)*1e-9f}+${(t2-t1)*1e-9f}s to get font list")*/
    }

    fun getFontSizeIndex(fontSize: Float): Int = round(100.0 * ln(fontSize)).toInt()
    fun getAvgFontSize(fontSizeIndex: Int): Float = exp(fontSizeIndex * 0.01f)

    val letterCache = HashMap<Triple<String, Int, String>, Texture2D>()

    fun getString(fontName: String, fontSize: Float, text: String): Texture2D? {
        if(text.isBlank()) return null
        val fontSizeIndex = getFontSizeIndex(fontSize)
        val key = Triple(fontName, fontSizeIndex, text)
        val cached = letterCache[key]
        if(cached != null) return cached
        val font = getFont(fontName, fontSize, fontSizeIndex)
        val averageFontSize = getAvgFontSize(fontSizeIndex)
        val texture = font.generateTexture(text, averageFontSize) ?: return null
        letterCache[key] = texture
        return texture
    }

    /*fun getChar(fontName: String, fontSize: Float, char: Int): Texture2D {
        val fontSizeIndex = getFontSizeIndex(fontSize)
        val key = Triple(fontName, fontSizeIndex, char)
        val cached = letterCache[key]
        if(cached != null) return cached
        val font = getFont(fontName)
        val averageFontSize = getAvgFontSize(fontSizeIndex)
        val texture = font.generateTexture(char, averageFontSize) ?: return getDefaultChar()
        letterCache[key] = texture
        return texture
    }*/

    val fonts = HashMap<String, XFont>()

    fun getFont(name: String, fontSize: Float, fontSizeIndex: Int): XFont {
        val font = fonts[name]
        if(font != null) return font
        val awtName = "$name:$fontSizeIndex"
        val font2 = AWTFont(fontMap[awtName] ?: Font.decode(name)?.deriveFont(fontSize) ?: throw RuntimeException("Font $name was not found"))
        fonts[awtName] = font2
        return font2
    }

    val fontMap = HashMap<String, Font>()

}