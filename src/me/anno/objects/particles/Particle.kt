package me.anno.objects.particles

import me.anno.objects.Transform
import org.joml.Vector3f

class Particle(
    var type: Transform,
    val birthTime: Double,
    val lifeTime: Double,
    val mass: Float
) {

    val states = ArrayList<ParticleState>()

    val position = Vector3f()
    val rotation = Vector3f()
    val color = Vector3f()

    fun getPosition(index0: Int, indexF: Float): Vector3f {
        val state0 = states[index0]
        val state1 = states[index0 + 1]
        return state0.position.lerp(state1.position, indexF, position)
    }

    fun getRotation(index0: Int, indexF: Float): Vector3f {
        val state0 = states[index0]
        val state1 = states[index0 + 1]
        return state0.rotation.lerp(state1.rotation, indexF, rotation)
    }

    fun getColor(index0: Int, indexF: Float): Vector3f {
        val state0 = states[index0]
        val state1 = states[index0 + 1]
        return state0.color.lerp(state1.color, indexF, color)
    }

    fun isAlive(time: Double) = (time - birthTime) in 0.0..lifeTime

    fun getLifeOpacity(time: Double, timeStep: Double, fadingIn: Double, fadingOut: Double): Double {
        if (lifeTime < timeStep) return 0.0
        val particleTime = time - birthTime
        if (particleTime <= 0.0 || particleTime >= lifeTime) return 0.0
        val fading = fadingIn + fadingOut
        if (fading > lifeTime) {
            return getLifeOpacity(time, timeStep, lifeTime * fadingIn / fading, lifeTime * fadingOut / fading)
        }
        if (particleTime < fadingIn) return particleTime / fadingIn
        if (particleTime > lifeTime - fadingOut) return (lifeTime - particleTime) / fadingOut
        return 1.0
    }

}