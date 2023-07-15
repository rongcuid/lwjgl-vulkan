package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.memoryTypeFromProperties
import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK12.*
class Image(device: Device, imageData: ImageData) {
    val vkImage: Long
    val vkMemory: Long
    val device: Device
    val format: Int
    val mipLevels: Int

    init {
        this.device = device
        MemoryStack.stackPush().use { stack ->
            this.format = imageData.format
            this.mipLevels = imageData.mipLevels

            val imageCreateInfo = VkImageCreateInfo.calloc(stack)
                .`sType$Default`()
                .imageType(VK_IMAGE_TYPE_2D)
                .format(format)
                .extent{it.width(imageData.width).height(imageData.height).depth(1)}
                .mipLevels(mipLevels)
                .arrayLayers(imageData.arrayLayers)
                .samples(imageData.sampleCount)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .tiling(VK_IMAGE_TILING_OPTIMAL)
                .usage(imageData.usage)
            val lp = stack.mallocLong(1)
            vkCheck(vkCreateImage(device.vkDevice, imageCreateInfo, null, lp),
                "Failed to create image")
            vkImage = lp[0]
            // Get memory requirements for this object
            val memReqs = VkMemoryRequirements.calloc(stack)
            vkGetImageMemoryRequirements(device.vkDevice, vkImage, memReqs)
            // Select memory size and type
            val memAlloc = VkMemoryAllocateInfo.calloc(stack)
                .`sType$Default`()
                .allocationSize(memReqs.size())
                .memoryTypeIndex(memoryTypeFromProperties(device.physicalDevice, memReqs.memoryTypeBits(), 0))
            // Allocate memory
            vkCheck(vkAllocateMemory(device.vkDevice, memAlloc, null, lp),
                "Failed to allocate memory")
            vkMemory = lp[0]
            // Bind memory
            vkCheck(vkBindImageMemory(device.vkDevice, vkImage, vkMemory, 0),
                "Failed to bind image memory")
        }
    }
    fun cleanup() {
        vkDestroyImage(device.vkDevice, vkImage, null)
        vkFreeMemory(device.vkDevice, vkMemory, null)
    }

    class ImageData {
        var format: Int = VK_FORMAT_R8G8B8A8_SRGB
        var mipLevels: Int = 1
        var sampleCount: Int = 1
        var arrayLayers: Int = 1
        var width: Int = 0
        var height: Int = 0
        var usage: Int = 0

        fun arrayLayers(arrayLayers: Int): ImageData {
            this.arrayLayers = arrayLayers
            return this
        }
        fun format(format: Int): ImageData {
            this.format = format
            return this
        }
        fun width(width: Int): ImageData {
            this.width = width
            return this
        }
        fun height(height: Int): ImageData {
            this.height = height
            return this
        }
        fun mipLevels(mipLevels: Int): ImageData {
            this.mipLevels = mipLevels
            return this
        }
        fun sampleCount(sampleCount: Int): ImageData {
            this.sampleCount = sampleCount
            return this
        }
        fun usage(usage: Int): ImageData {
            this.usage = usage
            return this
        }
    }
}