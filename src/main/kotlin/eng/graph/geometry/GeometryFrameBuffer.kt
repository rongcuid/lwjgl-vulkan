package eng.graph.geometry

import eng.graph.vk.FrameBuffer
import eng.graph.vk.SwapChain
import org.lwjgl.system.MemoryStack
import org.tinylog.kotlin.Logger

class GeometryFrameBuffer(swapchain: SwapChain) {
    val geometryRenderPass: GeometryRenderPass
    var frameBuffer: FrameBuffer private set
    var geometryAttachments: GeometryAttachments private set

    init {
        Logger.debug("Creating GeometryFrameBuffer")
        geometryAttachments = createAttachments(swapchain)
        geometryRenderPass = GeometryRenderPass(swapchain.device, geometryAttachments.attachments)
        frameBuffer = createFrameBuffer(swapchain)
    }

    private fun createFrameBuffer(swapchain: SwapChain): FrameBuffer {
        MemoryStack.stackPush().use { stack ->
            val attachments = geometryAttachments.attachments
            val attachmentsBuf = stack.mallocLong(attachments.size)
            for (attachment in attachments) {
                attachmentsBuf.put(attachment.imageView.vkImageView)
            }
            attachmentsBuf.flip()
            return FrameBuffer(swapchain.device, geometryAttachments.width, geometryAttachments.height,
                attachmentsBuf, geometryRenderPass.vkRenderPass)
        }
    }

    private fun createAttachments(swapchain: SwapChain): GeometryAttachments {
        val extent2D = swapchain.swapChainExtent
        val width = extent2D.width()
        val height = extent2D.height()
        return GeometryAttachments(swapchain.device, width, height)
    }

    fun cleanup() {
        Logger.debug("Destroying GeometryFrameBuffer")
        geometryRenderPass.cleanup()
        geometryAttachments.cleanup()
        frameBuffer.cleanup()
    }

    fun resize(swapchain: SwapChain) {
        frameBuffer.cleanup()
        geometryAttachments.cleanup()
        geometryAttachments = createAttachments(swapchain)
        frameBuffer = createFrameBuffer(swapchain)
    }
}