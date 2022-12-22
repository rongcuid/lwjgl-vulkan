package eng.graph.vk

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*
import org.tinylog.kotlin.Logger

open class Queue(device: Device, queueFamilyIndex: Int, queueIndex: Int) {
    val vkQueue: VkQueue

    init {
        Logger.debug("Creating queue")
        MemoryStack.stackPush().use { stack ->
            val pQueue = stack.mallocPointer(1)
            vkGetDeviceQueue(device.vkDevice, queueFamilyIndex, queueIndex, pQueue)
            val queue = pQueue[0]
            vkQueue = VkQueue(queue, device.vkDevice)
        }
    }

    fun waitIdle() {
        vkQueueWaitIdle(vkQueue)
    }

    class GraphicsQueue(device: Device, queueIndex: Int) :
        Queue(device, getGraphicsQueueFamilyIndex(device), queueIndex) {

        companion object {
            private fun getGraphicsQueueFamilyIndex(device: Device): Int {
                var index = -1
                val physicalDevice = device.physicalDevice
                val queuePropsBuf = physicalDevice.vkQueueFamilyProps
                val numQueueFamilies = queuePropsBuf.capacity()
                for (i in 0 until numQueueFamilies) {
                    val props = queuePropsBuf[i]
                    val graphicsQueue = (props.queueFlags() and VK_QUEUE_GRAPHICS_BIT) != 0
                    if (graphicsQueue) {
                        index = i
                        break
                    }
                }
                if (index < 0) {
                    throw RuntimeException("Failed to get graphics queue family index")
                }
                return index
            }
        }
    }
}