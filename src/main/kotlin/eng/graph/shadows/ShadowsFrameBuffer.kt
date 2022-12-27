package eng.graph.shadows

import eng.EngineProperties
import eng.graph.vk.*
import org.lwjgl.system.MemoryStack
import org.tinylog.kotlin.Logger
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*

class ShadowsFrameBuffer(device: Device) {

    var frameBuffer: FrameBuffer
    var shadowsRenderPass: ShadowRenderPass
    var depthAttachment: Attachment

    init {
        Logger.debug("Creating ShadowsFrameBuffer")
        MemoryStack.stackPush().use { stack ->
            val usage = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT or VK_IMAGE_USAGE_SAMPLED_BIT
            val engineProps = EngineProperties.instance
            val shadowMapSize = engineProps.shadowMapSize
            val imageData = Image.ImageData().width(shadowMapSize).height(shadowMapSize)
                .usage(usage or VK_IMAGE_USAGE_SAMPLED_BIT)
                .format(VK_FORMAT_D32_SFLOAT).arrayLayers(GraphConstants.SHADOW_MAP_CASCADE_COUNT)
            val depthImage = Image(device, imageData)

            val aspectMask = Attachment.calcAspectMask(usage)

            val imageViewData = ImageView.ImageViewData()
                .format(depthImage.format).aspectMask(aspectMask).viewType(VK_IMAGE_VIEW_TYPE_2D_ARRAY)
                .layerCount(GraphConstants.SHADOW_MAP_CASCADE_COUNT)
            val depthImageView = ImageView(device, depthImage.vkImage, imageViewData)
            depthAttachment = Attachment(depthImage, depthImageView, true)

            shadowsRenderPass = ShadowRenderPass(device, depthAttachment)

            val attachmentsBuf = stack.mallocLong(1)
            attachmentsBuf.put(0, depthAttachment.imageView.vkImageView)
            frameBuffer = FrameBuffer(
                device, shadowMapSize, shadowMapSize, attachmentsBuf,
                shadowsRenderPass.vkRenderPass, GraphConstants.SHADOW_MAP_CASCADE_COUNT
            )
        }
    }

    fun cleanup() {
        Logger.debug("Destroying ShadowsFrameBuffer")
        shadowsRenderPass.cleanup()
        depthAttachment.cleanup()
        frameBuffer.cleanup()
    }
}