package me.anno.objects.particles.forces.impl

import me.anno.io.ISaveable
import me.anno.io.base.BaseWriter
import me.anno.objects.InspectableAnimProperty
import me.anno.objects.animation.AnimatedProperty
import me.anno.objects.particles.forces.types.RelativeForceField
import me.anno.utils.Maths.pow
import me.anno.utils.Vectors.times
import org.joml.Vector3f

class GravityField : RelativeForceField("Central Gravity", "Gravity towards a single point") {

    val exponent = AnimatedProperty.float(2f)

    override fun getForce(delta: Vector3f, time: Double): Vector3f {
        val l = delta.length()
        return delta * (-pow(l, -(exponent[time] + 1f)) + 1e-16f)
    }

    override fun listProperties(): List<InspectableAnimProperty> {
        return super.listProperties() + listOf(InspectableAnimProperty(exponent, "Exponent", "How quickly the force declines with distance"))
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(this,"exponent", exponent)
    }

    override fun readObject(name: String, value: ISaveable?) {
        when(name){
            "exponent" -> exponent.copyFrom(value)
            else -> super.readObject(name, value)
        }
    }

    override fun getClassName() = "GravityField"

}