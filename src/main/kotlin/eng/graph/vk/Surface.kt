package eng.graph.vk

import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.KHRSurface
import org.tinylog.kotlin.Logger

class Surface(physicalDevice: PhysicalDevice, windowHandle: Long) {
    private val physicalDevice: PhysicalDevice
    val vkSurface: Long

    init {
        Logger.debug("Creating Vulkan surface")
        this.physicalDevice = physicalDevice
        MemoryStack.stackPush().use { stack ->
            val pSurface = stack.mallocLong(1)
            GLFWVulkan.glfwCreateWindowSurface(
                this.physicalDevice.vkPhysicalDevice.instance,
                windowHandle, null, pSurface
            )
            vkSurface = pSurface[0]
        }
    }

    fun cleanup() {
       Logger.debug("Destroying Vulkan surface")
       KHRSurface.vkDestroySurfaceKHR(physicalDevice.vkPhysicalDevice.instance, vkSurface, null)
    }
}