package eng.graph.vk

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK12.*
import org.lwjgl.vulkan.VkImageViewCreateInfo
import eng.graph.vk.VulkanUtils.Companion.vkCheck

class ImageView(device: Device, vkImage: Long, imageViewData: ImageViewData) {
    val device: Device
    val vkImageView: Long
    val aspectMask: Int
    val mipLevels: Int
    init {
        this.device = device
        this.aspectMask = imageViewData.aspectMask
        this.mipLevels = imageViewData.mipLevels
        MemoryStack.stackPush().use {stack ->
            val lp = stack.mallocLong(1)
            val viewCreateInfo = VkImageViewCreateInfo.calloc(stack)
                .`sType$Default`()
                .image(vkImage)
                .viewType(imageViewData.viewType)
                .format(imageViewData.format)
                .subresourceRange {
                    it.aspectMask(aspectMask)
                        .baseMipLevel(0)
                        .levelCount(mipLevels)
                        .baseArrayLayer(imageViewData.baseArrayLayer)
                        .layerCount(imageViewData.layerCount)
                }
            vkCheck(vkCreateImageView(device.vkDevice, viewCreateInfo, null, lp),
                "Failed to create image view")
            vkImageView = lp[0]
        }
    }

    fun cleanup() {
        vkDestroyImageView(device.vkDevice, vkImageView, null)
    }

    class ImageViewData {
        var aspectMask: Int
        var baseArrayLayer: Int
        var format: Int
        var layerCount: Int
        var mipLevels: Int
        var viewType: Int

        init {
            aspectMask = 0
            baseArrayLayer = 0
            format = 0
            layerCount = 1
            mipLevels = 1
            viewType = VK_IMAGE_VIEW_TYPE_2D
        }
        fun aspectMask(aspectMask: Int): ImageViewData {
            this.aspectMask = aspectMask
            return this
        }
        fun baseArrayLayer(baseArrayLayer: Int): ImageViewData {
            this.baseArrayLayer = baseArrayLayer
            return this
        }
        fun format(format: Int): ImageViewData {
            this.format = format
            return this
        }
        fun layerCount(layerCount: Int): ImageViewData {
            this.layerCount = layerCount
            return this
        }
        fun mipLevels(mipLevels: Int): ImageViewData {
            this.mipLevels = mipLevels
            return this
        }
        fun viewType(viewType: Int): ImageViewData {
            this.viewType = viewType
            return this
        }
    }
}