package me.anno.config

import me.anno.config.DefaultStyle.baseTheme
import me.anno.input.ActionManager
import me.anno.io.config.ConfigBasics
import me.anno.io.utils.StringMap
import me.anno.ui.style.Style

object DefaultConfig: StringMap() {

    init {
        init()
    }

    lateinit var style: Style

    fun init() {

        val t0 = System.nanoTime()

        this["style"] = "dark"
        this["ffmpegPath"] = "C:\\Users\\Antonio\\Downloads\\lib\\ffmpeg\\bin\\ffmpeg.exe"
        this["tooltip.reactionTime"] = 300
        this["lastUsed.fonts.count"] = 5
        this["default.video.nearest"] = false
        this["default.image.nearest"] = false

        this["grid.axis.x.color"] = "#ff7777"
        this["grid.axis.y.color"] = "#77ff77"
        this["grid.axis.z.color"] = "#7777ff"
        this["format.svg.stepsPerDegree"] = 0.1f

        addImportMappings("Image", "png", "jpg", "jpeg", "tiff", "webp", "svg")
        addImportMappings("Video", "mp4", "gif", "mpeg", "avi")
        addImportMappings("Text", "txt")
        addImportMappings("Markdown", "md")
        addImportMappings("Audio", "mp3", "wav", "ogg")

        this["import.mapping.*"] = "Text"

        val newConfig = ConfigBasics.loadConfig("main.config", this, true)
        if(newConfig !== this){
            putAll(newConfig)
        }

        val stylePath = newConfig["style"]?.toString() ?: "dark"
        style = baseTheme.getStyle(stylePath)

        ActionManager.init()

        val t1 = System.nanoTime()
        println("[INFO] Used ${(t1-t0)*1e-9f} to read the config")

    }

    fun addImportMappings(result: String, vararg extensions: String){
        for(extension in extensions){
            this["import.mapping.$extension"] = result
        }
    }

    val defaultFont get() = this["defaultFont"] as? String ?: "Verdana"

}