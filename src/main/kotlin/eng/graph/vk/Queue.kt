package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*
import org.tinylog.kotlin.Logger
import java.nio.IntBuffer
import java.nio.LongBuffer

open class Queue(device: Device, queueFamilyIndex: Int, queueIndex: Int) {
    val vkQueue: VkQueue
    val queueFamilyIndex: Int

    init {
        Logger.debug("Creating queue")
        this.queueFamilyIndex = queueFamilyIndex
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

    fun submit(commandBuffers: PointerBuffer, waitSemaphores: LongBuffer?, dstStageMasks: IntBuffer,
               signalSemaphores: LongBuffer, fence: Fence?) {
        MemoryStack.stackPush().use { stack ->
            val submitInfo = VkSubmitInfo.calloc(stack)
                .`sType$Default`()
                .pCommandBuffers(commandBuffers)
                .pSignalSemaphores(signalSemaphores)
            if (waitSemaphores != null) {
                submitInfo.waitSemaphoreCount(waitSemaphores.capacity())
                    .pWaitSemaphores(waitSemaphores)
                    .pWaitDstStageMask(dstStageMasks)
            } else {
                submitInfo.waitSemaphoreCount(0)
            }
            val fenceHandle = fence?.vkFence ?: VK_NULL_HANDLE
            vkCheck(vkQueueSubmit(vkQueue, submitInfo, fenceHandle),
                "Failed to submit command to queue")
        }
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

    class PresentQueue(device: Device, surface: Surface, queueIndex: Int) :
        Queue(device, getPresentQueueFamilyIndex(device, surface), queueIndex) {
        companion object {
            fun getPresentQueueFamilyIndex(device: Device, surface: Surface): Int {
                var index = -1
                MemoryStack.stackPush().use { stack ->
                    val physicalDevice = device.physicalDevice
                    val queuePropsBuf = physicalDevice.vkQueueFamilyProps
                    val numQueueFamilies = queuePropsBuf.capacity()
                    val intBuff = stack.mallocInt(1)
                    for (i in 0 until numQueueFamilies) {
                        KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice.vkPhysicalDevice, i,
                            surface.vkSurface, intBuff)
                        val supportsPresentation = intBuff[0] == VK_TRUE
                        if (supportsPresentation) {
                            index = i
                            break
                        }
                    }
                }
                if (index < 0) {
                    throw RuntimeException("Failed to get presentation queue family index")
                }

                return index
            }
        }
    }
}