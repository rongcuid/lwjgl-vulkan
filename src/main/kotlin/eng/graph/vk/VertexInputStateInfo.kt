package eng.graph.vk
import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.PointerBuffer
import org.lwjgl.system.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*
abstract class VertexInputStateInfo {
    abstract val vi: VkPipelineVertexInputStateCreateInfo

    open fun cleanup() {
        vi.free()
    }
}
class VertexBufferStructure : VertexInputStateInfo() {
    val viAttrs: VkVertexInputAttributeDescription.Buffer
    val viBindings: VkVertexInputBindingDescription.Buffer
    override val vi: VkPipelineVertexInputStateCreateInfo

    init {
        viAttrs = VkVertexInputAttributeDescription.calloc(NUMBER_OF_ATTRIBUTES)
        viBindings = VkVertexInputBindingDescription.calloc(1)
        vi = VkPipelineVertexInputStateCreateInfo.calloc()
        // Position
        viAttrs[0]
            .binding(0)
            .location(0)
            .format(VK_FORMAT_R32G32B32_SFLOAT)
            .offset(0)
        // Texture coordinates
        viAttrs[1]
            .binding(0)
            .location(1)
            .format(VK_FORMAT_R32G32_SFLOAT)
            .offset(POSITION_COMPONENTS * GraphConstants.FLOAT_LENGTH)
        viBindings[0]
            .binding(0)
            .stride((POSITION_COMPONENTS + TEXT_COORD_COMPONENTS) * GraphConstants.FLOAT_LENGTH)
            .inputRate(VK_VERTEX_INPUT_RATE_VERTEX)
        vi
            .`sType$Default`()
            .pVertexBindingDescriptions(viBindings)
            .pVertexAttributeDescriptions(viAttrs)
    }

    override fun cleanup() {
        super.cleanup()
        viBindings.free()
        viAttrs.free()
    }

    companion object {
        const val NUMBER_OF_ATTRIBUTES = 2
        const val POSITION_COMPONENTS = 3
        const val TEXT_COORD_COMPONENTS = 2
    }
}