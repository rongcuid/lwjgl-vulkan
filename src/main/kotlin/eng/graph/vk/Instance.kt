package eng.graph.vk

import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugUtils.*
import org.lwjgl.vulkan.VK13.*
import org.tinylog.kotlin.Logger
import eng.graph.vk.VulkanUtils.Companion.vkCheck

class Instance(val validate: Boolean) {
    val vkInstance: VkInstance
    private val debugUtils: VkDebugUtilsMessengerCreateInfoEXT?
    private val vkDebugHandle: Long

    init {
        Logger.debug("Creating Vulkan instance")
        MemoryStack.stackPush().use { stack ->
            val appShortName = stack.UTF8("VulkanBook")
            val appInfo = VkApplicationInfo.calloc(stack)
                .`sType$Default`()
                .pApplicationName(appShortName)
                .applicationVersion(1)
                .pEngineName(appShortName)
                .engineVersion(0)
                .apiVersion(VK_API_VERSION_1_3)
            val validationLayers = getSupportedValidationLayers()
            val numValidationLayers = validationLayers.size
            var supportsValidation = validate
            if (validate && numValidationLayers == 0) {
                supportsValidation = false
                Logger.warn("Request validation but no supported validation layers found. Falling back to no validation")
                Logger.debug("Validation: {}", supportsValidation)
            }
            // Set required layers
            var requiredLayers: PointerBuffer? = null
            if (supportsValidation) {
                requiredLayers = stack.mallocPointer(numValidationLayers)
                for (i in 0 until numValidationLayers) {
                    Logger.debug("Using a validation layer [{}]", validationLayers[i])
                    requiredLayers!!.put(i, stack.ASCII(validationLayers[i]))
                }
            }
            // GLFW extensions
            val glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions()
                ?: throw RuntimeException("Failed to find the GLFW platform surface extensions")
            val requiredExtensions: PointerBuffer
            if (supportsValidation) {
                val vkDebugUtilsExtension = stack.UTF8(VK_EXT_DEBUG_UTILS_EXTENSION_NAME)
                requiredExtensions = stack.mallocPointer(glfwExtensions.remaining() + 1)
                requiredExtensions.put(glfwExtensions).put(vkDebugUtilsExtension)
            } else {
                requiredExtensions = stack.mallocPointer(glfwExtensions.remaining() + 1)
                requiredExtensions.put(glfwExtensions)
            }
            requiredExtensions.flip()
            // Setup debug callback
            var extension = MemoryUtil.NULL
            if (supportsValidation) {
                debugUtils = createDebugCallback()
                extension = debugUtils.address()
            } else {
                debugUtils = null
            }
            // Create instance
            val instanceInfo = VkInstanceCreateInfo.calloc(stack)
                .`sType$Default`()
                .pNext(extension)
                .pApplicationInfo(appInfo)
                .ppEnabledLayerNames(requiredLayers)
                .ppEnabledExtensionNames(requiredExtensions)
            val pInstance = stack.mallocPointer(1)
            vkCheck(vkCreateInstance(instanceInfo, null, pInstance), "Error creating instance")
            vkInstance = VkInstance(pInstance[0], instanceInfo)
            // Instantiate debug extension
            vkDebugHandle = if (supportsValidation) {
                val longBuff = stack.mallocLong(1)
                vkCheck(vkCreateDebugUtilsMessengerEXT(vkInstance, debugUtils!!, null, longBuff), "Error creating debug utils")
                longBuff.get(0)
            } else {
                VK_NULL_HANDLE
            }
        }
    }

    private fun createDebugCallback(): VkDebugUtilsMessengerCreateInfoEXT {
        val result = VkDebugUtilsMessengerCreateInfoEXT
            .calloc()
            .`sType$Default`()
            .messageSeverity(MESSAGE_SEVERITY_BITMASK)
            .messageType(MESSAGE_TYPE_BITMASK)
            .pfnUserCallback { messageSeverity, messageTypes, pCallbackData, pUserData ->
                val callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData)
                if ((messageSeverity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0) {
                    Logger.info("VkDebugUtilsCallback, {}", callbackData.pMessageString())
                } else if ((messageSeverity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0) {
                    Logger.warn("VkDebugUtilsCallback, {}", callbackData.pMessageString())
                } else if ((messageSeverity and VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) {
                    Logger.error("VkDebugUtilsCallback, {}", callbackData.pMessageString())
                } else {
                    Logger.debug("VkDebugUtilsCallback, {}", callbackData.pMessageString())
                }
                VK_FALSE
            }
        return result
    }

    private fun getSupportedValidationLayers(): List<String> {
        MemoryStack.stackPush().use { stack ->
            // Query number of layers
            val numLayersArr = stack.callocInt(1)
            vkEnumerateInstanceLayerProperties(numLayersArr, null)
            val numLayers = numLayersArr.get(0)
            Logger.debug("Instance supports [{}] layers", numLayers)
            // Then query the properties
            val propsBuf = VkLayerProperties.calloc(numLayers, stack)
            val supportedLayers = ArrayList<String>()
            vkEnumerateInstanceLayerProperties(numLayersArr, propsBuf)
            for (i in 0 until numLayers) {
                val props = propsBuf.get(i)
                val layerName = props.layerNameString()
                supportedLayers.add(layerName)
                Logger.debug("Supported layer [{}]", layerName)
            }
            // Select layers
            val layersToUse = ArrayList<String>()
            // Main layer
            if (supportedLayers.contains("VK_LAYER_KHRONOS_validation")) {
                layersToUse.add("VK_LAYER_KHRONOS_validation")
                return layersToUse
            }
            // Fallback 1
            if (supportedLayers.contains("VK_LAYER_LUNARG_standard_validation")) {
                layersToUse.add("VK_LAYER_LUNARG_standard_validation")
                return layersToUse
            }
            // Fallback 2
            val requestedLayers = ArrayList<String>()
            requestedLayers.add("VK_LAYER_GOOGLE_threading")
            requestedLayers.add("VK_LAYER_LUNARG_parameter_validation")
            requestedLayers.add("VK_LAYER_LUNARG_object_tracker")
            requestedLayers.add("VK_LAYER_LUNARG_core_validation")
            requestedLayers.add("VK_LAYER_GOOGLE_unique_objects")
            val overlap = requestedLayers.stream().filter(supportedLayers::contains).toList()
            return overlap
        }
    }

    fun cleanup() {
        Logger.debug("Destroying Vulkan instance")
        if (vkDebugHandle != VK_NULL_HANDLE) {
            vkDestroyDebugUtilsMessengerEXT(vkInstance, vkDebugHandle, null)
        }
        if (debugUtils != null) {
            debugUtils.pfnUserCallback().free()
            debugUtils.free()
        }
        vkDestroyInstance(vkInstance, null)
    }

    companion object {
        const val MESSAGE_SEVERITY_BITMASK = VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT or
                VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT
        const val MESSAGE_TYPE_BITMASK = VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT or
                VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT or
                VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT
    }
}