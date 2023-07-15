package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK12.*
class Fence(device: Device, signaled: Boolean) {
    val device: Device
    val vkFence: Long

    init {
        this.device = device
        MemoryStack.stackPush().use { stack ->
            val fenceCreateInfo = VkFenceCreateInfo.calloc(stack)
                .`sType$Default`()
                .flags(if (signaled) VK_FENCE_CREATE_SIGNALED_BIT else 0)
            val lp = stack.mallocLong(1)
            vkCheck(vkCreateFence(device.vkDevice, fenceCreateInfo, null, lp),
                "Failed to create semaphore")
            vkFence = lp[0]
        }
    }

    fun cleanup() {
        vkDestroyFence(device.vkDevice, vkFence, null)
    }

    fun fenceWait() {
        vkWaitForFences(device.vkDevice, vkFence, true, Long.MAX_VALUE)
    }

    fun reset() {
        vkResetFences(device.vkDevice, vkFence)
    }
}