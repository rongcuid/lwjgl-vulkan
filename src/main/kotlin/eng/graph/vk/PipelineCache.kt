package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.*
import org.lwjgl.system.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*
import org.tinylog.kotlin.Logger

class PipelineCache(device: Device) {
    val device: Device
    val vkPipelineCache: Long

    init {
        Logger.debug("Creating pipeline cache")
        this.device = device
        MemoryStack.stackPush().use {stack ->
            val createInfo = VkPipelineCacheCreateInfo.calloc(stack)
                .`sType$Default`()
            val lp = stack.mallocLong(1)
            vkCheck(vkCreatePipelineCache(device.vkDevice, createInfo, null, lp),
                "Error creating pipeline cache")
            vkPipelineCache = lp[0]
        }
    }

    fun cleanup() {
        Logger.debug("Destroying pipeline cache")
        vkDestroyPipelineCache(device.vkDevice, vkPipelineCache, null)
    }
}