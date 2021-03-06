package me.anno.gpu.framebuffer

import me.anno.cache.CacheSection
import me.anno.cache.data.ICacheData
import org.apache.logging.log4j.LogManager

object FBStack : CacheSection("FBStack") {

    private val LOGGER = LogManager.getLogger(FBStack::class)

    abstract class FBStackData(val w: Int, val h: Int, val samples: Int, val targetType: TargetType) : ICacheData {

        var nextIndex = 0
        val data = ArrayList<Framebuffer>()

        override fun destroy() {
            if (data.isNotEmpty()) {
                data.forEach { it.destroy() }
                printDestroyed(data.size)
                data.clear()
            }
        }

        fun getFrame(name: String): Framebuffer {
            return if (nextIndex >= data.size) {
                val framebuffer = Framebuffer(
                    name, w, h,
                    samples, arrayOf(targetType),
                    Framebuffer.DepthBufferType.TEXTURE
                )
                data.add(framebuffer)
                nextIndex = data.size
                data.last()
            } else {
                val framebuffer = data[nextIndex++]
                framebuffer.name = name
                framebuffer
            }
        }

        abstract fun printDestroyed(size: Int)

    }

    data class FBKey1(val w: Int, val h: Int, val channels: Int, val usesFP: Boolean, val samples: Int)
    class FBStackData1(val key: FBKey1) :
        FBStackData(key.w, key.h, key.samples, getTargetType(key.channels, key.usesFP)) {
        override fun printDestroyed(size: Int) {
            val fs = if(size == 1) "1 framebuffer" else "$size framebuffers"
            LOGGER.info("Freed $fs of size ${key.w} x ${key.h}, samples: ${key.samples}, fp: ${key.usesFP}")
        }
    }

    data class FBKey2(val w: Int, val h: Int, val targetType: TargetType, val samples: Int)
    class FBStackData2(val key: FBKey2) : FBStackData(key.w, key.h, key.samples, key.targetType) {
        override fun printDestroyed(size: Int) {
            val fs = if(size == 1) "1 framebuffer" else "$size framebuffers"
            LOGGER.info("Freed $fs of size ${key.w} x ${key.h}, samples: ${key.samples}, type: ${key.targetType}")
        }
    }

    fun getValue(w: Int, h: Int, channels: Int, usesFP: Boolean, samples: Int): FBStackData {
        val key = FBKey1(w, h, channels, usesFP, samples)
        return getEntry(key, 2100, false) {
            FBStackData1(key)
        } as FBStackData
    }

    fun getValue(w: Int, h: Int, targetType: TargetType, samples: Int): FBStackData {
        val key = FBKey2(w, h, targetType, samples)
        return getEntry(key, 2100, false) {
            FBStackData2(key)
        } as FBStackData
    }

    operator fun get(name: String, w: Int, h: Int, channels: Int, usesFP: Boolean, samples: Int): Framebuffer {
        val value = getValue(w, h, channels, usesFP, samples)
        synchronized(value) {
            return value.getFrame(name)
        }
    }

    operator fun get(name: String, w: Int, h: Int, targetType: TargetType, samples: Int): Framebuffer {
        val value = getValue(w, h, targetType, samples)
        synchronized(value) {
            return value.getFrame(name)
        }
    }

    fun getTargetType(channels: Int, usesFP: Boolean): TargetType {
        return if (usesFP) {
            when (channels) {
                1 -> TargetType.FloatTarget1
                2 -> TargetType.FloatTarget2
                3 -> TargetType.FloatTarget3
                else -> TargetType.FloatTarget4
            }
        } else {
            when (channels) {
                1 -> TargetType.UByteTarget1
                2 -> TargetType.UByteTarget2
                3 -> TargetType.UByteTarget3
                else -> TargetType.UByteTarget4
            }
        }
    }

    fun clear(w: Int, h: Int) {
        synchronized(cache) {
            for (value in cache.values) {
                val data = value.data
                if (data is FBStackData && data.w == w && data.h == h) {
                    data.nextIndex = 0
                }
            }
        }
    }

    fun clear(w: Int, h: Int, samples: Int) {
        synchronized(cache) {
            for (value in cache.values) {
                val data = value.data
                if (data is FBStackData && data.w == w && data.h == h && data.samples == samples) {
                    data.nextIndex = 0
                }
            }
        }
    }

    fun reset() {
        resetFBStack()
    }

    private fun resetFBStack() {
        synchronized(cache) {
            synchronized(cache) {
                for (value in cache.values) {
                    val data = value.data
                    if (data is FBStackData) {
                        data.nextIndex = 0
                    }
                }
            }
        }
    }

}