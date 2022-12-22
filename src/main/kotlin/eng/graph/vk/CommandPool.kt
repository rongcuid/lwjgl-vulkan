package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*
import org.tinylog.kotlin.Logger

class CommandPool(device: Device, queueFamilyIndex: Int) {
    val device: Device
    val vkCommandPool: Long

    init {
        Logger.debug("Create Vulkan CommandPool")
        this.device = device

        MemoryStack.stackPush().use { stack ->
            val cmdPoolInfo = VkCommandPoolCreateInfo.calloc(stack)
                .`sType$Default`()
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                .queueFamilyIndex(queueFamilyIndex)
            val lp = stack.mallocLong(1)
            vkCheck(vkCreateCommandPool(device.vkDevice, cmdPoolInfo, null, lp),
                "Failed to create command pool")
            vkCommandPool = lp[0]
        }
    }

    fun cleanup() {
        vkDestroyCommandPool(device.vkDevice, vkCommandPool, null)
    }
}