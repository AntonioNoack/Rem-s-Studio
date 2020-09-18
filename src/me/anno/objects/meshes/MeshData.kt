package me.anno.objects.meshes

import me.anno.gpu.GFX
import me.anno.gpu.GFX.isFinalRendering
import me.anno.gpu.GFX.matrixBufferFBX
import me.anno.gpu.ShaderLib.shaderFBX
import me.anno.gpu.ShaderLib.shaderObjMtl
import me.anno.gpu.TextureLib.whiteTexture
import me.anno.gpu.buffer.StaticFloatBuffer
import me.anno.gpu.texture.ClampMode
import me.anno.gpu.texture.FilteringMode
import me.anno.gpu.texture.Texture2D
import me.anno.objects.Transform.Companion.yAxis
import me.anno.objects.cache.Cache
import me.anno.objects.cache.CacheData
import me.anno.objects.meshes.fbx.model.FBXGeometry
import me.anno.objects.meshes.obj.Material
import me.anno.video.MissingFrameException
import me.karl.main.Camera
import me.karl.scene.Scene
import org.joml.Matrix4f
import org.joml.Matrix4fArrayList
import org.joml.Vector4f
import org.lwjgl.opengl.GL20
import java.io.File

class MeshData : CacheData {

    var objData: Map<Material, StaticFloatBuffer>? = null
    var fbxGeometry: FBXGeometry? = null
    var daeScene: Scene? = null

    fun getTexture(file: File?, defaultTexture: Texture2D): Texture2D {
        if (file == null) return defaultTexture
        val tex = Cache.getImage(file, 1000, true)
        if (tex == null && isFinalRendering) throw MissingFrameException(file)
        return tex ?: defaultTexture
    }

    fun drawObj(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        for ((material, buffer) in objData!!) {
            val shader = shaderObjMtl.shader
            GFX.shader3DUniforms(shader, stack, 1, 1, color, null, FilteringMode.NEAREST, null)
            getTexture(material.diffuseTexture, whiteTexture).bind(0, false, ClampMode.CLAMP)
            buffer.draw(shader)
            GFX.check()
        }
    }

    fun drawDae(stack: Matrix4fArrayList, time: Double, color: Vector4f){
        GFX.check()
        val scene = daeScene!!
        val renderer = Mesh.daeRenderer!!
        val camera = scene.camera as Camera
        camera.updateTransformMatrix(stack)
        scene.animatedModel.update(time)
        renderer.render(scene.animatedModel, scene.camera, scene.lightDirection)
        GFX.check()
    }

    // doesn't work :/
    fun drawFBX(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        for ((material, buffer) in objData!!) {

            val shader = shaderFBX.shader
            shader.use()

            // todo calculate all bone transforms, and upload them to the shader...
            val geo = fbxGeometry!!
            // todo is weight 0 automatically set, and 1-sum???
            // todo root motion might be saved in object...

            // todo reset the matrixBuffer somehow??
            matrixBufferFBX.position(0)
            geo.bones.forEach { bone ->

                // todo apply local translation and rotation...
                // global -> local -> rotated local -> global
                // stack.get(GFX.matrixBufferFBX)

                val jointMatrix = bone.transform!!
                val invJointMatrix = bone.transformLink!!

                val bp = bone.parent
                val parentMatrix = bp?.localJointMatrix
                val angle = 1f * (GFX.lastTime/3 * 1e-9f).rem(1f)

                val dx = jointMatrix[3,0] - (bp?.transform?.get(3,0) ?: 0f)
                val dy = jointMatrix[3,1] - (bp?.transform?.get(3,1) ?: 0f)
                val dz = jointMatrix[3,2] - (bp?.transform?.get(3,2) ?: 0f)

                // effectively bone-space parent-2-child-transform
                val translateMat = Matrix4f().translate(dx, dy, dz).rotate(angle, yAxis) // Vector3f(dx,dy,dz).normalize()

                var jointMat = translateMat// .mul(rotationMat)

                if(parentMatrix != null){
                    jointMat.mul(parentMatrix)
                    //jointMat = Matrix4f(parentMatrix).mul(jointMat)
                }


                bone.localJointMatrix = jointMat

                val mat = Matrix4f(jointMat)
                mat.mul(invJointMatrix) // invJointMatrix

                // (mat)

                for (i in 0 until 16) {
                    matrixBufferFBX.put(mat.get(i / 4, i and 3))
                }

            }

            matrixBufferFBX.position(0)
            GFX.check()
            GL20.glUniformMatrix4fv(shader["transforms"], false, matrixBufferFBX)
            GFX.check()

            GFX.shader3DUniforms(shader, stack, 1, 1, color, null, FilteringMode.NEAREST, null)
            getTexture(material.diffuseTexture, whiteTexture).bind(0, false, ClampMode.CLAMP)
            buffer.draw(shader)
            GFX.check()

        }
    }

    override fun destroy() {
        objData?.entries?.forEach {
            it.value.destroy()
        }
        // fbxGeometry?.destroy()
        daeScene?.animatedModel?.destroy()
    }

}