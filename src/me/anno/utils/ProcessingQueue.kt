package me.anno.utils

import org.apache.logging.log4j.LogManager
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

class ProcessingQueue(val name: String){

    private val tasks = LinkedBlockingQueue<() -> Unit>()

    fun start() {
        thread {
            while (!shallShutDown) {
                try {
                    // will block, until we have new work
                    val task = tasks.poll() ?: null
                    if(task == null){
                        Thread.sleep(1)
                    } else {
                        task()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }.name = name
    }

    operator fun plusAssign(task: () -> Unit) {
        tasks += task
    }

    companion object {
        private val LOGGER = LogManager.getLogger(ProcessingQueue::class)
        var shallShutDown = false
        fun destroy(){
            LOGGER.info("Shutting down")
            shallShutDown = true
        }
    }

}