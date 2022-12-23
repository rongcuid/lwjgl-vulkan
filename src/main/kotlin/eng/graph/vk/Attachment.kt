package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.memoryTypeFromProperties
import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*
class Attachment(device: Device, width: Int, height: Int, format: Int, usage: Int) {
    val image: Image
    val imageView: ImageView
    val depthAttachment: Boolean

    init {
        val imageData = Image.ImageData().width(width).height(height)
            .usage(usage or VK_IMAGE_USAGE_SAMPLED_BIT)
            .format(format)
        image = Image(device, imageData)

        var aspectMask = 0
        var _depthAttachment = false
        if ((usage and VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) > 0) {
            aspectMask = VK_IMAGE_ASPECT_COLOR_BIT
            _depthAttachment = false
        }
        if ((usage and VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT) > 0) {
            aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT
            _depthAttachment = true
        }
        depthAttachment = _depthAttachment

        val imageViewData = ImageView.ImageViewData().format(image.format).aspectMask(aspectMask)
        imageView = ImageView(device, image.vkImage, imageViewData)
    }
    fun cleanup() {
        imageView.cleanup()
        image.cleanup()
    }
}