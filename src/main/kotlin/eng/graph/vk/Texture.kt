package eng.graph.vk

import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK13.*
import org.lwjgl.vulkan.*
import org.tinylog.kotlin.Logger
import java.nio.ByteBuffer

class Texture(device: Device, val fileName: String, imageFormat: Int) {
    private var recordedTransition: Boolean = false
    val width: Int
    val height: Int
    private val mipLevels: Int
    private val image: Image
    val imageView: ImageView
    private var stgBuffer: VulkanBuffer?

    init {
        val buf: ByteBuffer
        MemoryStack.stackPush().use { stack ->
            val w = stack.mallocInt(1)
            val h = stack.mallocInt(1)
            val channels = stack.mallocInt(1)

            buf = stbi_load(fileName, w, h, channels, 4)
                ?: throw RuntimeException("Image file [$fileName] not loaded: ${stbi_failure_reason()}")
            width = w.get()
            height = h.get()
            mipLevels = 1

            stgBuffer = createStgBuffer(device, buf)
            val imageData = Image.ImageData().width(width).height(height)
                .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT or VK_IMAGE_USAGE_SAMPLED_BIT)
                .format(imageFormat).mipLevels(mipLevels)
            image = Image(device, imageData)
            val imageViewData = ImageView.ImageViewData()
                .format(image.format).aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevels(mipLevels)
            imageView = ImageView(device, image.vkImage, imageViewData)
            stbi_image_free(buf)
        }
    }

    fun cleanup() {
        cleanupStgBuffer()
        imageView.cleanup()
        image.cleanup()
    }

    private fun createStgBuffer(device: Device, data: ByteBuffer): VulkanBuffer {
        val size = data.remaining()
        val stgBuffer = VulkanBuffer(
            device, size.toLong(), VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        )
        val mappedMemory = stgBuffer.map()
        val buffer = MemoryUtil.memByteBuffer(mappedMemory, stgBuffer.requestedSize.toInt())
        buffer.put(data)
        data.flip()
        stgBuffer.unmap()
        return stgBuffer
    }

    fun cleanupStgBuffer() {
        stgBuffer?.cleanup()
        stgBuffer = null
    }

    fun recordTextureTransition(cmd: CommandBuffer) {
        if (stgBuffer != null && !recordedTransition) {
            Logger.debug("Recording transition for texture [$fileName]")
            recordedTransition = true
            MemoryStack.stackPush().use { stack ->
                recordImageTransition(stack, cmd, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                recordCopyBuffer(stack, cmd, stgBuffer!!)
                recordImageTransition(
                    stack,
                    cmd,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                )
            }
        } else {
            Logger.debug("Texture [$fileName] has already been transitioned")
        }
    }

    private fun recordImageTransition(stack: MemoryStack, cmd: CommandBuffer, oldLayout: Int, newLayout: Int) {
        val barrier = VkImageMemoryBarrier.calloc(1, stack)
            .`sType$Default`()
            .oldLayout(oldLayout)
            .newLayout(newLayout)
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .image(image.vkImage)
            .subresourceRange {
                it
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(mipLevels)
                    .baseArrayLayer(0)
                    .layerCount(1)
            }
        val srcStage: Int
        val srcAccessMask: Int
        val dstAccessMask: Int
        val dstStage: Int
        if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
            srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
            srcAccessMask = 0
            dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT
            dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT
        } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT
            srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT
            dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
            dstAccessMask = VK_ACCESS_SHADER_READ_BIT
        } else {
            throw RuntimeException("Unsupported layout transition")
        }
        barrier.srcAccessMask(srcAccessMask)
        barrier.dstAccessMask(dstAccessMask)
        vkCmdPipelineBarrier(cmd.vkCommandBuffer, srcStage, dstStage, 0, null, null, barrier)
    }

    private fun recordCopyBuffer(stack: MemoryStack, cmd: CommandBuffer, bufferData: VulkanBuffer) {
        val region = VkBufferImageCopy.calloc(1, stack)
            .bufferOffset(0)
            .bufferRowLength(0)
            .bufferImageHeight(0)
            .imageSubresource {
                it
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1)
            }
            .imageOffset { it.x(0).y(0).z(0) }
            .imageExtent { it.width(width).height(height).depth(1) }
        vkCmdCopyBufferToImage(cmd.vkCommandBuffer, bufferData.buffer, image.vkImage,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region)
    }
}