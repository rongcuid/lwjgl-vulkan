package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK12.vkCreateShaderModule
import org.lwjgl.vulkan.VK12.vkDestroyShaderModule
import org.lwjgl.vulkan.VkShaderModuleCreateInfo
import org.lwjgl.vulkan.VkSpecializationInfo
import org.tinylog.kotlin.Logger
import java.io.File
import java.io.IOException
import java.nio.file.Files

class ShaderProgram(device: Device, shaderModuleData: Array<ShaderModuleData>?) {
    val device: Device
    val shaderModules: Array<ShaderModule>
    init {
        try {
            this.device = device
            val numModules = shaderModuleData?.size ?: 0
            shaderModules = Array(numModules) {
                val moduleContents = Files.readAllBytes(File(shaderModuleData!![it].shaderSpvFile).toPath())
                val moduleHandle = createShaderModule(moduleContents)
                ShaderModule(shaderModuleData[it].shaderStage, moduleHandle, shaderModuleData[it].specInfo)
            }
        } catch (e: IOException) {
            Logger.error("Error reading shader files", e)
            throw RuntimeException(e)
        }
    }
    fun cleanup() {
        for (shaderModule in shaderModules) {
            vkDestroyShaderModule(device.vkDevice, shaderModule.handle, null)
        }
    }

    private fun createShaderModule(code: ByteArray): Long {
        MemoryStack.stackPush().use { stack ->
            val pCode = stack.malloc(code.size).put(0, code)
            val moduleCreateInfo = VkShaderModuleCreateInfo.calloc(stack)
                .`sType$Default`()
                .pCode(pCode)
            val lp = stack.mallocLong(1)
            vkCheck(vkCreateShaderModule(device.vkDevice, moduleCreateInfo, null, lp),
                "Failed to create shader module")
            return lp[0]
        }
    }

    data class ShaderModule(val shaderStage: Int, val handle: Long, val specInfo: VkSpecializationInfo?)
    data class ShaderModuleData(val shaderStage: Int, val shaderSpvFile: String, val specInfo: VkSpecializationInfo?=null)
}