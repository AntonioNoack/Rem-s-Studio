package me.anno.io.base

import me.anno.io.ISaveable
import me.anno.io.utils.StringMap
import me.anno.objects.*
import me.anno.objects.animation.AnimatedProperty
import me.anno.objects.animation.Keyframe
import me.anno.objects.effects.MaskLayer
import me.anno.objects.geometric.Circle
import me.anno.objects.geometric.Polygon
import java.io.File
import java.lang.RuntimeException

abstract class BaseReader {

    val content = HashMap<Int, ISaveable>()
    val missingReferences = HashMap<Int, ArrayList<Pair<Any, String>>>()

    fun getNewClassInstance(clazz: String): ISaveable {
        return when(clazz){
            "SMap" -> StringMap()
            "Transform" -> Transform(null)
            "Text" -> Text("", null)
            "Circle" -> Circle(null)
            "Polygon" -> Polygon(null)
            "Image" -> Image(File(""), null)
            "Video" -> Video(File(""), null)
            "AnimatedProperty<float>" -> AnimatedProperty.float()
            "AnimatedProperty<pos>" -> AnimatedProperty.pos()
            "AnimatedProperty<scale>" -> AnimatedProperty.scale()
            "AnimatedProperty<rotYXZ>" -> AnimatedProperty.rotYXZ()
            "AnimatedProperty<skew2D>" -> AnimatedProperty.skew()
            "AnimatedProperty<color>" -> AnimatedProperty.color()
            "AnimatedProperty<quaternion>" -> AnimatedProperty.quat()
            "Keyframe" -> Keyframe<Any>(0f, 0f)
            "MaskLayer" -> MaskLayer(null)
            "Camera" -> Camera(null)
            else -> {
                ISaveable.objectTypeRegistry[clazz]?.invoke() ?: throw RuntimeException("Unknown class $clazz")
            }
        }
    }

    fun register(value: ISaveable, ptr: Int){
        if(ptr != 0){
            content[ptr] = value
            missingReferences[ptr]?.forEach { (obj, name) ->
                when(obj){
                    is ISaveable -> {
                        obj.readObject(name, value)
                    }
                    is MissingListElement -> {
                        obj.target[obj.targetIndex] = value
                    }
                    else -> throw RuntimeException("Unknown missing reference type")
                }
            }
        } else println("Got object with uuid 0: $value, it will be ignored")
    }

    fun addMissingReference(owner: Any, name: String, childPtr: Int){
        val list = missingReferences[childPtr]
        val entry = owner to name
        if(list != null){
            list += entry
        } else {
            missingReferences[childPtr] = arrayListOf(entry)
        }
    }

    fun assert(b: Boolean, msg: String){
        if(!b) throw RuntimeException(msg)
    }

    fun assertChar(isValue: Char, shallValue: Char){
        if(isValue != shallValue) throw RuntimeException("Expected $shallValue but got $isValue")
    }

    fun error(msg: String): Nothing = throw RuntimeException(msg)


}