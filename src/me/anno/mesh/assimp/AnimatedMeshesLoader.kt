package me.anno.mesh.assimp

import me.anno.ecs.Entity
import me.anno.io.files.FileReference
import me.anno.io.files.FileReference.Companion.getReference
import me.anno.mesh.assimp.AnimGameItem.Companion.maxBones
import me.anno.mesh.assimp.AnimatedMeshesLoader2.boneTransform2
import me.anno.mesh.assimp.AnimatedMeshesLoader2.getDuration
import me.anno.mesh.assimp.AssimpTree.convert
import me.anno.utils.OS
import org.apache.logging.log4j.LogManager
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3d
import org.lwjgl.assimp.*
import org.lwjgl.assimp.Assimp.aiImportFile
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.min

object AnimatedMeshesLoader : StaticMeshesLoader() {

    // todo also load morph targets

    private val LOGGER = LogManager.getLogger(StaticMeshesLoader::class)

    override fun load(resourcePath: String, texturesDir: String?, flags: Int): AnimGameItem {
        val aiScene: AIScene = aiImportFile(resourcePath, flags) ?: throw Exception("Error loading model")
        val metadata = aiScene.mMetaData()
        if (metadata != null) {
            // UnitScaleFactor
            val keys = metadata.mKeys()
            val values = metadata.mValues()
            for (i in 0 until metadata.mNumProperties()) {
                val key = keys[i].dataString()
                val valueRaw = values[i]
                val valueType = valueRaw.mType()
                val value = when (valueType) {
                    0 -> valueRaw.mData(1)[0] // bool
                    1 -> valueRaw.mData(4).int // int
                    2 -> valueRaw.mData(8).long // long, unsigned
                    3 -> valueRaw.mData(4).float // float
                    4 -> {// string
                        val buff = valueRaw.mData(256)
                        val length = buff.int
                        buff.limit(buff.position() + length)
                        "$length: '${StandardCharsets.UTF_8.decode(buff)}'"
                    }
                    5 -> {
                        // aivector3d
                        // todo doubles or floats?
                        val buffer = valueRaw.mData(12 * 8).asDoubleBuffer()
                        Vector3d(buffer[0], buffer[1], buffer[2])
                    }
                    else -> continue
                }
                LOGGER.info("Metadata $key: $valueType, $value")
            }
        }
        val materials = loadMaterials(aiScene, texturesDir)
        val boneList = ArrayList<Bone>()
        val boneMap = HashMap<String, Bone>()
        val meshes = loadMeshes(aiScene, materials, boneList, boneMap)
        val animations = loadAnimations(aiScene, boneList, boneMap)
        val nameFile = File(resourcePath)
        var name = nameFile.name
        if (name == "scene.gltf") name = nameFile.parentFile.name
        getReference(OS.desktop, "$name-${resourcePath.hashCode()}.txt")
            .writeText(
                "" +
                        "${boneList.map { "${it.name}: ${it.offsetMatrix}" }}\n" +
                        "$meshes"
            )
        // LOGGER.info("Found ${meshes.size} meshes and ${animations.size} animations on ${boneList.size} bones, in $resourcePath")
        // println(animations)
        return AnimGameItem(meshes, animations)
    }

    private fun loadMeshes(
        aiScene: AIScene,
        materials: Array<Material>,
        boneList: ArrayList<Bone>,
        boneMap: HashMap<String, Bone>
    ): Entity {

        val numMeshes = aiScene.mNumMeshes()
        val meshes = if (numMeshes > 0) {
            val aiMeshes = aiScene.mMeshes()!!
            Array(numMeshes) {
                val aiMesh = AIMesh.create(aiMeshes[it])
                processMesh(aiMesh, materials, boneList, boneMap)
            }
        } else emptyArray()
        return buildScene(aiScene, meshes)

    }

    private fun loadAnimations(
        aiScene: AIScene,
        boneList: List<Bone>,
        boneMap: HashMap<String, Bone>
    ): Map<String, Animation> {
        val root = aiScene.mRootNode()!!
        // val rootNode = buildNodesTree(root, null)
        // Unit-Matrix -> walking dae dude is correct
        // todo the static model of the engine is incorrect as well... we are missing something...
        val globalInverseTransformation = convert(root.mTransformation()).invert()
        /*return processAnimations(
            aiScene, boneList, rootNode,
            globalInverseTransformation
        )*/
        return processAnimations2(
            aiScene, boneList, boneMap, root,
            globalInverseTransformation
        )
    }

    private fun processAnimations2(
        aiScene: AIScene, boneList: List<Bone>, boneMap: HashMap<String, Bone>,
        rootNode: AINode, globalInverseTransformation: Matrix4f
    ): Map<String, Animation> {
        // Process all animations
        val numAnimations = aiScene.mNumAnimations()
        val animations = HashMap<String, Animation>(numAnimations)
        val aiAnimations = aiScene.mAnimations()
        LOGGER.info("Loading animations: $numAnimations")
        for (i in 0 until numAnimations) {
            val aiAnimation = AIAnimation.create(aiAnimations!![i])
            val animNodeCache = createAnimationCache(aiAnimation)
            val maxFrames = calcAnimationMaxFrames(aiAnimation)
            val interpolation = if (maxFrames == 1) 1 else max(1, 30 / maxFrames)
            val maxFramesV2 = maxFrames * interpolation
            val duration0 = getDuration(animNodeCache)
            val timeScale = duration0 / (maxFramesV2 - 1.0)
            val frames = Array(maxFramesV2) { frameIndex ->
                val animatedFrame = AnimatedFrame()
                boneTransform2(
                    aiScene, rootNode, frameIndex * timeScale,
                    animatedFrame.matrices, globalInverseTransformation, boneList, boneMap, animNodeCache
                )
                animatedFrame
            }
            var tps = aiAnimation.mTicksPerSecond()
            if (tps < 1e-16) tps = 1000.0
            val duration = aiAnimation.mDuration() / tps
            val animation = Animation(aiAnimation.mName().dataString(), frames, duration)
            animations[animation.name] = animation
        }
        return animations
    }

    private fun processAnimations(
        aiScene: AIScene, boneList: List<Bone>,
        rootNode: Node, globalInverseTransformation: Matrix4f
    ): Map<String, Animation> {
        // Process all animations
        val numAnimations = aiScene.mNumAnimations()
        val animations = HashMap<String, Animation>(numAnimations)
        val aiAnimations = aiScene.mAnimations()
        LOGGER.info("Loading animations: $numAnimations")
        for (i in 0 until numAnimations) {
            val aiAnimation = AIAnimation.create(aiAnimations!![i])
            val animNodeCache = createAnimationCache(aiAnimation)
            val maxFrames = calcAnimationMaxFrames(aiAnimation)
            val frames = Array(maxFrames) { frameIndex ->
                val animatedFrame = AnimatedFrame()
                buildFrameMatrices(
                    aiAnimation, animNodeCache, boneList, animatedFrame, frameIndex, rootNode,
                    rootNode.transformation, globalInverseTransformation
                )
                animatedFrame
            }
            var tps = aiAnimation.mTicksPerSecond()
            if (tps < 1e-16) tps = 1000.0
            val duration = aiAnimation.mDuration() / tps
            val animation = Animation(aiAnimation.mName().dataString(), frames, duration)
            animations[animation.name] = animation
        }
        return animations
    }

    fun buildFrameMatrices(
        aiAnimation: AIAnimation,
        animNodeCache: Map<String, AINodeAnim>,
        bones: List<Bone>,
        animatedFrame: AnimatedFrame,
        frame: Int,
        node: Node,
        parentTransformation: Matrix4f,
        globalInverseTransform: Matrix4f
    ) {
        val nodeName = node.name
        val aiNodeAnim = animNodeCache[nodeName]
        var nodeTransform = node.transformation
        if (aiNodeAnim != null) {
            nodeTransform = buildNodeTransformationMatrix(aiNodeAnim, frame)
        }
        val nodeGlobalTransform = Matrix4f(parentTransformation).mul(nodeTransform)
        // todo there probably shouldn't be multiple bones... or can they?...
        // todo if not, we can use a hashmap, which is n times as fast
        // todo -> there is, because there is multiple meshes, one for each material...
        val affectedBones = bones.filter { it.name == nodeName }
        // if(affectedBones.size > 1) println("filtering nodes for name $nodeName, found ${affectedBones.size} bones, bones: ${bones.map { it.name }}")
        for (bone in affectedBones) {
            val boneTransform = Matrix4f(globalInverseTransform)
                .mul(nodeGlobalTransform)
                .mul(bone.offsetMatrix)
            animatedFrame.setMatrix(bone.id, boneTransform)
        }
        for (childNode in node.children) {
            buildFrameMatrices(
                aiAnimation, animNodeCache, bones, animatedFrame, frame, childNode, nodeGlobalTransform,
                globalInverseTransform
            )
        }
    }

    fun buildNodeTransformationMatrix(aiNodeAnim: AINodeAnim, frame: Int): Matrix4f {
        val positionKeys = aiNodeAnim.mPositionKeys()
        val scalingKeys = aiNodeAnim.mScalingKeys()
        val rotationKeys = aiNodeAnim.mRotationKeys()
        var aiVecKey: AIVectorKey
        var vec: AIVector3D
        val nodeTransform = Matrix4f()
        val numPositions = aiNodeAnim.mNumPositionKeys()
        if (numPositions > 0) {
            aiVecKey = positionKeys!![min(numPositions - 1, frame)]
            vec = aiVecKey.mValue()
            nodeTransform.translate(vec.x(), vec.y(), vec.z())
        }
        val numRotations = aiNodeAnim.mNumRotationKeys()
        if (numRotations > 0) {
            val quatKey = rotationKeys!![min(numRotations - 1, frame)]
            val aiQuat = quatKey.mValue()
            val quat = Quaternionf(aiQuat.x(), aiQuat.y(), aiQuat.z(), aiQuat.w())
            nodeTransform.rotate(quat)
        }
        val numScalingKeys = aiNodeAnim.mNumScalingKeys()
        if (numScalingKeys > 0) {
            aiVecKey = scalingKeys!![min(numScalingKeys - 1, frame)]
            vec = aiVecKey.mValue()
            nodeTransform.scale(vec.x(), vec.y(), vec.z())
        }
        return nodeTransform
    }

    fun createAnimationCache(aiAnimation: AIAnimation): HashMap<String, AINodeAnim> {
        val numAnimNodes = aiAnimation.mNumChannels()
        val aiChannels = aiAnimation.mChannels()
        val map = HashMap<String, AINodeAnim>(numAnimNodes)
        for (i in 0 until numAnimNodes) {
            val aiNodeAnim = AINodeAnim.create(aiChannels!![i])
            val name = aiNodeAnim.mNodeName().dataString()
            map[name] = aiNodeAnim
        }
        return map
    }

    fun calcAnimationMaxFrames(aiAnimation: AIAnimation): Int {
        var maxFrames = 0
        val numNodeAnimations = aiAnimation.mNumChannels()
        val aiChannels = aiAnimation.mChannels()
        for (i in 0 until numNodeAnimations) {
            val aiNodeAnim = AINodeAnim.create(aiChannels!![i])
            val numFrames = max(
                max(aiNodeAnim.mNumPositionKeys(), aiNodeAnim.mNumScalingKeys()),
                aiNodeAnim.mNumRotationKeys()
            )
            maxFrames = max(maxFrames, numFrames)
        }
        return maxFrames
    }

    fun processBones(
        aiMesh: AIMesh,
        boneList: ArrayList<Bone>,
        boneMap: HashMap<String, Bone>,
        boneIds: IntArray, weights: FloatArray
    ) {

        val weightSet: MutableMap<Int, MutableList<VertexWeight>> = HashMap()
        val numBones = aiMesh.mNumBones()

        if (numBones > 0) {

            val aiBones = aiMesh.mBones()!!
            boneList.ensureCapacity(boneList.size + numBones)

            for (i in 0 until numBones) {

                val aiBone = AIBone.create(aiBones[i])
                val boneName = aiBone.mName().dataString()
                val boneTransform = convert(aiBone.mOffsetMatrix())

                var bone = boneMap[boneName]
                if (bone == null) {
                    bone = Bone(boneList.size, boneName, boneTransform)
                    boneList.add(bone)
                    boneMap[boneName] = bone
                }

                val numWeights = aiBone.mNumWeights()
                val aiWeights = aiBone.mWeights()
                for (j in 0 until numWeights) {
                    val aiWeight = aiWeights[j]
                    val vw = VertexWeight(
                        bone.id, aiWeight.mVertexId(),
                        aiWeight.mWeight()
                    )
                    var vertexWeightList = weightSet[vw.vertexId]
                    if (vertexWeightList == null) {
                        vertexWeightList = ArrayList(4)
                        weightSet[vw.vertexId] = vertexWeightList
                    }
                    vertexWeightList.add(vw)
                }

            }
        }

        val numVertices = aiMesh.mNumVertices()
        val maxBoneId = maxBones - 1
        for (i in 0 until numVertices) {
            val vertexWeightList = weightSet[i]
            if (vertexWeightList != null) {
                vertexWeightList.sortByDescending { it.weight }
                val size = vertexWeightList.size
                val i4 = i * 4
                weights[i4] = 1f
                for (j in 0 until size) {
                    val vw = vertexWeightList[j]
                    weights[i4 + j] = vw.weight
                    boneIds[i4 + j] = min(vw.boneId, maxBoneId)
                }
            } else {
                val i4 = i * 4
                weights[i4] = 1f
            }
        }

    }

    fun processMesh(
        aiMesh: AIMesh,
        materials: Array<Material>,
        boneList: ArrayList<Bone>,
        boneMap: HashMap<String, Bone>
    ): AssimpMesh {


        val vertexCount = aiMesh.mNumVertices()
        val vertices = FloatArray(vertexCount * 3)
        val uvs = FloatArray(vertexCount * 2)
        val normals = FloatArray(vertexCount * 3)
        val boneIds = IntArray(vertexCount * 4)
        val weights = FloatArray(vertexCount * 4)
        val colors = FloatArray(vertexCount * 4)

        // todo directly use an array
        //  - we can force triangles, and know the count that way, or quickly walk through it...
        val indices = ArrayList<Int>()

        processVertices(aiMesh, vertices)
        processNormals(aiMesh, normals)
        processUVs(aiMesh, uvs)
        processIndices(aiMesh, indices)
        processVertexColors(aiMesh, colors)
        processBones(aiMesh, boneList, boneMap, boneIds, weights)

        val mesh = AssimpMesh(
            vertices, uvs,
            normals, colors,
            indices.toIntArray(),
            boneIds, weights
        )
        // todo calculate the transform and set it
        // todo use it for correct rendering
        // mesh.transform.set(convert())

        val materialIdx = aiMesh.mMaterialIndex()
        if (materialIdx in materials.indices) {
            mesh.material = materials[materialIdx]
        }

        return mesh

    }


}