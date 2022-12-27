package eng.graph.geometry

import eng.graph.vk.Attachment
import eng.graph.vk.Device
import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*

class GeometryRenderPass(val device: Device, attachments: List<Attachment>) {
    val vkRenderPass: Long

    init {
        MemoryStack.stackPush().use { stack ->
            val numAttachments = attachments.size
            val attachmentsDesc = VkAttachmentDescription.calloc(numAttachments, stack)
            var depthAttachmentPos = 0
            for (i in 0 until numAttachments) {
                val attachment = attachments[i]
                attachmentsDesc[i]
                    .format(attachment.image.format)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .samples(MAX_SAMPLES)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                if (attachment.isDepthAttachment) {
                    depthAttachmentPos = i
                    attachmentsDesc[i].finalLayout(VK_IMAGE_LAYOUT_DEPTH_READ_ONLY_OPTIMAL)
                } else {
                    attachmentsDesc[i].finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                }
            }
            val colorReferences = VkAttachmentReference.calloc(GeometryAttachments.NUMBER_COLOR_ATTACHMENTS, stack)
            for (i in 0 until GeometryAttachments.NUMBER_COLOR_ATTACHMENTS) {
                colorReferences[i]
                    .attachment(i)
                    .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
            }
            val depthReference = VkAttachmentReference.calloc(stack)
                .attachment(depthAttachmentPos)
                .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
            // Render subpass
            val subpass = VkSubpassDescription.calloc(1, stack)
                .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .pColorAttachments(colorReferences)
                .colorAttachmentCount(colorReferences.capacity())
                .pDepthStencilAttachment(depthReference)
            // Subpass dependencies
            val subpassDependencies = VkSubpassDependency.calloc(2, stack)
            subpassDependencies[0]
                .srcSubpass(VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .srcAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT or VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT)
            subpassDependencies[1]
                .srcSubpass(0)
                .dstSubpass(VK_SUBPASS_EXTERNAL)
                .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT or VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT)
            // Render pass
            val renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                .`sType$Default`()
                .pAttachments(attachmentsDesc)
                .pSubpasses(subpass)
                .pDependencies(subpassDependencies)
            val lp = stack.mallocLong(1)
            vkCheck(vkCreateRenderPass(device.vkDevice, renderPassInfo, null, lp),
                "Failed to create render pass")
            vkRenderPass = lp[0]
        }
    }

    fun cleanup() {
        vkDestroyRenderPass(device.vkDevice, vkRenderPass, null)
    }

    companion object {
        val MAX_SAMPLES: Int = 1
    }
}