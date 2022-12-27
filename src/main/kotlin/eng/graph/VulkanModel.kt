package eng.graph

import eng.graph.vk.*
import eng.scene.ModelData
import eng.scene.ModelData.MeshData
import org.joml.Vector4f
import org.lwjgl.system.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*


class VulkanModel(val modelId: String) {
    val vulkanMaterialList = ArrayList<VulkanMaterial>()

    fun cleanup() {
        vulkanMaterialList.forEach { it.vulkanMeshList.forEach(VulkanMesh::cleanup) }
    }

    data class VulkanMesh(val verticesBuffer: VulkanBuffer, val indicesBuffer: VulkanBuffer, val numIndices: Int) {
        fun cleanup() {
            verticesBuffer.cleanup()
            indicesBuffer.cleanup()
        }
    }

    companion object {
        private fun createVerticesBuffers(device: Device, meshData: MeshData): TransferBuffers {
            val positions = meshData.positions
            val normals = meshData.normals
            val tangents = meshData.tangents
            val biTangents = meshData.biTangents
            var textCoords = meshData.textCoords
            if (textCoords == null || textCoords.isEmpty()) {
                textCoords = FloatArray(positions.size / 3 * 2)
            }
            val numElements = positions.size + normals.size + tangents.size + biTangents.size + textCoords.size
            val bufferSize = numElements * GraphConstants.FLOAT_LENGTH
            val srcBuffer = VulkanBuffer(
                device, bufferSize.toLong(),
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            )
            val dstBuffer = VulkanBuffer(
                device, bufferSize.toLong(),
                VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
            )
            val mappedMemory = srcBuffer.map()
            val data = MemoryUtil.memFloatBuffer(mappedMemory, srcBuffer.requestedSize.toInt())
            for (row in 0 until positions.size / 3) {
                val startPos = row * 3
                val startTextCoord = row * 2
                data.put(positions[startPos])
                data.put(positions[startPos + 1])
                data.put(positions[startPos + 2])
                data.put(normals[startPos])
                data.put(normals[startPos + 1])
                data.put(normals[startPos + 2])
                data.put(tangents[startPos])
                data.put(tangents[startPos + 1])
                data.put(tangents[startPos + 2])
                data.put(biTangents[startPos])
                data.put(biTangents[startPos + 1])
                data.put(biTangents[startPos + 2])
                data.put(textCoords[startTextCoord])
                data.put(textCoords[startTextCoord + 1])
            }
            srcBuffer.unmap()

            return TransferBuffers(srcBuffer, dstBuffer)
        }

        private fun createIndicesBuffers(device: Device, meshData: MeshData): TransferBuffers {
            val indices: IntArray = meshData.indices
            val numIndices = indices.size
            val bufferSize = numIndices * GraphConstants.INT_LENGTH
            val srcBuffer = VulkanBuffer(
                device,
                bufferSize.toLong(),
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            )
            val dstBuffer = VulkanBuffer(
                device,
                bufferSize.toLong(),
                VK_BUFFER_USAGE_TRANSFER_DST_BIT or VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
            )
            val mappedMemory = srcBuffer.map()
            val data = MemoryUtil.memIntBuffer(mappedMemory, srcBuffer.requestedSize.toInt())
            data.put(indices)
            srcBuffer.unmap()
            return TransferBuffers(srcBuffer, dstBuffer)
        }

        private fun transformMaterial(material: ModelData.Material, device: Device, textureCache: TextureCache,
                                      cmd: CommandBuffer, textureList: MutableList<Texture>): VulkanMaterial {
            val texture = textureCache.createTexture(device, material.texturePath, VK_FORMAT_R8G8B8A8_SRGB)
            val hasTexture = material.texturePath != null && material.texturePath.trim().isNotEmpty()
            val normalMapTexture = textureCache.createTexture(device, material.normalMapPath, VK_FORMAT_R8G8B8A8_UNORM)
            val hasNormalMapTexture = !material.normalMapPath.isNullOrEmpty()
            val metalRoughTexture = textureCache.createTexture(device, material.metalRoughMap, VK_FORMAT_R8G8B8A8_SRGB)
            val hasMetalRoughTexture = !material.metalRoughMap.isNullOrEmpty()

            texture.recordTextureTransition(cmd)
            textureList.add(texture)
            normalMapTexture.recordTextureTransition(cmd)
            textureList.add(normalMapTexture)
            metalRoughTexture.recordTextureTransition(cmd)
            textureList.add(metalRoughTexture)

            return VulkanMaterial(material.diffuseColor, texture, hasTexture,
                normalMapTexture, hasNormalMapTexture, metalRoughTexture, hasMetalRoughTexture,
                material.metallicFactor, material.roughnessFactor,
                ArrayList())
        }

        fun transformModels(modelDataList: List<ModelData>, textureCache: TextureCache, commandPool: CommandPool, queue: Queue): List<VulkanModel> {
            val vulkanModelList = ArrayList<VulkanModel>()
            val device = commandPool.device
            val cmd = CommandBuffer(commandPool, true, true)
            val stagingBufferList = ArrayList<VulkanBuffer>()
            val textureList = ArrayList<Texture>()
            cmd.beginRecording()
            for (modelData in modelDataList) {
                val vulkanModel = VulkanModel(modelData.modelId)
                vulkanModelList.add(vulkanModel)
                // Create textures defined for materials
                var defaultVulkanMaterial: VulkanMaterial? = null
                for (mat in modelData.materialList) {
                    val vulkanMaterial = transformMaterial(mat, device, textureCache, cmd, textureList)
                    vulkanModel.vulkanMaterialList.add(vulkanMaterial)
                }
                // Transform meshes loading their data into GPU buffers
                for (meshData in modelData.meshDataList) {
                    val verticesBuffers = createVerticesBuffers(device, meshData)
                    val indicesBuffers = createIndicesBuffers(device, meshData)
                    stagingBufferList.add(verticesBuffers.srcBuffer)
                    stagingBufferList.add(indicesBuffers.srcBuffer)
                    recordTransferCommand(cmd, verticesBuffers)
                    recordTransferCommand(cmd, indicesBuffers)

                    val vulkanMesh = VulkanMesh(
                        verticesBuffers.dstBuffer,
                        indicesBuffers.dstBuffer, meshData.indices.size
                    )

                    val vulkanMaterial: VulkanMaterial
                    val materialIdx = meshData.materialIdx
                    if (materialIdx >= 0 && materialIdx < vulkanModel.vulkanMaterialList.size) {
                        vulkanMaterial = vulkanModel.vulkanMaterialList[materialIdx]
                    } else {
                        if (defaultVulkanMaterial == null) {
                            defaultVulkanMaterial = transformMaterial(ModelData.Material(), device, textureCache, cmd, textureList)
                        }
                        vulkanMaterial = defaultVulkanMaterial
                    }
                    vulkanMaterial.vulkanMeshList.add(vulkanMesh)
                }
            }
            cmd.endRecording()
            val fence = Fence(device, true)
            fence.reset()
            MemoryStack.stackPush().use { stack ->
                queue.submit(stack.pointers(cmd.vkCommandBuffer), null, null, null, fence)
            }
            fence.fenceWait()
            fence.cleanup()
            cmd.cleanup()
            stagingBufferList.forEach(VulkanBuffer::cleanup)
            textureList.forEach(Texture::cleanupStgBuffer)
            return vulkanModelList
        }

        private fun recordTransferCommand(cmd: CommandBuffer, transferBuffers: TransferBuffers) {
            MemoryStack.stackPush().use { stack ->
                val copyRegion = VkBufferCopy.calloc(1, stack)
                    .srcOffset(0).dstOffset(0).size(transferBuffers.srcBuffer.requestedSize)
                vkCmdCopyBuffer(
                    cmd.vkCommandBuffer, transferBuffers.srcBuffer.buffer,
                    transferBuffers.dstBuffer.buffer, copyRegion
                )
            }
        }
    }

    data class TransferBuffers(val srcBuffer: VulkanBuffer, val dstBuffer: VulkanBuffer)

    data class VulkanMaterial(
        val diffuseColor: Vector4f,
        val texture: Texture, val hasTexture: Boolean,
        val normalMap: Texture, val hasNormalMap: Boolean,
        val metalRoughMap: Texture, val hasMetalRoughMap: Boolean,
        val metallicFactor: Float, val roughnessFactor: Float,
        val vulkanMeshList: MutableList<VulkanMesh>
    ) {
        val isTransparent: Boolean get() = texture.hasTransparencies
    }
}