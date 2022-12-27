package eng.graph.shadows

import eng.graph.vk.*
import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*

class ShadowRenderPass(device: Device, depthAttachment: Attachment) {
    private val device = device
    val vkRenderPass: Long

    init {
        MemoryStack.stackPush().use { stack ->
            val attachmentsDesc = VkAttachmentDescription.calloc(1, stack)
            attachmentsDesc[0]
                .format(depthAttachment.image.format)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .stencilStoreOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .samples(MAX_SAMPLES)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL)
            val depthReferences = VkAttachmentReference.calloc(stack)
                .attachment(0)
                .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
            // Render subpass
            val subpass = VkSubpassDescription.calloc(1, stack)
                .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .pDepthStencilAttachment(depthReferences)
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
                .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT)
                .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT)
            // Render pass
            val renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                .`sType$Default`()
                .pAttachments(attachmentsDesc)
                .pSubpasses(subpass)
                .pDependencies(subpassDependencies)
            val lp = stack.mallocLong(1)
            vkCheck(
                VK10.vkCreateRenderPass(device.vkDevice, renderPassInfo, null, lp),
                "Failed to create render pass"
            )
            vkRenderPass = lp[0]
        }
    }

    fun cleanup() {
        vkDestroyRenderPass(device.vkDevice, vkRenderPass, null)
    }

    companion object {
        private const val MAX_SAMPLES = 1
    }
}