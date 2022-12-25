package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.tinylog.kotlin.Logger
import org.lwjgl.system.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*
abstract class DescriptorSet {
    abstract val vkDescriptorSet: Long

    class UniformDescriptorSet(descriptorPool: DescriptorPool, descriptorSetLayout: DescriptorSetLayout,
        buffer: VulkanBuffer, binding: Int) : SimpleDescriptorSet(descriptorPool, descriptorSetLayout,
            buffer, binding, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, buffer.requestedSize)
    class DynUniformDescriptorSet(descriptorPool: DescriptorPool, descriptorSetLayout: DescriptorSetLayout,
                                  buffer: VulkanBuffer, binding: Int, size: Long) : SimpleDescriptorSet(descriptorPool, descriptorSetLayout,
        buffer, binding, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, size)

    open class SimpleDescriptorSet(descriptorPool: DescriptorPool, descriptorSetLayout: DescriptorSetLayout,
        buffer: VulkanBuffer, binding: Int, type: Int, size: Long) : DescriptorSet() {
        override val vkDescriptorSet: Long
        init {
            MemoryStack.stackPush().use { stack ->
                val device = descriptorPool.device
                val pDescriptorSetLayout = stack.mallocLong(1)
                pDescriptorSetLayout.put(0, descriptorSetLayout.vkDescriptorLayout)
                val allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .`sType$Default`()
                    .descriptorPool(descriptorPool.vkDescriptorPool)
                    .pSetLayouts(pDescriptorSetLayout)

                val pDescriptorSet = stack.mallocLong(1)
                vkCheck(vkAllocateDescriptorSets(device.vkDevice, allocInfo, pDescriptorSet),
                    "Failed to create descriptor set")
                vkDescriptorSet = pDescriptorSet[0]

                val bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                    .buffer(buffer.buffer)
                    .offset(0)
                    .range(size)

                val descrBuffer = VkWriteDescriptorSet.calloc(1, stack)
                descrBuffer[0]
                    .`sType$Default`()
                    .dstSet(vkDescriptorSet)
                    .dstBinding(binding)
                    .descriptorType(type)
                    .descriptorCount(1)
                    .pBufferInfo(bufferInfo)
                vkUpdateDescriptorSets(device.vkDevice, descrBuffer, null)
            }
        }
    }
}

