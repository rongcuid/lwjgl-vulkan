package eng.graph.lighting

import eng.graph.vk.FrameBuffer
import eng.graph.vk.SwapChain
import org.lwjgl.system.MemoryStack
import org.tinylog.kotlin.Logger

class LightingFrameBuffer(swapChain: SwapChain) {
    val lightingRenderPass: LightingRenderPass

    var frameBuffers: Array<FrameBuffer>

    init {
        Logger.debug("Creating LightingFrameBuffer")
        lightingRenderPass = LightingRenderPass(swapChain)
        frameBuffers = createFrameBuffers(swapChain)
    }

    private fun createFrameBuffers(swapChain: SwapChain): Array<FrameBuffer> {
        MemoryStack.stackPush().use { stack ->
            val extent2D = swapChain.swapChainExtent
            val width = extent2D.width()
            val height = extent2D.height()
            val numImages = swapChain.numImages
            val attachmentsBuf = stack.mallocLong(1)
            return Array(numImages) {
                attachmentsBuf.put(0, swapChain.imageViews[it].vkImageView)
                FrameBuffer(swapChain.device, width, height, attachmentsBuf, lightingRenderPass.vkRenderPass)
            }
        }
    }

    fun cleanup() {
        Logger.debug("Destroying LightingFrameBuffer")
        frameBuffers.forEach(FrameBuffer::cleanup)
        lightingRenderPass.cleanup()
    }

    fun resize(swapChain: SwapChain) {
        frameBuffers.forEach(FrameBuffer::cleanup)
        frameBuffers = createFrameBuffers(swapChain)
    }
}