package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.tinylog.kotlin.Logger
import org.lwjgl.system.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*

class TextureDescriptorSet(descriptorPool: DescriptorPool, descriptorSetLayout: DescriptorSetLayout,
                           texture: Texture, textureSampler: TextureSampler, binding: Int) : DescriptorSet() {
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
            vkCheck(
                vkAllocateDescriptorSets(device.vkDevice, allocInfo, pDescriptorSet),
                "Failed to create descriptor set"
            )
            vkDescriptorSet = pDescriptorSet[0]
            val imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .imageView(texture.imageView.vkImageView)
                .sampler(textureSampler.vkSampler)
            val descrBuffer = VkWriteDescriptorSet.calloc(1, stack)
            descrBuffer[0]
                .`sType$Default`()
                .dstSet(vkDescriptorSet)
                .dstBinding(binding)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1)
                .pImageInfo(imageInfo)
            vkUpdateDescriptorSets(device.vkDevice, descrBuffer, null)
        }
    }
}