package me.anno.objects.cache

import me.anno.gpu.GFX
import me.anno.gpu.texture.Texture2D
import me.anno.gpu.texture.Texture3D
import me.anno.objects.modes.LoopingState
import me.anno.objects.cache.VideoData.Companion.framesPerContainer
import me.anno.studio.Studio.editorTimeDilation
import me.anno.video.FFMPEGMetadata
import me.anno.video.FFMPEGStream
import me.anno.video.Frame
import org.apache.logging.log4j.LogManager
import java.io.File
import java.io.FileNotFoundException
import javax.imageio.ImageIO
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

object Cache {

    private val LOGGER = LogManager.getLogger(Cache::class)

    private val cache = HashMap<Any, CacheEntry>()
    private val lockedKeys = HashSet<Any>(2048)

    fun clear(){
        synchronized(this){
            cache.values.forEach { it.destroy() }
            cache.clear()
            lockedKeys.clear() // mmh...
        }
    }

    fun getLUT(file: File, asyncGenerator: Boolean, timeout: Long = 5000): Texture3D? {
        val cache = getEntry("LUT", file.toString(), 0, timeout, asyncGenerator){
            val cache = Texture3DCache(null)
            thread {
                val img = ImageIO.read(file)
                val sqrt = sqrt(img.width+0.5f).toInt()
                val tex = Texture3D(sqrt, img.height, sqrt)
                tex.create(img, false)
                cache.texture = tex
            }
            cache
        } as? Texture3DCache
        return cache?.texture
    }

    fun getIcon(name: String, asyncGenerator: Boolean, timeout: Long = 5000): Texture2D? {
        val cache = getEntry("Icon", name, 0, timeout, asyncGenerator){
            val cache = TextureCache(null)
            thread {
                try {
                    val img = GFX.loadAssetsImage(name)
                    val tex = Texture2D(img.width, img.height, 1)
                    tex.create(img, false)
                    cache.texture = tex
                } catch (e: FileNotFoundException){
                    LOGGER.warn("Icon $name not found!")
                } catch (e: Exception){
                    e.printStackTrace()
                }
            }
            cache
        } as? TextureCache
        return cache?.texture as? Texture2D
    }

    fun getEntry(file: File, allowDirectories: Boolean, key: Any, timeout: Long, asyncGenerator: Boolean, generator: () -> CacheData): CacheData? {
        if(!file.exists() || (!allowDirectories && file.isDirectory)) return null
        return getEntry(file to key, timeout, asyncGenerator, generator)
    }

    fun getEntry(major: String, minor: String, sub: Int, timeout: Long, asyncGenerator: Boolean, generator: () -> CacheData): CacheData? {
        return getEntry(Triple(major, minor, sub), timeout, asyncGenerator, generator)
    }

    fun getEntry(key: Any, timeout: Long, asyncGenerator: Boolean, generator: () -> CacheData): CacheData? {

        // old, sync cache
        /*if(false){// key is FBStack.FBKey -> all textures are missing... why ever...
            synchronized(cache){
                val cached = cache[key]
                if(cached != null){
                    cached.lastUsed = GFX.lastTime
                    return cached.data
                }
                var data: CacheData? = null
                try {
                    data = generator()
                } catch (e: FileNotFoundException){
                    LOGGER.info(e.message ?: "")
                } catch (e: Exception){
                    e.printStackTrace()
                }
                synchronized(cache){
                    cache[key] = CacheEntry(data, timeout, GFX.lastTime)
                }
                return data
            }
        }*/

        // new, async cache
        // only the key needs to be locked, not the whole cache

        if(asyncGenerator){
            synchronized(lockedKeys){
                if(key !in lockedKeys){
                    lockedKeys += key
                } else {
                    return null
                } // somebody else is using the cache ;p
            }
        } else {
            var hasKey = false
            while(!hasKey){
                synchronized(lockedKeys){
                    if(key !in lockedKeys){
                        lockedKeys += key
                        hasKey = true
                    }
                }
                if(hasKey) break
                Thread.sleep(1)
            }
        }


        val cached: CacheEntry?
        synchronized(cache){ cached = cache[key] }
        if(cached != null){
            cached.lastUsed = GFX.lastTime
            synchronized(lockedKeys){ lockedKeys.remove(key) }
            return cached.data
        }

        return if(asyncGenerator){
            thread {
                var data: CacheData? = null
                try {
                    data = generator()
                } catch (e: FileNotFoundException){
                    LOGGER.info(e.message ?: "")
                } catch (e: Exception){
                    e.printStackTrace()
                }
                synchronized(cache){ cache[key] = CacheEntry(data, timeout, GFX.lastTime) }
                synchronized(lockedKeys){ lockedKeys.remove(key) }
            }
            null
        } else {
            var data: CacheData? = null
            try {
                data = generator()
            } catch (e: FileNotFoundException){
                LOGGER.info(e.message ?: "")
            } catch (e: Exception){
                e.printStackTrace()
            }
            synchronized(cache){ cache[key] = CacheEntry(data, timeout, GFX.lastTime) }
            synchronized(lockedKeys){ lockedKeys.remove(key) }
            data
        }

    }

    fun getVideoFrame(file: File, scale: Int, index: Int, frameLength: Int, fps: Double, timeout: Long, isLooping: LoopingState): Frame? {
        if(index < 0) return null
        val bufferIndex = index/framesPerContainer
        val videoData = getVideoFrames(file, scale, bufferIndex, fps, timeout) ?: return null
        if(videoData.time0 != GFX.lastTime){
            if(editorTimeDilation > 0.01f){
                if((bufferIndex+1)*framesPerContainer <= frameLength){
                    getVideoFrames(file, scale, bufferIndex+1, fps, timeout)
                } else if(isLooping == LoopingState.PLAY_LOOP){// otherwise nearby containers will be loaded ;)
                    getVideoFrames(file, scale, 0, fps, timeout)
                }
            } else if(editorTimeDilation < -0.01f){
                if(bufferIndex > 0){
                    getVideoFrames(file, scale, bufferIndex-1, fps, timeout)
                } else {
                    val maybeIndex = FFMPEGStream.frameCountByFile[file]
                    if(maybeIndex != null){// 1/16 probability, that this won't work ...
                        getVideoFrames(file, scale, (maybeIndex-1)/framesPerContainer, fps, timeout)
                    }
                }
            }
        }
        return videoData.frames.getOrNull(index % framesPerContainer)
    }

    fun getVideoFrames(file: File, scale: Int, index: Int, fps: Double, timeout: Long) = getEntry(file, false, Triple(index, fps, scale), timeout, true){
        val meta = FFMPEGMetadata.getMeta(file, false)!!
        VideoData(file, meta.videoWidth/scale, meta.videoHeight/scale, index, fps)
    } as? VideoData

    fun getImage(file: File, timeout: Long, asyncGenerator: Boolean) =
        if(file.isDirectory || !file.exists()) null
        else (getEntry(file as Any, timeout, asyncGenerator){
            ImageData(file)
        } as? ImageData)?.texture

    fun update(){
        val minTimeout = 300L
        val time = GFX.lastTime
        synchronized(cache){
            val toRemove = cache.filter { (_, entry) -> abs(entry.lastUsed - time) > max(entry.timeout, minTimeout) * 1_000_000 }
            toRemove.forEach {
                cache.remove(it.key)
                it.value.destroy()
            }
        }
    }

}