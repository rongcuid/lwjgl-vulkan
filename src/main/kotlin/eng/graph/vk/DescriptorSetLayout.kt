package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.system.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*
import org.tinylog.kotlin.Logger

abstract class DescriptorSetLayout(private val device: Device) {
    abstract val vkDescriptorLayout: Long

    fun cleanup() {
        Logger.debug("Destroying descriptor set layout")
        vkDestroyDescriptorSetLayout(device.vkDevice, vkDescriptorLayout, null)
    }

    open class SimpleDescriptorSetLayout(device: Device, descriptorType: Int, binding: Int, stage: Int) :
        DescriptorSetLayout(device) {
        override val vkDescriptorLayout: Long

        init {
            MemoryStack.stackPush().use { stack ->
                val layoutBindings = VkDescriptorSetLayoutBinding.calloc(1, stack)
                layoutBindings[0]
                    .binding(binding)
                    .descriptorType(descriptorType)
                    .descriptorCount(1)
                    .stageFlags(stage)
                val layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .`sType$Default`()
                    .pBindings(layoutBindings)
                val pSetLayout = stack.mallocLong(1)
                vkCheck(
                    vkCreateDescriptorSetLayout(device.vkDevice, layoutInfo, null, pSetLayout),
                    "Failed to create descriptor set layout"
                )
                vkDescriptorLayout = pSetLayout[0]
            }
        }
    }

    class SamplerDescriptorSetLayout(device: Device, binding: Int, stage: Int) :
        SimpleDescriptorSetLayout(device, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, binding, stage)
    class UniformDescriptorSetLayout(device: Device, binding: Int, stage: Int) :
            SimpleDescriptorSetLayout(device, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, binding, stage)
    class DynUniformDescriptorSetLayout(device: Device, binding: Int, stage: Int) :
            SimpleDescriptorSetLayout(device, binding, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, stage)
}