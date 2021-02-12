package me.anno.objects.lists

import me.anno.objects.Transform
import me.anno.objects.particles.Particle

abstract class ElementGenerator: Transform() {
    abstract fun generateEntry(index: Int): Transform?
    open fun generateParticle(transform: Transform, index: Int, birthTime: Double, lifeTime: Double): Particle {
        return Particle(transform, birthTime, lifeTime, 1f)
    }
}