package eng.graph.vk

import org.lwjgl.vulkan.VK13.VK_MAX_MEMORY_TYPES
import org.lwjgl.vulkan.VK13.VK_SUCCESS

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
    }
}