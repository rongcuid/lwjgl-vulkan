package eng.graph.vk

import org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT
import org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT
import org.lwjgl.vulkan.VK13.*


class Attachment {
    val image: Image
    val imageView: ImageView
    val isDepthAttachment: Boolean
    constructor(image: Image, imageView: ImageView, isDepthAttachment: Boolean) {
        this.image = image
        this.imageView = imageView
        this.isDepthAttachment = isDepthAttachment
    }
    constructor(device: Device, width: Int, height: Int, format: Int, usage: Int) {
        val imageData = Image.ImageData().width(width).height(height)
            .usage(usage or VK_IMAGE_USAGE_SAMPLED_BIT)
            .format(format)
        image = Image(device, imageData)
        val aspectMask = calcAspectMask(usage)
        isDepthAttachment = aspectMask == VK_IMAGE_ASPECT_DEPTH_BIT

        val imageViewData = ImageView.ImageViewData().format(image.format)
        imageView = ImageView(device, image.vkImage, imageViewData)
    }

    fun cleanup() {
        imageView.cleanup()
        image.cleanup()
    }
    companion object {
        fun calcAspectMask(usage: Int): Int {
            var aspectMask = 0
            if (usage and VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT > 0) {
                aspectMask = VK_IMAGE_ASPECT_COLOR_BIT
            }
            if (usage and VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT > 0) {
                aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT
            }
            return aspectMask
        }
    }
}