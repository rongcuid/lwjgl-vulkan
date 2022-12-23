package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*
class ModelData(modelId: String, meshDataList: List<MeshData>) {
    val meshDataList: List<MeshData>
    val modelId: String

    init {
        this.modelId = modelId
        this.meshDataList = meshDataList
    }

    data class MeshData(val positions: FloatArray, val textCoords: FloatArray?, val indices: IntArray)
}