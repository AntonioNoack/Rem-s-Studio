package me.anno.video

import me.anno.gpu.GFX
import me.anno.objects.cache.VideoData.Companion.framesPerContainer
import me.anno.video.formats.ARGBFrame
import me.anno.video.formats.BGRAFrame
import me.anno.video.formats.I420Frame
import me.anno.video.formats.RGBFrame
import java.io.File
import java.io.InputStream
import java.lang.Exception
import java.lang.RuntimeException
import kotlin.concurrent.thread

class FFMPEGVideo(file: File?, val frame0: Int, bufferLength: Int):
    FFMPEGStream(file){

    override fun process(process: Process, arguments: List<String>) {
        thread {
            val out = process.errorStream.bufferedReader()
            val parser = FFMPEGMetaParser()
            while(true){
                val line = out.readLine() ?: break
                parser.parseLine(line, this)
            }
        }
        thread {
            val frameCount = arguments[arguments.indexOf("-vframes")+1].toInt()
            val input = process.inputStream
            readFrame(input)
            for(i in 1 until frameCount){
                readFrame(input)
            }
            input.close()
        }
    }

    val frames = ArrayList<Frame>(bufferLength)

    var isFinished = false
    fun readFrame(input: InputStream){
        while(w == 0 || h == 0 || codec.isEmpty()){
            Thread.sleep(0, 100_000)
        }
        if(!isDestroyed && !isFinished){
            synchronized(frames){
                try {
                    val frame = when(codec){
                        "I420" -> I420Frame(w, h)
                        "ARGB" -> ARGBFrame(w, h)
                        "BGRA" -> BGRAFrame(w, h)
                        "RGB"  ->  RGBFrame(w, h)
                        else -> throw RuntimeException("Unsupported Codec $codec!")
                    }
                    frame.load(input)
                    frames.add(frame)
                } catch (e: LastFrame){
                    frameCountByFile[file!!] = frames.size + frame0
                    isFinished = true
                } catch (e: Exception){
                    e.printStackTrace()
                    frameCountByFile[file!!] = frames.size + frame0
                    isFinished = true
                }
            }
        }
        if(isDestroyed) destroy()
    }

    var isDestroyed = false
    override fun destroy() {
        synchronized(frames){
            if(frames.isNotEmpty()){
                val f0 = frames[0]
                // delete them over time? it seems like it's really expensive on my Envy x360 xD
                frames.forEach { GFX.addGPUTask(f0.w, f0.h){ it.destroy() } }
            }
            frames.clear()
            isDestroyed = true
        }
    }



}