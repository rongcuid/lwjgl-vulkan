package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*
import org.tinylog.kotlin.Logger

class Device(instance: Instance, physicalDevice: PhysicalDevice) {
    val physicalDevice: PhysicalDevice
    val vkDevice: VkDevice
    val memoryAllocator: MemoryAllocator
    val samplerAnisotropy: Boolean
    init {
        Logger.debug("Creating device")
        this.physicalDevice = physicalDevice
        MemoryStack.stackPush().use { stack ->
            // Required extensions
            val requiredExtensions = stack.mallocPointer(1)
            requiredExtensions.put(0, stack.ASCII(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME))
            // Set up required features
            val features = VkPhysicalDeviceFeatures.calloc(stack)
            val supportedFeatures = physicalDevice.vkPhysicalDeviceFeatures
            samplerAnisotropy = supportedFeatures.samplerAnisotropy()
            features.samplerAnisotropy(samplerAnisotropy)
            // Enable all queue families
            val queuePropsBuff = physicalDevice.vkQueueFamilyProps
            val numQueueFamilies = queuePropsBuff.capacity()
            val queueCreationInfoBuf = VkDeviceQueueCreateInfo.calloc(numQueueFamilies, stack)
            for (i in 0 until numQueueFamilies) {
                val priorities = stack.callocFloat(queuePropsBuff.get(i).queueCount())
                queueCreationInfoBuf[i]
                    .`sType$Default`()
                    .queueFamilyIndex(i)
                    .pQueuePriorities(priorities)
            }
            val deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
                .`sType$Default`()
                .ppEnabledExtensionNames(requiredExtensions)
                .pEnabledFeatures(features)
                .pQueueCreateInfos(queueCreationInfoBuf)
            val pp = stack.mallocPointer(1)
            vkCheck(vkCreateDevice(physicalDevice.vkPhysicalDevice, deviceCreateInfo, null, pp),
                "Failed to create device")
            vkDevice = VkDevice(pp.get(0), physicalDevice.vkPhysicalDevice, deviceCreateInfo)
            memoryAllocator = MemoryAllocator(instance, physicalDevice, vkDevice)
        }
    }

    fun cleanup() {
        Logger.debug("Destroying Vulkan device")
        memoryAllocator.cleanup()
        vkDestroyDevice(vkDevice, null)
    }

    fun waitIdle() {
        vkDeviceWaitIdle(vkDevice)
    }
}