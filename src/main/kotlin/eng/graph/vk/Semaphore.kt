package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*

class Semaphore(device: Device) {
    val device: Device
    val vkSemaphore: Long

    init {
        this.device = device
        MemoryStack.stackPush().use { stack ->
            val semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc(stack)
                .`sType$Default`()
            val lp = stack.mallocLong(1)
            vkCheck(VK10.vkCreateSemaphore(device.vkDevice, semaphoreCreateInfo, null, lp),
                "Failed to create semaphore")
            vkSemaphore = lp.get(0)
        }
    }

    fun cleanup() {
        vkDestroySemaphore(device.vkDevice, vkSemaphore, null)
    }
}