package eng.graph.lighting

import eng.graph.vk.GraphConstants
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import java.nio.ByteBuffer

class LightSpecConstants {
    private val data: ByteBuffer

    private val specEntryMap: VkSpecializationMapEntry.Buffer
    val specInfo: VkSpecializationInfo

    init {
        data = MemoryUtil.memAlloc(GraphConstants.INT_LENGTH)
        data.putInt(GraphConstants.MAX_LIGHTS)
        data.flip()

        specEntryMap = VkSpecializationMapEntry.calloc(1)
        specEntryMap[0]
            .constantID(0)
            .size(GraphConstants.INT_LENGTH.toLong())
            .offset(0)

        specInfo = VkSpecializationInfo.calloc()
        specInfo.pData(data)
            .pMapEntries(specEntryMap)
    }

    fun cleanup() {
        MemoryUtil.memFree(specEntryMap)
        specInfo.free()
        MemoryUtil.memFree(data)
    }
}