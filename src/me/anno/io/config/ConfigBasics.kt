package me.anno.io.config

import me.anno.io.text.TextReader
import me.anno.io.text.TextWriter
import me.anno.io.utils.StringMap
import me.anno.utils.OS
import org.apache.logging.log4j.LogManager
import java.io.File
import java.nio.charset.Charset

/**
 * max file size: 2GB, because of Javas internal String and array limitations
 * 2GB for a config file would be just insane
 * */
object ConfigBasics {

    val LOGGER = LogManager.getLogger(ConfigBasics::class)!!
    val utf8Charset = Charset.forName("UTF-8")

    val configFolder = File(OS.home, ".config/RemsStudio")

    val beautify = true

    fun getConfigFile(localFileName: String): File {
        return File(configFolder, localFileName)
    }

    fun save(file: File, data: String){
        val parentFile = file.parentFile
        if(!parentFile.exists()) parentFile.mkdirs()
        file.writeText(data, utf8Charset)
    }

    fun save(localFileName: String, data: String) = save(getConfigFile(localFileName), data)

    fun load(file: File, saveIfMissing: Boolean, getDefault: () -> String): String {
        return if(file.exists()){
            file.readText(utf8Charset)
        } else {
            val default = getDefault()
            if(saveIfMissing) save(file, default)
            default
        }
    }

    fun load(localFileName: String, saveIfMissing: Boolean, getDefault: () -> String) =
        load(getConfigFile(localFileName), saveIfMissing, getDefault)


    fun loadConfig(file: File, defaultValue: StringMap, saveIfMissing: Boolean): StringMap {
        val read = load(file, saveIfMissing){
            LOGGER.info("Didn't find $file, using default values")
            TextWriter.toText(defaultValue, beautify)
        }
        val readData = TextReader.fromText(read)
        val map = readData.firstOrNull { it is StringMap } as? StringMap
        return if(map == null){
            LOGGER.info("Config was corrupted, didn't find a config, in $file, got $readData")
            save(file, TextWriter.toText(defaultValue, beautify))
            defaultValue
        } else map
    }

    fun loadConfig(localFileName: String, defaultValue: StringMap, saveIfMissing: Boolean) =
        loadConfig(getConfigFile(localFileName), defaultValue, saveIfMissing)

    fun loadJsonArray(localFileName: String, defaultValue: List<ConfigEntry>, saveIfMissing: Boolean): List<ConfigEntry> {

        val data = load(localFileName, saveIfMissing){ TextWriter.toText(defaultValue, beautify) }

        val loaded = TextReader.fromText(data)
        val newestEntries = HashMap<String, ConfigEntry>(loaded.size + 10)

        fun addIfNewest(entry: ConfigEntry): Boolean {
            val id = entry.id
            return if(id != null){
                val old = newestEntries[id]
                if(old != null){
                    if(entry.version > old.version){
                        newestEntries[id] = entry
                        true
                    } else false
                } else {
                    newestEntries[id] = entry
                    true
                }
            } else false
        }

        loaded.forEach {  entry ->
            (entry as? ConfigEntry)?.apply {
                addIfNewest(this)
            }
        }

        var wasAugmentedByDefault = false
        defaultValue.forEach { entry ->
            if(addIfNewest(entry)){
                wasAugmentedByDefault = true
            }
        }

        val result = newestEntries.values.toList()

        if(wasAugmentedByDefault){
            save(localFileName, TextWriter.toText(result, beautify))
        }

        return result
    }

}