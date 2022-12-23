package eng.graph.vk

import eng.graph.vk.ModelData.MeshData
import org.lwjgl.*
import org.lwjgl.system.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*


class VulkanModel(modelId: String) {
    val modelId: String
    var vulkanMeshList: MutableList<VulkanMesh>

    init {
        this.modelId = modelId
        vulkanMeshList = ArrayList()
    }

    fun cleanup() {
        vulkanMeshList.forEach(VulkanMesh::cleanup)
    }

    data class VulkanMesh(val verticesBuffer: VulkanBuffer, val indicesBuffer: VulkanBuffer, val numIndices: Int) {
        fun cleanup() {
            verticesBuffer.cleanup()
            indicesBuffer.cleanup()
        }
    }

    companion object {
        private fun createVerticesBuffers(device: Device, meshData: ModelData.MeshData): TransferBuffers {
            val positions = meshData.positions
            val numPositions = positions.size
            val bufferSize = numPositions * GraphConstants.FLOAT_LENGTH
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
            data.put(positions)
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

        fun transformModels(modelDataList: List<ModelData>, commandPool: CommandPool, queue: Queue): List<VulkanModel> {
            val vulkanModelList = ArrayList<VulkanModel>()
            val device = commandPool.device
            val cmd = CommandBuffer(commandPool, true, true)
            val stagingBufferList = ArrayList<VulkanBuffer>()
            cmd.beginRecording()
            for (modelData in modelDataList) {
                val vulkanModel = VulkanModel(modelData.modelId)
                vulkanModelList.add(vulkanModel)
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
                    vulkanModel.vulkanMeshList.add(vulkanMesh)
                }
            }
            cmd.endRecording()
            val fence = Fence(device, true)
            fence.reset()
            MemoryStack.stackPush().use { stack ->
                queue.submit(stack.pointers(cmd.vkCommandBuffer), null, null, null, fence)
            }
            fence.fenceWait();
            fence.cleanup()
            cmd.cleanup()
            stagingBufferList.forEach(VulkanBuffer::cleanup)
            return vulkanModelList
        }

        private fun recordTransferCommand(cmd: CommandBuffer, transferBuffers: TransferBuffers) {
            MemoryStack.stackPush().use { stack ->
                val copyRegion = VkBufferCopy.calloc(1, stack)
                    .srcOffset(0).dstOffset(0).size(transferBuffers.srcBuffer.requestedSize)
                vkCmdCopyBuffer(cmd.vkCommandBuffer, transferBuffers.srcBuffer.buffer,
                    transferBuffers.dstBuffer.buffer, copyRegion)
            }
        }
    }

    data class TransferBuffers(val srcBuffer: VulkanBuffer, val dstBuffer: VulkanBuffer)
}