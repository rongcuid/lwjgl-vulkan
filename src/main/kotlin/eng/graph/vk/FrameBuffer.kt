package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*
import java.nio.LongBuffer

class FrameBuffer(device: Device, width: Int, height: Int, pAttachments: LongBuffer, renderPass: Long, layers: Int) {
    val device: Device
    val vkFrameBuffer: Long

    init {
        this.device = device
        MemoryStack.stackPush().use { stack ->
            val fci = VkFramebufferCreateInfo.calloc(stack)
                .`sType$Default`()
                .pAttachments(pAttachments)
                .width(width)
                .height(height)
                .layers(layers)
                .renderPass(renderPass)
            val lp = stack.mallocLong(1)
            vkCheck(vkCreateFramebuffer(device.vkDevice, fci, null, lp),
                "Failed to create FrameBuffer")
            vkFrameBuffer = lp[0]
        }
    }

    fun cleanup() {
        vkDestroyFramebuffer(device.vkDevice, vkFrameBuffer, null)
    }
}