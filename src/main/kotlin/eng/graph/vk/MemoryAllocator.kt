package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.system.*
import org.lwjgl.util.vma.*
import org.lwjgl.util.vma.Vma.*
import org.lwjgl.vulkan.*

class MemoryAllocator(instance: Instance, physicalDevice: PhysicalDevice, vkDevice: VkDevice) {
    val vmaAllocator: Long

    init {
        MemoryStack.stackPush().use { stack ->
            val pAllocator = stack.mallocPointer(1)
            val vmaVulkanFunctions = VmaVulkanFunctions.calloc(stack)
                .set(instance.vkInstance, vkDevice)
            val createInfo = VmaAllocatorCreateInfo.calloc(stack)
                .instance(instance.vkInstance)
                .device(vkDevice)
                .physicalDevice(physicalDevice.vkPhysicalDevice)
                .pVulkanFunctions(vmaVulkanFunctions)
            vkCheck(vmaCreateAllocator(createInfo, pAllocator), "Failed to create VMA allocator")
            vmaAllocator = pAllocator[0]
        }
    }

    fun cleanup() {
        vmaDestroyAllocator(vmaAllocator)
    }
}