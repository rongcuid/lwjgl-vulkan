package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.system.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK12.*
import org.tinylog.kotlin.Logger

class TextureSampler(val device: Device, mipLevels: Int) {
    val vkSampler: Long
    init {
        MemoryStack.stackPush().use { stack ->
            val samplerInfo = VkSamplerCreateInfo.calloc(stack)
                .`sType$Default`()
                .magFilter(VK_FILTER_LINEAR)
                .minFilter(VK_FILTER_LINEAR)
                .addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                .addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                .addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                .unnormalizedCoordinates(false)
                .compareEnable(false)
                .compareOp(VK_COMPARE_OP_ALWAYS)
                .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
                .minLod(0f)
                .maxLod(mipLevels.toFloat())
                .mipLodBias(0f)
            if (device.samplerAnisotropy) {
                samplerInfo
                    .anisotropyEnable(true)
                    .maxAnisotropy(MAX_ANISOTROPY)
            }
            val lp = stack.mallocLong(1)
            vkCheck(vkCreateSampler(device.vkDevice, samplerInfo, null, lp),
                "Failed to create sampler")
            vkSampler = lp[0]
        }
    }
    fun cleanup() {
        vkDestroySampler(device.vkDevice, vkSampler, null)
    }
    companion object {
        private val MAX_ANISOTROPY = 16f
    }
}