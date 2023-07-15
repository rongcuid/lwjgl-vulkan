package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.VK_QUEUE_GRAPHICS_BIT
import org.lwjgl.vulkan.VK12.*
import org.tinylog.kotlin.Logger


class PhysicalDevice(vkPhysicalDevice: VkPhysicalDevice) {
    private val vkDeviceExtensions: VkExtensionProperties.Buffer
    val vkMemoryProperties: VkPhysicalDeviceMemoryProperties
    val vkPhysicalDevice: VkPhysicalDevice
    val vkPhysicalDeviceFeatures: VkPhysicalDeviceFeatures
    val vkPhysicalDeviceProperties: VkPhysicalDeviceProperties
    val vkQueueFamilyProps: VkQueueFamilyProperties.Buffer

    val deviceName: String get() = vkPhysicalDeviceProperties.deviceNameString()


    init {
        MemoryStack.stackPush().use { stack ->
            this.vkPhysicalDevice = vkPhysicalDevice
            val intBuffer = stack.mallocInt(1)

            // Get device properties
            vkPhysicalDeviceProperties = VkPhysicalDeviceProperties.calloc()
            vkGetPhysicalDeviceProperties(vkPhysicalDevice, vkPhysicalDeviceProperties)

            // Get extensions
            vkCheck(
                vkEnumerateDeviceExtensionProperties(vkPhysicalDevice, null as String?, intBuffer, null),
                "Failed to get number of device extension properties"
            )
            vkDeviceExtensions = VkExtensionProperties.calloc(intBuffer[0])
            vkCheck(
                vkEnumerateDeviceExtensionProperties(vkPhysicalDevice, null as String?, intBuffer, vkDeviceExtensions),
                "Failed to get device extension properties"
            )

            // Get queue family properties
            vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, intBuffer, null)
            vkQueueFamilyProps = VkQueueFamilyProperties.calloc(intBuffer.get(0))
            vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, intBuffer, vkQueueFamilyProps)

            vkPhysicalDeviceFeatures = VkPhysicalDeviceFeatures.calloc()
            vkGetPhysicalDeviceFeatures(vkPhysicalDevice, vkPhysicalDeviceFeatures)

            // Get Memory information and properties
            vkMemoryProperties = VkPhysicalDeviceMemoryProperties.calloc()
            vkGetPhysicalDeviceMemoryProperties(vkPhysicalDevice, vkMemoryProperties)
        }
    }

    fun cleanup() {
        if (Logger.isDebugEnabled()) {
            Logger.debug("Destroying physical device [{}]", vkPhysicalDeviceProperties.deviceNameString())
        }
        vkMemoryProperties.free()
        vkPhysicalDeviceFeatures.free()
        vkQueueFamilyProps.free()
        vkDeviceExtensions.free()
        vkPhysicalDeviceProperties.free()
    }

    private fun hasKHRSwapChainExtension(): Boolean {
        var result = false
        val numExtensions = vkDeviceExtensions.capacity()
        for (i in 0 until numExtensions) {
            val extensionName = vkDeviceExtensions[i].extensionNameString()
            if (KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME == extensionName) {
                result = true
                break
            }
        }
        return result
    }

    private fun hasGraphicsQueueFamily(): Boolean {
        var result = false
        val numQueueFamilies = vkQueueFamilyProps.capacity()
        for (i in 0 until numQueueFamilies) {
            val familyProps = vkQueueFamilyProps[i]
            if (familyProps.queueFlags() and VK_QUEUE_GRAPHICS_BIT != 0) {
                result = true
                break
            }
        }
        return result
    }

    companion object {
        fun createPhysicalDevice(instance: Instance, preferredDeviceName: String?): PhysicalDevice {
            Logger.debug("Selecting physical devices")
            var selectedPhysicalDevice: PhysicalDevice? = null
            MemoryStack.stackPush().use { stack ->
                val pPhysicalDevices = getPhysicalDevices(instance, stack)
                val numDevices = pPhysicalDevices.capacity()
                if (numDevices <= 0) {
                    throw RuntimeException("No physical devices found")
                }
                // Populate available devices
                val devices = ArrayList<PhysicalDevice>()
                for (i in 0 until numDevices) {
                    val vkPhysicalDevice = VkPhysicalDevice(pPhysicalDevices[i], instance.vkInstance)
                    val physicalDevice = PhysicalDevice(vkPhysicalDevice)
                    val deviceName = physicalDevice.deviceName
                    if (physicalDevice.hasGraphicsQueueFamily() && physicalDevice.hasKHRSwapChainExtension()) {
                        Logger.debug("Device [{}] supports required extensions", deviceName)
                        if (preferredDeviceName != null && preferredDeviceName == deviceName) {
                            selectedPhysicalDevice = physicalDevice
                            break
                        }
                        devices.add(physicalDevice)
                    } else {
                        Logger.debug("Device [{}] does not support required extensions", deviceName)
                        physicalDevice.cleanup()
                    }
                }
                // If no preferred device, pick the first one
                selectedPhysicalDevice =
                    if (selectedPhysicalDevice == null && devices.isNotEmpty()) devices.removeAt(0) else selectedPhysicalDevice
                // Clean up non-selected devices
                for (physicalDevice in devices) {
                    physicalDevice.cleanup()
                }
                selectedPhysicalDevice ?: throw RuntimeException("No suitable physical devices found")
                Logger.debug("Selected device: [{}]", selectedPhysicalDevice!!.deviceName)
            }
            return selectedPhysicalDevice!!
        }

        private fun getPhysicalDevices(instance: Instance, stack: MemoryStack): PointerBuffer {
            val pPhysicalDevices: PointerBuffer
            val intBuffer = stack.mallocInt(1)
            vkCheck(
                vkEnumeratePhysicalDevices(instance.vkInstance, intBuffer, null),
                "Failed to get number of physical devices"
            )
            val numDevices = intBuffer[0]
            Logger.debug("Detected {} physical device(s)", numDevices)
            pPhysicalDevices = stack.mallocPointer(numDevices)
            vkCheck(
                vkEnumeratePhysicalDevices(instance.vkInstance, intBuffer, pPhysicalDevices),
                "Failed to get physical devices"
            )
            return pPhysicalDevices
        }
    }
}