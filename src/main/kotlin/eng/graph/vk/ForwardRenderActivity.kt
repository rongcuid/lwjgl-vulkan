package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*

class ForwardRenderActivity(swapChain: SwapChain, commandPool: CommandPool) {
    val swapChain: SwapChain
    val renderPass: SwapChainRenderPass
    val frameBuffers: Array<FrameBuffer>
    val commandBuffers: Array<CommandBuffer>
    val fences: Array<Fence>

    init {
        this.swapChain = swapChain
        MemoryStack.stackPush().use { stack ->
            val device = swapChain.device
            val swapChainExtent = swapChain.swapChainExtent
            val imageViews = swapChain.imageViews
            val numImages = imageViews.size
            renderPass = SwapChainRenderPass(swapChain)
            val pAttachments = stack.mallocLong(1)
            frameBuffers = Array(numImages) {
                pAttachments.put(0, imageViews[it].vkImageView)
                FrameBuffer(
                    device,
                    swapChainExtent.width(),
                    swapChainExtent.height(),
                    pAttachments,
                    renderPass.vkRenderPass
                )
            }
            commandBuffers = Array(numImages) { CommandBuffer(commandPool, true, false) }
            fences = Array(numImages) { Fence(device, true) }
            for (i in 0 until numImages) {
                recordCommandBuffer(commandBuffers[i], frameBuffers[i], swapChainExtent.width(), swapChainExtent.height())
            }
        }
    }

    fun cleanup() {
        frameBuffers.forEach(FrameBuffer::cleanup)
        renderPass.cleanup()
        commandBuffers.forEach(CommandBuffer::cleanup)
        fences.forEach(Fence::cleanup)
    }

    private fun recordCommandBuffer(commandBuffer: CommandBuffer, frameBuffer: FrameBuffer, width: Int, height: Int) {
        MemoryStack.stackPush().use { stack ->
            val clearValues = VkClearValue.calloc(1, stack)
            clearValues.apply(0) { v ->
                v.color().float32(0, 0.5f).float32(1, 0.7f).float32(2, 0.9f).float32(3, 1f)
            }
            val renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                .`sType$Default`()
                .renderPass(renderPass.vkRenderPass)
                .pClearValues(clearValues)
                .renderArea { a -> a.extent().set(width, height) }
                .framebuffer(frameBuffer.vkFrameBuffer)
            commandBuffer.beginRecording()
            vkCmdBeginRenderPass(commandBuffer.vkCommandBuffer, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE)
            vkCmdEndRenderPass(commandBuffer.vkCommandBuffer)
            commandBuffer.endRecording()
        }
    }

    fun submit(queue: Queue) {
        MemoryStack.stackPush().use { stack ->
            val idx = swapChain.currentFrame
            val commandBuffer = commandBuffers[idx]
            val currentFence = fences[idx]
            currentFence.fenceWait()
            currentFence.reset()
            val syncSemaphores = swapChain.syncSemaphoresList[idx]
            queue.submit(
                stack.pointers(commandBuffer.vkCommandBuffer),
                stack.longs(syncSemaphores.imgAcquisitionSemaphore.vkSemaphore),
                stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT),
                stack.longs(syncSemaphores.renderCompleteSemaphore.vkSemaphore),
                currentFence
            )
        }
    }
}