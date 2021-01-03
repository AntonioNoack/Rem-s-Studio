package me.anno.objects.forces.types

import me.anno.objects.particles.Particle
import me.anno.objects.particles.ParticleState
import me.anno.objects.forces.ForceField
import me.anno.utils.Vectors.minus
import me.anno.utils.Vectors.times
import org.joml.Vector3f

abstract class RelativeForceField(displayName: String, description: String): ForceField(displayName, description) {

    abstract fun getForce(delta: Vector3f, time: Double): Vector3f

    override fun getForce(state: ParticleState, time: Double, particles: List<Particle>): Vector3f {
        val position = state.position
        val center = this.position[time]
        return getForce(position - center, time) * strength[time]
    }

}