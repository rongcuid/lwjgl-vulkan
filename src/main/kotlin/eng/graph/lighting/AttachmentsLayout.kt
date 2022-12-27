package eng.graph.lighting

import eng.graph.vk.DescriptorSetLayout
import eng.graph.vk.Device
import eng.graph.vk.VulkanUtils
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK13
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo
import org.tinylog.kotlin.Logger

class AttachmentsLayout(device: Device, numAttachments: Int) :
    DescriptorSetLayout(device) {
    override val vkDescriptorLayout: Long
    init {
        Logger.debug("Creating AttachmentsLayout with [{}] attachments", numAttachments)
        MemoryStack.stackPush().use { stack ->
            val layoutBindings = VkDescriptorSetLayoutBinding.calloc(numAttachments, stack)
            for (i in 0 until numAttachments) {
                layoutBindings[i]
                    .binding(i)
                    .descriptorType(VK13.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK13.VK_SHADER_STAGE_FRAGMENT_BIT)
            }
            val layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .`sType$Default`()
                .pBindings(layoutBindings)
            val lp = stack.mallocLong(1)
            VulkanUtils.vkCheck(
                VK13.vkCreateDescriptorSetLayout(device.vkDevice, layoutInfo, null, lp),
                "Failed to create descriptor set layout"
            )
            vkDescriptorLayout = lp[0]
        }
    }
}