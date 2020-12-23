package me.anno.objects.particles.forces.impl

import me.anno.objects.particles.Particle
import me.anno.objects.particles.ParticleState
import me.anno.objects.particles.forces.ForceField
import me.anno.utils.Vectors.times
import org.joml.Vector3f

class VelocityFrictionForce: ForceField("Velocity Friction", "The faster a particle is, the more friction it will experience") {

    override fun getForce(state: ParticleState, time: Double, particles: List<Particle>): Vector3f {
        return (state.dPosition * strength[time]).mul(-1f)
    }

    override fun getClassName() = "VelocityFrictionForce"

}