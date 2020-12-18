package me.anno.studio.history

import me.anno.gpu.GFX
import me.anno.io.ISaveable
import me.anno.io.Saveable
import me.anno.io.base.BaseWriter
import me.anno.studio.history.State.Companion.capture
import org.apache.logging.log4j.LogManager
import kotlin.math.max

class History : Saveable() {

    var currentState: State? = null
    var nextInsertIndex = 0
        set(value) {
            field = max(value, 0)
        }

    private val states = ArrayList<State>()

    fun isEmpty() = states.isEmpty()

    fun clearToSize() {
        while (states.size > maxChanged && maxChanged > 0) {
            states.removeAt(0)
        }
    }

    fun update(title: String){
        val last = states.lastOrNull()
        if(last?.title == title){
            last.capture(last)
        } else {
            put(title)
        }
    }

    fun put(change: State): Int {
        // remove states at the top of the stack...
        while (states.size > nextInsertIndex) states.removeAt(states.lastIndex)
        states += change
        nextInsertIndex = states.size
        clearToSize()
        return nextInsertIndex
    }

    fun put(title: String) {
        val nextState = capture(title, currentState)
        if (nextState != currentState) {
            nextInsertIndex = put(nextState)
            currentState = nextState
        }
    }

    fun redo() {
        if (nextInsertIndex < states.size) {
            states[nextInsertIndex].apply()
            nextInsertIndex++
        } else LOGGER.info("Nothing left to redo!")
    }

    fun undo() {
        if(nextInsertIndex > 1){
            nextInsertIndex--
            states[nextInsertIndex - 1].apply()
        } else LOGGER.info("Nothing left to undo!")
    }

    fun redo(index: Int) {
        if (index != states.lastIndex) {
            states.getOrNull(index)?.apply {
                put(this)
                apply()
            }
        }
    }

    fun display() {
        GFX.openMenu("Change History", states.mapIndexed { index, change ->
            val title = if(index == nextInsertIndex-1) "* ${change.title}" else change.title
            title to {
                redo(index)
            }
        }.reversed())
    }

    // todo file explorer states?

    override fun readInt(name: String, value: Int) {
        when(name){
            "nextInsertIndex" -> nextInsertIndex = value
            else -> super.readInt(name, value)
        }
    }

    override fun readObject(name: String, value: ISaveable?) {
        when(name){
            "state" -> {
                states += value as? State ?: return
            }
            else -> super.readObject(name, value)
        }
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeInt("nextInsertIndex", nextInsertIndex)
        states.forEach {  state ->
            writer.writeObject(this, "state", state)
        }
    }

    override fun getApproxSize(): Int = 1_500_000_000
    override fun isDefaultValue(): Boolean = false
    override fun getClassName(): String = "History2"

    companion object {
        val LOGGER = LogManager.getLogger(History::class)
        val maxChanged = 512
    }

}