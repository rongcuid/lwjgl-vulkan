package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*
import org.tinylog.kotlin.Logger

class CommandBuffer(commandPool: CommandPool, primary: Boolean, oneTimeSubmit: Boolean) {
    val commandPool: CommandPool
    val oneTimeSubmit: Boolean
    val vkCommandBuffer: VkCommandBuffer

    init {
        Logger.trace("Creating command buffer")
        this.commandPool = commandPool
        this.oneTimeSubmit = oneTimeSubmit
        val vkDevice = commandPool.device.vkDevice

        MemoryStack.stackPush().use { stack ->
            val cmdBufAllocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .`sType$Default`()
                .commandPool(commandPool.vkCommandPool)
                .level(if (primary) VK_COMMAND_BUFFER_LEVEL_PRIMARY else VK_COMMAND_BUFFER_LEVEL_SECONDARY)
                .commandBufferCount(1)
            val pb = stack.mallocPointer(1)
            vkCheck(vkAllocateCommandBuffers(vkDevice, cmdBufAllocateInfo, pb),
                "Failed to allocate render command buffer")
            vkCommandBuffer = VkCommandBuffer(pb[0], vkDevice)
        }
    }

    fun beginRecording() {
        MemoryStack.stackPush().use { stack ->
            val cmdBufInfo = VkCommandBufferBeginInfo.calloc(stack)
                .`sType$Default`()
            if (oneTimeSubmit) {
                cmdBufInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)
            }
            vkCheck(vkBeginCommandBuffer(vkCommandBuffer, cmdBufInfo),
                "Failed to begin command buffer")
        }
    }

    fun endRecording() {
        vkCheck(vkEndCommandBuffer(vkCommandBuffer), "Failed to end command buffer")
    }

    fun cleanup() {
        Logger.trace("Destroying command buffer")
        vkFreeCommandBuffers(commandPool.device.vkDevice, commandPool.vkCommandPool, vkCommandBuffer)
    }

    fun reset() {
        vkResetCommandBuffer(vkCommandBuffer, VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT)
    }
}