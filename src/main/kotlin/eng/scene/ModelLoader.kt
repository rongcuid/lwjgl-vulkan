package eng.scene

import org.joml.Vector4f
import org.lwjgl.assimp.*
import org.lwjgl.assimp.Assimp.*
import org.lwjgl.system.MemoryStack
import org.tinylog.kotlin.Logger
import java.io.File
import java.nio.IntBuffer
import java.util.*


class ModelLoader {
    companion object {
        fun loadModel(modelId: String, modelPath: String, texturesDir: String): ModelData {
            return loadModel(
                modelId, modelPath, texturesDir,
                aiProcess_GenSmoothNormals or aiProcess_JoinIdenticalVertices
                        or aiProcess_Triangulate or aiProcess_FixInfacingNormals
                        or aiProcess_CalcTangentSpace or aiProcess_PreTransformVertices
            )
        }

        fun loadModel(modelId: String, modelPath: String, texturesDir: String, flags: Int): ModelData {
            Logger.debug("Loading model data [{}]", modelPath)
            if (!File(modelPath).exists()) {
                throw RuntimeException("Model path does not exist [$modelPath]")
            }
            if (!File(texturesDir).exists()) {
                throw RuntimeException("Textures path does not exist [$texturesDir]")
            }
            val aiScene = aiImportFile(modelPath, flags)
                ?: throw RuntimeException("Error loading model [modelPath: $modelPath, texturesDir: $texturesDir]")

            val numMaterials = aiScene.mNumMaterials()
            val materialList = ArrayList<ModelData.Material>()
            for (i in 0 until numMaterials) {
                val aiMaterial = AIMaterial.create(aiScene.mMaterials()!![i])
                val material = processMaterial(aiMaterial, texturesDir)
                materialList.add(material)
            }

            val numMeshes = aiScene.mNumMeshes()
            val aiMeshes = aiScene.mMeshes()!!
            val meshDataList = ArrayList<ModelData.MeshData>()
            for (i in 0 until numMeshes) {
                val aiMesh = AIMesh.create(aiMeshes[i])
                val meshData = processMesh(aiMesh)
                meshDataList.add(meshData)
            }

            val modelData = ModelData(modelId, meshDataList, materialList)

            aiReleaseImport(aiScene)
            Logger.debug("Loaded model [{}]", modelPath)
            return modelData
        }

        private fun processMaterial(aiMaterial: AIMaterial, texturesDir: String): ModelData.Material {
            MemoryStack.stackPush().use { stack ->
                val color = AIColor4D.create()
                var diffuse = ModelData.Material.DEFAULT_COLOR
                var result = aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_DIFFUSE, aiTextureType_NONE, 0, color)
                if (result == aiReturn_SUCCESS) {
                    diffuse = Vector4f(color.r(), color.g(), color.b(), color.a())
                }
                val aiDiffuseMapPath = AIString.calloc(stack)
                aiGetMaterialTexture(
                    aiMaterial, aiTextureType_DIFFUSE, 0, aiDiffuseMapPath,
                    null as IntBuffer?, null, null, null, null, null
                )
                var diffuseMapPath = aiDiffuseMapPath.dataString()
                if (diffuseMapPath.isNotEmpty()) {
                    diffuseMapPath = texturesDir + File.separator + File(diffuseMapPath).name
                    diffuse = Vector4f(0f, 0f, 0f, 0f)
                }
                val aiNormalMapPath = AIString.calloc(stack)
                Assimp.aiGetMaterialTexture(aiMaterial, aiTextureType_NORMALS, 0, aiNormalMapPath,
                    null as IntBuffer?, null, null, null, null, null)
                var normalMapPath = aiNormalMapPath.dataString()
                if (normalMapPath.isNotEmpty()) {
                    normalMapPath = texturesDir + File.separator + File(normalMapPath).name
                }

                val aiMetallicRoughnessPath = AIString.calloc(stack)
                aiGetMaterialTexture(
                    aiMaterial,
                    AI_MATKEY_GLTF_PBRMETALLICROUGHNESS_METALLICROUGHNESS_TEXTURE,
                    0,
                    aiMetallicRoughnessPath,
                    null as IntBuffer?,
                    null,
                    null,
                    null,
                    null,
                    null
                )
                var metallicRoughnessPath = aiMetallicRoughnessPath.dataString()
                if (metallicRoughnessPath != null && metallicRoughnessPath.length > 0) {
                    metallicRoughnessPath = texturesDir + File.separator + File(metallicRoughnessPath).name
                }

                val metallicArr = floatArrayOf(0.0f)
                val pMax = intArrayOf(1)
                result = aiGetMaterialFloatArray(
                    aiMaterial,
                    AI_MATKEY_METALLIC_FACTOR,
                    aiTextureType_NONE,
                    0,
                    metallicArr,
                    pMax
                )
                if (result != aiReturn_SUCCESS) {
                    metallicArr[0] = 1.0f
                }

                val roughnessArr = floatArrayOf(0.0f)
                result = aiGetMaterialFloatArray(
                    aiMaterial,
                    AI_MATKEY_ROUGHNESS_FACTOR,
                    aiTextureType_NONE,
                    0,
                    roughnessArr,
                    pMax
                )
                if (result != aiReturn_SUCCESS) {
                    roughnessArr[0] = 1.0f
                }
                return ModelData.Material(diffuseMapPath, normalMapPath, metallicRoughnessPath,
                    diffuse, roughnessArr[0], metallicArr[0])
            }
        }

        private fun processMesh(aiMesh: AIMesh): ModelData.MeshData {
            val vertices = processVertices(aiMesh)
            val normals = processNormals(aiMesh)
            val tangents = processTangents(aiMesh, normals)
            val biTangents = processBitangents(aiMesh, normals)
            val textCoords = processTextCoords(aiMesh)
            val indices = processIndices(aiMesh)
            if (textCoords.isEmpty()) {
                val numElements = (vertices.size / 3) * 2
                for (i in 0 until numElements) {
                    textCoords.add(0.0f)
                }
            }
            val materialIdx = aiMesh.mMaterialIndex()
            return ModelData.MeshData(
                vertices.toFloatArray(),
                normals.toFloatArray(),
                tangents.toFloatArray(),
                biTangents.toFloatArray(),
                textCoords.toFloatArray(),
                indices.toIntArray(),
                materialIdx
            )
        }

        private fun processVertices(aiMesh: AIMesh): List<Float> {
            val vertices = ArrayList<Float>()
            val aiVertices = aiMesh.mVertices()
            while (aiVertices.remaining() > 0) {
                val aiVertex = aiVertices.get()
                vertices.add(aiVertex.x())
                vertices.add(aiVertex.y())
                vertices.add(aiVertex.z())
            }
            return vertices
        }

        private fun processBitangents(aiMesh: AIMesh, normals: List<Float>): List<Float> {
            var biTangents: MutableList<Float> = ArrayList()
            val aiBitangents = aiMesh.mBitangents()
            while (aiBitangents != null && aiBitangents.remaining() > 0) {
                val aiBitangent = aiBitangents.get()
                biTangents.add(aiBitangent.x())
                biTangents.add(aiBitangent.y())
                biTangents.add(aiBitangent.z())
            }

            // Assimp may not calculate tangents with models that do not have texture coordinates. Just create empty values
            if (biTangents.isEmpty()) {
                biTangents = ArrayList(Collections.nCopies(normals.size, 0.0f))
            }
            return biTangents
        }

        private fun processNormals(aiMesh: AIMesh): List<Float> {
            val normals: MutableList<Float> = ArrayList()
            val aiNormals = aiMesh.mNormals()
            while (aiNormals != null && aiNormals.remaining() > 0) {
                val aiNormal = aiNormals.get()
                normals.add(aiNormal.x())
                normals.add(aiNormal.y())
                normals.add(aiNormal.z())
            }
            return normals
        }

        private fun processTangents(aiMesh: AIMesh, normals: List<Float>): List<Float> {
            var tangents: MutableList<Float> = ArrayList()
            val aiTangents = aiMesh.mTangents()
            while (aiTangents != null && aiTangents.remaining() > 0) {
                val aiTangent = aiTangents.get()
                tangents.add(aiTangent.x())
                tangents.add(aiTangent.y())
                tangents.add(aiTangent.z())
            }

            // Assimp may not calculate tangents with models that do not have texture coordinates. Just create empty values
            if (tangents.isEmpty()) {
                tangents = ArrayList(Collections.nCopies(normals.size, 0.0f))
            }
            return tangents
        }

        private fun processTextCoords(aiMesh: AIMesh): MutableList<Float> {
            val textCoords = ArrayList<Float>()
            val aiTextCoords = aiMesh.mTextureCoords(0)
            val numTextCoords = aiTextCoords?.remaining() ?: 0
            for (i in 0 until numTextCoords) {
                val textCoord = aiTextCoords!!.get()
                textCoords.add(textCoord.x())
                textCoords.add(1 - textCoord.y())
            }
            return textCoords
        }

        private fun processIndices(aiMesh: AIMesh): List<Int> {
            val indices = ArrayList<Int>()
            val numFaces = aiMesh.mNumFaces()
            val aiFaces = aiMesh.mFaces()
            for (i in 0 until numFaces) {
                val aiFace = aiFaces[i]
                val buffer = aiFace.mIndices()
                while (buffer.remaining() > 0) {
                    indices.add(buffer.get())
                }
            }
            return indices
        }
    }
}