package me.anno.objects.meshes.svg

import me.anno.config.DefaultConfig
import me.anno.gpu.GFX.toRadians
import me.anno.gpu.buffer.Attribute
import me.anno.gpu.buffer.StaticFloatBuffer
import me.anno.io.xml.XMLElement
import me.anno.utils.OS
import me.anno.utils.clamp
import me.anno.utils.length
import org.apache.logging.log4j.LogManager
import org.joml.Vector2d
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import java.lang.Exception
import java.lang.RuntimeException
import javax.imageio.ImageIO
import kotlin.math.*

// todo animated svg
// todo transforms
// todo gradients
// todo don't use depth, use booleans on triangles to remove flickering

class SVGMesh {

    // read svg
    // creates mesh with colors

    val stepsPerDegree = DefaultConfig["format.svg.stepsPerDegree", 0.1f]

    var z = 0.0
    val deltaZ = 0.001

    fun parse(svg: XMLElement){
        parseChildren(svg.children, null)
        val viewBox = (svg["viewBox"] ?: "0 0 100 100").split(' ').map { it.toDouble() }
        createMesh(viewBox[0], viewBox[1], viewBox[2], viewBox[3])
    }

    fun parseChildren(children: List<Any>, parentGroup: XMLElement?){
        children.forEach {
            (it as? XMLElement)?.apply {
                convertStyle(this)
                parentGroup?.properties?.forEach { key, value ->
                    // todo apply transforms differently
                    if(key !in this.properties){
                        this[key] = value
                    }
                }
                val style = SVGStyle(this)
                when(type.toLowerCase()){
                    "circle" -> {
                        if(style.isFill) addCircle(this, style, true)
                        if(style.isStroke) addCircle(this, style, false)
                    }
                    "rect" -> {
                        if(style.isFill) addRectangle(this, style, true)
                        if(style.isStroke) addRectangle(this, style, false)
                    }
                    "ellipse" -> {
                        if(style.isFill) addEllipse(this, style, true)
                        if(style.isStroke) addEllipse(this, style, false)
                    }
                    "line" -> {
                        if(style.isFill) addLine(this, style, true)
                    }
                    "polyline" -> {
                        if(style.isFill) addPolyline(this, style, true)
                        if(style.isStroke) addPolyline(this, style, false)
                    }
                    "polygon" -> {
                        if(style.isFill) addPolygon(this, style, true)
                        if(style.isStroke) addPolygon(this, style, false)
                    }
                    "path" -> {
                        if(style.isFill) addPath(this, style, true)
                        if(style.isStroke) addPath(this, style, false)
                    }
                    "g" -> {
                        parseChildren(this.children, this)
                    }
                    "switch", "foreignobject", "i:pgfref", "i:pgf" -> {
                        parseChildren(this.children, parentGroup)
                    }
                    else -> throw RuntimeException("Unknown svg element $type")
                }
            }
        }
    }

    var buffer: StaticFloatBuffer? = null

    fun debugMesh(x: Double, y: Double, w: Double, h: Double){
        val x0 = x+w/2
        val y0 = y+h/2
        val debugImageSize = 1000
        val scale = debugImageSize/h
        val img = BufferedImage(debugImageSize, debugImageSize, 1)
        val gfx = img.graphics as Graphics2D
        fun ix(v: Vector2d) = debugImageSize/2 + ((v.x-x0)*scale).roundToInt()
        fun iy(v: Vector2d) = debugImageSize/2 + ((v.y-y0)*scale).roundToInt()
        curves.forEach {
            val color = it.color or 0x333333
            val triangles = it.triangles
            gfx.color = Color(color, false)
            for(i in triangles.indices step 3){
                val a = triangles[i]
                val b = triangles[i+1]
                val c = triangles[i+2]
                gfx.drawLine(ix(a), iy(a), ix(b), iy(b))
                gfx.drawLine(ix(b), iy(b), ix(c), iy(c))
                gfx.drawLine(ix(c), iy(c), ix(a), iy(a))
            }
        }
        ImageIO.write(img, "png", File(OS.desktop, "svg/tiger.png"))
    }

    fun createMesh(x0: Double, y0: Double, w: Double, h: Double){
        val scale = 1f/h
        val totalPointCount = curves.sumBy { it.triangles.size }
        val totalDoubleCount = totalPointCount * 7 // xyz, rgba
        if(totalPointCount > 0){
            val buffer = StaticFloatBuffer(listOf(
                Attribute("attr0",3), Attribute("attr1", 4)
            ), totalDoubleCount)
            this.buffer = buffer
            curves.forEach {
                val color = it.color
                val r = color.shr(16).and(255)/255f
                val g = color.shr(8).and(255)/255f
                val b = color.and(255)/255f
                val a = color.shr(24).and(255)/255f
                val depth = it.depth.toFloat()
                it.triangles.forEach { v ->
                    buffer.put(((v.x-x0)*scale).toFloat(), ((v.y-y0)*scale).toFloat(), depth)
                    //buffer.put(Math.random().toDouble(), Math.random().toDouble(), Math.random().toDouble(), Math.random().toDouble())
                    // LOGGER.info(Vector3d((v.x-x0)*scale, (v.y-y0)*scale, it.depth).print())
                    buffer.put(r, g, b, a)
                }
            }
        }
        // LOGGER.info("created buffer $x $y $scale of curves with size $totalPointCount")
    }

    fun convertStyle(xml: XMLElement){
        val style = xml["style"] ?: return
        val properties = style.split(';')
        properties.forEach {
            val index = it.indexOf(':')
            if(index in 1 until it.lastIndex){
                val name = it.substring(0, index).trim()
                val value = it.substring(index+1).trim()
                xml[name] = value
            }
        }
    }

    val currentCurve = ArrayList<Vector2d>(128)
    val curves = ArrayList<SVGCurve>()

    var x = 0.0
    var y = 0.0

    var reflectedX = 0.0
    var reflectedY = 0.0

    lateinit var currentStyle: SVGStyle
    var currentFill = false

    fun init(style: SVGStyle, fill: Boolean){
        end(false)
        currentStyle = style
        currentFill = fill
        // each new element is relative to its parent
        x = 0.0
        y = 0.0
    }

    fun endElement(){
        end(false)
        z += deltaZ
    }

    fun addLine(xml: XMLElement, style: SVGStyle, fill: Boolean){
        init(style, fill)
        moveTo(xml["x1"]!!.toDouble(), xml["y1"]!!.toDouble())
        lineTo(xml["x2"]!!.toDouble(), xml["y2"]!!.toDouble())
        endElement()
    }

    fun addPath(xml: XMLElement, style: SVGStyle, fill: Boolean){
        init(style, fill)
        val data = xml["d"] ?: return
        var i = 0
        fun read(): Double {
            var j = i
            spaces@while(true){
                when(data[j]){
                    ' ', '\t', '\r', '\n', ',' -> j++
                    else -> break@spaces
                }
            }
            i = j
            when(data[j]){
                '+', '-' -> j++
            }
            when(data[j]){
                '.' -> {
                    // LOGGER.info("starts with .")
                    j++
                    int@while(true){
                        when(data.getOrNull(j)){
                            in '0' .. '9' -> j++
                            else -> break@int
                        }
                    }
                }
                else -> {
                    int@while(true){
                        when(data.getOrNull(j)){
                            in '0' .. '9' -> j++
                            else -> break@int
                        }
                    }
                    if(data.getOrNull(j) == '.'){
                        j++
                        int@while(true){
                            when(data.getOrNull(j)){
                                in '0' .. '9' -> j++
                                else -> break@int
                            }
                        }
                    }
                }
            }

            when(data.getOrNull(j)){
                'e', 'E' -> {
                    j++
                    when(data.getOrNull(j)){
                        '+', '-' -> j++
                    }
                    int@while(true){
                        when(data.getOrNull(j)){
                            in '0' .. '9' -> j++
                            else -> break@int
                        }
                    }
                }
            }
            // LOGGER.info("'${data.substring(i, j)}' + ${data.substring(j, j+10)}")
            val value = data.substring(i, j).toDouble()
            i = j
            return value
        }

        var lastAction = ' '
        fun parseAction(symbol: Char): Boolean {
            try {
                when(symbol){
                    ' ', '\t', '\r', '\n' -> return false
                    'M' -> moveTo(read(), read())
                    'm' -> moveTo(x + read(), y + read())
                    'L' -> lineTo(read(), read())
                    'l' -> lineTo(x + read(), y + read())
                    'H' -> lineTo(read(), y)
                    'h' -> lineTo(x + read(), y)
                    'V' -> lineTo(x, read())
                    'v' -> lineTo(x, y + read())
                    'C' -> cubicTo(read(), read(), read(), read(), read(), read())
                    'c' -> cubicTo(x + read(), y + read(), x + read(), y + read(), x + read(), y + read())
                    'S' -> cubicTo(reflectedX, reflectedY, read(), read(), read(), read())
                    's' -> cubicTo(reflectedX, reflectedY, x + read(), y + read(), x + read(), y + read())
                    'Q' -> quadraticTo(read(), read(), read(), read())
                    'q' -> quadraticTo(x + read(), y + read(), x + read(), y + read())
                    'T' -> quadraticTo(reflectedX, reflectedY, read(), read())
                    't' -> quadraticTo(reflectedX, reflectedY, x + read(), y + read())
                    'A' -> arcTo(read(), read(), read(), read(), read(), read(), read())
                    'a' -> arcTo(read(), read(), read(), read(), read(), x + read(), y + read())
                    'Z', 'z' -> close()
                    else -> {
                        i--
                        parseAction(lastAction)
                        return false
                    }
                }
            } catch (e: Exception){
                LOGGER.info(data)
                throw e
            }
            return true
        }

        while(i < data.length){
            when(val symbol = data[i++]){
                ' ', '\t', '\r', '\n' -> {}
                else -> {
                    if(parseAction(symbol)){
                        lastAction = symbol
                    }
                }
            }
        }
        endElement()
    }

    fun arcTo(rx: Double, ry: Double, xAxisRotation: Double,
              largeArcFlag: Double, sweepFlag: Double,
              x2: Double, y2: Double){
        // LOGGER.info("$rx $ry $xAxisRotation $largeArcFlag $sweepFlag $x2 $y2")
        arcTo(rx, ry, xAxisRotation,
            largeArcFlag.toInt() != 0,
            sweepFlag.toInt() != 0,
            x2, y2)
    }

    // http://xahlee.info/REC-SVG11-20110816/implnote.html#ArcImplementationNotes
    fun arcTo(rx: Double, ry: Double, xAxisRotation: Double,
              largeArcFlag: Boolean, sweepFlag: Boolean,
              x2: Double, y2: Double){

        if(rx == 0.0 && ry == 0.0) return lineTo(x2, y2)

        if(rx < 0f || ry < 0f) return arcTo(abs(rx), abs(ry), xAxisRotation, largeArcFlag, sweepFlag, x2, y2)

        val x1 = this.x
        val y1 = this.y

        val angle = toRadians(xAxisRotation)
        val cos = cos(angle)
        val sin = sin(angle)

        val idxh = (x1-x2)/2
        val idyh = (y1-y2)/2

        val x12 = cos * idxh + sin * idyh
        val y12 =-sin * idxh + cos * idyh

        val scaleCorrection = length(x12/rx, y12/ry)
        if(scaleCorrection > 1f){
            return arcTo(rx*scaleCorrection, ry*scaleCorrection, xAxisRotation, largeArcFlag, sweepFlag, x2, y2)
        }

        val sign = if(largeArcFlag != sweepFlag) 1f else -1f
        val tx = rx*rx*y12*y12
        val ty = ry*ry*x12*x12
        val c2Length = sign * sqrt((rx*rx*ry*ry - (tx + ty))/(tx + ty))
        val cx2 = c2Length * rx*y12/ry
        val cy2 = c2Length * -ry*x12/rx

        val avgX = (x1+x2)/2
        val avgY = (y1+y2)/2
        val cx = cos * cx2 - sin * cy2 + avgX
        val cy = sin * cx2 + cos * cy2 + avgY

        val qx = (x12-cx2)/rx
        val qy = (y12-cy2)/ry

        val twoPi = (2*PI).toDouble()

        val theta0 = angle(1.0, 0.0, qx, qy)
        var deltaTheta = angle(qx, qy, -(x12+cx2)/rx, -(y12+cy2)/ry)// % twoPi

        if(sweepFlag){
            if(deltaTheta <= 0f) deltaTheta += twoPi
        } else {
            if(deltaTheta >= 0f) deltaTheta -= twoPi
        }

        val angleDegrees = deltaTheta * 180 / PI

        val steps = max(3, (abs(angleDegrees) * stepsPerDegree).roundToInt())
        for(i in 1 until steps){
            val theta = theta0 + deltaTheta*i/steps
            val localX = rx * cos(theta)
            val localY = ry * sin(theta)
            val rotX = cos * localX - sin * localY
            val rotY = sin * localX + cos * localY
            lineTo(cx + rotX, cy + rotY)
        }

        lineTo(x2, y2)

    }

    fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
        val sign = if(ux*vy - uy*vx > 0f) 1f else -1f
        val dotTerm = (ux*vx+uy*vy) / sqrt((ux*ux+uy*uy) * (vx*vx+vy*vy))
        return sign * acos(clamp(dotTerm, -1.0, 1.0))
    }

    fun addPolyline(xml: XMLElement, style: SVGStyle, fill: Boolean){
        init(style, fill)
        val data = xml["points"]!!

        var i = 0
        fun read(): Double {
            var j = i
            spaces@while(true){
                when(data[j]){
                    ' ', '\t', '\r', '\n', ',' -> j++
                    else -> break@spaces
                }
            }
            i = j
            when(data[j]){
                '+', '-' -> j++
            }
            when(data[j]){
                '.' -> {
                    // LOGGER.info("starts with .")
                    j++
                    int@while(true){
                        when(data.getOrNull(j)){
                            in '0' .. '9' -> j++
                            else -> break@int
                        }
                    }
                }
                else -> {
                    int@while(true){
                        when(data.getOrNull(j)){
                            in '0' .. '9' -> j++
                            else -> break@int
                        }
                    }
                    if(data.getOrNull(j) == '.'){
                        j++
                        int@while(true){
                            when(data.getOrNull(j)){
                                in '0' .. '9' -> j++
                                else -> break@int
                            }
                        }
                    }
                }
            }

            when(data.getOrNull(j)){
                'e', 'E' -> {
                    j++
                    when(data.getOrNull(j)){
                        '+', '-' -> j++
                    }
                    int@while(true){
                        when(data.getOrNull(j)){
                            in '0' .. '9' -> j++
                            else -> break@int
                        }
                    }
                }
            }
            // LOGGER.info("'${data.substring(i, j)}' + ${data.substring(j, j+10)}")
            val value = data.substring(i, j).toDouble()
            i = j
            return value
        }

        var isFirst = false
        while(i < data.length){
            when(data[i++]){
                ' ', '\t', '\r', '\n' -> {}
                else -> {
                    i--
                    val x = read()
                    val y = read()
                    if(isFirst){
                        moveTo(x,y)
                        isFirst = false
                    } else {
                        lineTo(x,y)
                    }
                }
            }
        }

        endElement()
    }

    fun addEllipse(xml: XMLElement, style: SVGStyle, fill: Boolean){
        init(style, fill)
        addSimpleEllipse(xml["cx"]!!.toDouble(), xml["cy"]!!.toDouble(), xml["rx"]!!.toDouble(), xml["ry"]!!.toDouble())
        endElement()
    }

    fun addSimpleEllipse(cx: Double, cy: Double, rx: Double, ry: Double){
        val steps = max(7, (360 * stepsPerDegree).roundToInt())
        moveTo(cx + rx, cy)
        for(i in 1 until steps){
            val f = (PI*2*i/steps).toDouble()
            val s = sin(f)
            val c = cos(f)
            lineTo(cx + c * rx, cy + s * ry)
        }
        close()
    }

    fun addRectangle(xml: XMLElement, style: SVGStyle, fill: Boolean){
        init(style, fill)
        val rx = max(xml["rx"]?.toDoubleOrNull() ?: 0.0, 0.0)
        val ry = max(xml["ry"]?.toDoubleOrNull() ?: 0.0, 0.0)
        val x = xml["x"]!!.toDouble()
        val y = xml["y"]!!.toDouble()
        val w = xml["width"]!!.toDouble()
        val h = xml["height"]!!.toDouble()

        if(rx > 0f || ry > 0f){

            moveTo(x+rx, y)
            lineTo(x+w-rx, y)
            // todo curve down
            moveTo(x, y+ry)
            lineTo(x+w, y+h-ry)
            // todo curve
            moveTo(x+w-rx, y+h)
            lineTo(x+rx, y+h)
            // todo curve
            // todo curve once more somewhere...
            close()

        } else {
            moveTo(x, y)
            lineTo(x+w, y)
            lineTo(x+w, y+h)
            lineTo(x, y+h)
            close()
        }

        endElement()
    }

    fun addCircle(xml: XMLElement, style: SVGStyle, fill: Boolean){
        init(style, fill)
        val r = xml["r"]!!.toDouble()
        val cx = xml["cx"]!!.toDouble()
        val cy = xml["cy"]!!.toDouble()
        addSimpleEllipse(cx, cy, r, r)
        endElement()
    }

    fun addPolygon(xml: XMLElement, style: SVGStyle, fill: Boolean){
        init(style, fill)
        endElement()
    }

    fun angleDegrees(dx1: Double, dy1: Double, dx2: Double, dy2: Double): Double {
        val div = (dx1*dx1+dy1*dy1) * (dx2*dx2+dy2*dy2)
        if(div == 0.0) return 57.29577951308232
        return 57.29577951308232 * (acos(clamp((dx1*dx2 + dy1*dy2) / sqrt(div), -1.0, 1.0)))
    }

    fun steps(dx1: Double, dy1: Double, dx2: Double, dy2: Double) =
        max((angleDegrees(dx1, dy1, dx2, dy2) * stepsPerDegree).roundToInt(), 2)

    fun cubicTo(x1: Double, y1: Double, x2: Double, y2: Double, x: Double, y: Double){

        val steps = steps(x1 - this.x, y1 - this.y, x - x2, y - y2)
        for(i in 1 until steps){
            val f = i * 1f / steps
            val g = 1f-f
            val a = g*g*g
            val b = 3*g*g*f
            val c = 3*g*f*f
            val d = f*f*f
            currentCurve += Vector2d(
                this.x * a + x1 * b + x2 * c + x * d,
                this.y * a + y1 * b + y2 * c + y * d
            )
        }

        reflectedX = 2 * x - x2
        reflectedY = 2 * y - y2

        lineTo(x, y)

    }

    fun quadraticTo(x1: Double, y1: Double, x: Double, y: Double){

        val steps = steps(x1 - this.x, y1 - this.y, x - x1, y - y1)
        for(i in 1 until steps){
            val f = i * 1f / steps
            val g = 1f-f
            val a = g*g
            val b = 2*g*f
            val c = f*f
            currentCurve += Vector2d(
                this.x * a + x1 * b + x * c,
                this.y * a + y1 * b + y * c
            )
        }

        reflectedX = 2 * x - x1
        reflectedY = 2 * y - y1

        lineTo(x, y)

    }

    fun lineTo(x: Double, y: Double){

        currentCurve += Vector2d(x, y)

        this.x = x
        this.y = y

    }

    fun moveTo(x: Double, y: Double){

        end(false)

        currentCurve += Vector2d(x, y)

        this.x = x
        this.y = y

    }

    fun end(closed: Boolean){

        if(currentCurve.isNotEmpty()){
            curves += SVGCurve(ArrayList(currentCurve), closed, z,
                if(currentFill) currentStyle.fill!! else currentStyle.stroke!!,
                if(currentFill) 0.0 else currentStyle.strokeWidth)
            currentCurve.clear()
        }

    }

    fun close() = end(true)

    companion object {
        private val LOGGER = LogManager.getLogger(SVGMesh::class)
    }

}