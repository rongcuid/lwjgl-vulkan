package eng.graph.lighting

import eng.graph.vk.*
import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*

class AttachmentsDescriptorSet(
    descriptorPool: DescriptorPool, descriptorSetlayout: AttachmentsLayout,
    attachments: List<Attachment>, val binding: Int
) :
    DescriptorSet() {
    val device: Device = descriptorPool.device
    val textureSampler: TextureSampler
    override val vkDescriptorSet: Long
    init {
        MemoryStack.stackPush().use { stack ->
            val pDescriptorSetLayout = stack.mallocLong(1)
            pDescriptorSetLayout.put(0, descriptorSetlayout.vkDescriptorLayout)
            val allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .`sType$Default`()
                .descriptorPool(descriptorPool.vkDescriptorPool)
                .pSetLayouts(pDescriptorSetLayout)
            val pDescriptorSet = stack.mallocLong(1)
            vkCheck(vkAllocateDescriptorSets(device.vkDevice, allocInfo, pDescriptorSet),
                "Failed to create descriptor set")
            vkDescriptorSet = pDescriptorSet[0]
            textureSampler = TextureSampler(device, 1)
            update(attachments)
        }
    }
    fun cleanup() {
        textureSampler.cleanup()
    }

    fun update(attachments: List<Attachment>) {
        MemoryStack.stackPush().use { stack ->
            val numAttachments = attachments.size
            val descrBuffer = VkWriteDescriptorSet.calloc(numAttachments, stack)
            for (i in 0 until numAttachments) {
                val attachment = attachments[i]
                val imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                    .sampler(textureSampler.vkSampler)
                    .imageView(attachment.imageView.vkImageView)
                if (attachment.isDepthAttachment) {
                    imageInfo.imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL)
                } else {
                    imageInfo.imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                }

                descrBuffer[i]
                    .`sType$Default`()
                    .dstSet(vkDescriptorSet)
                    .dstBinding(binding + i)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(imageInfo)
            }
            vkUpdateDescriptorSets(device.vkDevice, descrBuffer, null)
        }
    }
}