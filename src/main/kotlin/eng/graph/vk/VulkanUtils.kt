package eng.graph.vk

import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK12.*
import org.lwjgl.vulkan.VkCommandBuffer

class VulkanUtils {
    companion object {
        fun vkCheck(err: Int, errMsg: String) {
            if (err != VK_SUCCESS) {
                throw RuntimeException("$errMsg: $err")
            }
        }
        fun memoryTypeFromProperties(physDevice: PhysicalDevice, typeBits: Int, reqMask: Int): Int {
            var result = -1
            var typeBits = typeBits
            val memoryTypes = physDevice.vkMemoryProperties.memoryTypes()
            for (i in 0 until VK_MAX_MEMORY_TYPES) {
                if ((typeBits and 1) == 1 && (memoryTypes[i].propertyFlags() and reqMask) == reqMask) {
                    result = i
                    break
                }
                typeBits = typeBits shr 1
            }
            if (result < 0) {
                throw RuntimeException("Failed to find memoryType")
            }
            return result
        }

        fun copyMatrixToBuffer(buffer: VulkanBuffer, matrix: Matrix4f) {
            copyMatrixToBuffer(buffer, matrix, 0)
        }

        fun copyMatrixToBuffer(buffer: VulkanBuffer, matrix: Matrix4f, offset: Int) {
            val mappedMemory = buffer.map()
            val matrixBuffer = MemoryUtil.memByteBuffer(mappedMemory, buffer.requestedSize.toInt())
            matrix.get(offset, matrixBuffer)
            buffer.unmap()
        }

        fun setMatrixAsPushConstant(pipeline: Pipeline, cmdHandle: VkCommandBuffer, matrix: Matrix4f) {
            MemoryStack.stackPush().use {stack ->
                val pushConstantBuffer = stack.malloc(GraphConstants.MAT4X4_SIZE)
                matrix.get(0, pushConstantBuffer)
                vkCmdPushConstants(cmdHandle, pipeline.vkPipelineLayout,
                    VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstantBuffer)
            }
        }
    }
}