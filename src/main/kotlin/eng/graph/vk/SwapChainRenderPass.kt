package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK12.*

class SwapChainRenderPass(swapChain: SwapChain, depthImageFormat: Int) {
    val swapChain: SwapChain
    val vkRenderPass: Long
    init {
        this.swapChain =  swapChain

        MemoryStack.stackPush().use { stack ->
            val attachments = VkAttachmentDescription.calloc(2, stack)
            // Color attachment
            attachments[0]
                .format(swapChain.surfaceFormat.imageFormat)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .finalLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
            // Depth attachment
            attachments[1]
                .format(depthImageFormat)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
            val colorReference = VkAttachmentReference.calloc(1, stack)
                .attachment(0)
                .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
            val depthReference = VkAttachmentReference.malloc(stack)
                .attachment(1)
                .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
            val subPass = VkSubpassDescription.calloc(1, stack)
                .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(colorReference.remaining())
                .pColorAttachments(colorReference)
                .pDepthStencilAttachment(depthReference)
            val subpassDependencies = VkSubpassDependency.calloc(1, stack)
            subpassDependencies[0]
                .srcSubpass(VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .srcAccessMask(0)
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
            val renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                .`sType$Default`()
                .pAttachments(attachments)
                .pSubpasses(subPass)
                .pDependencies(subpassDependencies)
            val lp = stack.mallocLong(1)
            vkCheck(vkCreateRenderPass(swapChain.device.vkDevice, renderPassInfo, null, lp),
                "Failed to create render pass")
            vkRenderPass = lp[0]
        }
    }

    fun cleanup() {
        vkDestroyRenderPass(swapChain.device.vkDevice, vkRenderPass, null)
    }
}