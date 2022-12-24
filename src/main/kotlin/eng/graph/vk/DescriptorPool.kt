package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.tinylog.kotlin.Logger
import org.lwjgl.system.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*

class DescriptorPool(val device: Device, descriptorTypeCounts: List<DescriptorTypeCount>) {
    val vkDescriptorPool: Long
    init {
        Logger.debug("Creating descriptor pool")
        MemoryStack.stackPush().use { stack ->
            var maxSets = 0
            val numTypes = descriptorTypeCounts.size
            val typeCounts = VkDescriptorPoolSize.calloc(numTypes, stack)
            for (i in 0 until numTypes) {
                maxSets += descriptorTypeCounts[i].count
                typeCounts[i]
                    .type(descriptorTypeCounts[i].descriptorType)
                    .descriptorCount(descriptorTypeCounts[i].count)
            }
            val dpCI = VkDescriptorPoolCreateInfo.calloc(stack)
                .`sType$Default`()
                .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
                .pPoolSizes(typeCounts)
                .maxSets(maxSets)
            val pDescriptorPool = stack.mallocLong(1)
            vkCheck(vkCreateDescriptorPool(device.vkDevice, dpCI, null, pDescriptorPool),
                "Failed to create descriptor pool")
            vkDescriptorPool = pDescriptorPool[0]
        }
    }

    fun freeDescriptorSet(vkDescriptorSet: Long) {
        MemoryStack.stackPush().use { stack ->
            val longBuffer = stack.mallocLong(1)
            longBuffer.put(0, vkDescriptorSet)
            vkCheck(VK10.vkFreeDescriptorSets(device.vkDevice, vkDescriptorPool, longBuffer),
                "Failed to free descriptor set")
        }
    }
    fun cleanup() {
        Logger.debug("Destroying descriptor pool")
        vkDestroyDescriptorPool(device.vkDevice, vkDescriptorPool, null)
    }
    data class DescriptorTypeCount(val count: Int, val descriptorType: Int)
}