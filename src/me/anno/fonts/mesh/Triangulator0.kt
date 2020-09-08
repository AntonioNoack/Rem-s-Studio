package me.anno.fonts.mesh

import me.anno.utils.*
import org.apache.logging.log4j.LogManager
import org.joml.Vector2d
import java.lang.RuntimeException

object Triangulator0 {

    val LOGGER = LogManager.getLogger(Triangulator::class)!!

    //fun ringToTriangles(pts: List<Vector2d>) =
    //    ringToTriangleIndices(pts).map { pts[it] }.toMutableList()

    // operator fun Vector2d.minus(s: Vector2d) = Vector2d(x-s.x, y-s.y)

    fun getGuessArea(pts: List<Vector2d>): Double {
        val n = pts.size
        var area = 0.0
        var p = n-1
        var q = 0
        while(q < n){
            val pv = pts[p]
            val qv = pts[q]
            area += pv.x * qv.y - qv.x * pv.y // cross product
            p = q++
        }
        return area * 0.5
    }

    fun ringToTriangles(input: List<Vector2d>): List<Vector2d> {

        val n = input.size
        if(n < 3) return emptyList()

        val areaForSign = getGuessArea(input)

        val pts = if(areaForSign > 0){
            input.reversed()
        } else input

        fun containsSomething(a: Vector2d, b: Vector2d, c: Vector2d): Boolean {

            for(p in pts){
                if(p === a || p === b || p === c) continue
                if(p.isInsideTriangle(a, b, c)){
                    return true
                }
            }

            return false

        }

        fun cutsSomething(a: Vector2d, b: Vector2d): Boolean {

            for((i, p1) in pts.withIndex()){
                if(p1 === a || p1 === b) continue
                val p0 = if(i == 0) pts.last() else pts[i-1]
                if(p0 === a || p0 === b) continue
                val cut = getStrictLineIntersection(a, b, p0, p1)
                if(cut != null){
                    /*println("cut of line (${a.print(input)}-${b.print(input)}):" +
                            " at ${cut.print()} with ${p0.print(input)}-${p1.print(input)}")
                    println(a.print())
                    println(b.print())
                    println(cut.print())
                    println(p0.print())
                    println(p1.print())*/
                    // throw RuntimeException()
                    return true
                }
            }

            return false

        }


        // our own,
        // always working solution...

        // we have a list of points we need to connect
        // try all combinations to create triangles :)

        // use a linked list for performance reasons?
        // -> no, the checks are in O(n), so improving the remove operation to O(1) is useless for
        // lists with many points

        val maxTriangleCount = pts.size - 2
        val triangulation = ArrayList<Vector2d>(maxTriangleCount * 3)
        val shrinkingRing = ArrayList(pts)
        search@ while(shrinkingRing.size > 3){

            var wasChanged = false

            for(ai in shrinkingRing.indices){

                val m = shrinkingRing.size
                if(ai >= m) continue@search // was changed anyways
                if(m <= 3) break@search

                val bi = (ai+1) % m
                val ci = (ai+2) % m

                val a = shrinkingRing[ai]
                val b = shrinkingRing[bi]
                val c = shrinkingRing[ci]

                // check if b can be removed
                val sideSign = b.getSideSign(a, c)
                if(sideSign < 0.0){
                    // correct order :)
                    // now check, if we don't cross other lines
                    // this is fulfilled most times, if we contain no foreign point
                    if(!containsSomething(a, b, c)){
                        if(!cutsSomething(a, c)){

                            triangulation.add(a)
                            triangulation.add(b)
                            triangulation.add(c)
                            shrinkingRing.removeAt(bi)
                            wasChanged = true

                        }
                    }
                } else if(sideSign == 0.0){
                    wasChanged = true
                    shrinkingRing.removeAt(bi)
                }

            }

            if(!wasChanged) break

        }

        if(shrinkingRing.size == 3){
            triangulation.addAll(shrinkingRing)
        } else LOGGER.warn("Polygon could not be triangulated!, ${pts.size} -> ${shrinkingRing.size},\n" +
                "    ${input.joinToString { it.print() }}\n" +
                " -> ${triangulation.joinToString {it.print()}},\n" +
                "    ${shrinkingRing.joinToString { it.print(input) }}")

        return triangulation

    }

}