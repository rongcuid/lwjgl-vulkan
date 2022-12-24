package eng.scene

import eng.EngineProperties
import org.joml.*
import org.lwjgl.assimp.*
import org.lwjgl.assimp.Assimp.*
import org.lwjgl.system.*
import org.tinylog.kotlin.Logger
import java.io.File
import java.nio.IntBuffer

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
                val result = aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_DIFFUSE, aiTextureType_NONE, 0, color)
                if (result == aiReturn_SUCCESS) {
                    diffuse = Vector4f(color.r(), color.g(), color.b(), color.a())
                }
                val aiTexturePath = AIString.calloc(stack)
                aiGetMaterialTexture(
                    aiMaterial, aiTextureType_DIFFUSE, 0, aiTexturePath,
                    null as IntBuffer?, null, null, null, null, null
                )
                var texturePath = aiTexturePath.dataString()
                if (texturePath.length > 0) {
                    texturePath = texturesDir + File.separator + File(texturePath).name
                    diffuse = Vector4f(0f, 0f, 0f, 0f)
                }
                return ModelData.Material(texturePath, diffuse)
            }
        }

        private fun processMesh(aiMesh: AIMesh): ModelData.MeshData {
            val vertices = processVertices(aiMesh)
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