package eng.graph.lighting

import eng.EngineProperties
import eng.graph.vk.GraphConstants
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import java.nio.ByteBuffer


class LightSpecConstants {
    private val data: ByteBuffer = MemoryUtil.memAlloc(GraphConstants.INT_LENGTH * 4 + GraphConstants.FLOAT_LENGTH)

    private val specEntryMap: VkSpecializationMapEntry.Buffer
    val specInfo: VkSpecializationInfo

    init {
        val engineProperties = EngineProperties.instance

        data.putInt(GraphConstants.MAX_LIGHTS)
        data.putInt(GraphConstants.SHADOW_MAP_CASCADE_COUNT)
        data.putInt(if (engineProperties.shadowPcf) 1 else 0)
        data.putFloat(engineProperties.shadowBias)
        data.putInt(if (engineProperties.shadowDebug) 1 else 0)
        data.flip()

        specEntryMap = VkSpecializationMapEntry.calloc(5)
        specEntryMap[0]
            .constantID(0)
            .size(GraphConstants.INT_LENGTH.toLong())
            .offset(0)
        specEntryMap.get(1)
            .constantID(1)
            .size(GraphConstants.INT_LENGTH.toLong())
            .offset(GraphConstants.INT_LENGTH);
        specEntryMap.get(2)
            .constantID(2)
            .size(GraphConstants.INT_LENGTH.toLong())
            .offset(GraphConstants.INT_LENGTH * 2);
        specEntryMap.get(3)
            .constantID(3)
            .size(GraphConstants.FLOAT_LENGTH.toLong())
            .offset(GraphConstants.INT_LENGTH * 3);
        specEntryMap.get(4)
            .constantID(4)
            .size(GraphConstants.INT_LENGTH.toLong())
            .offset(GraphConstants.INT_LENGTH * 3 + GraphConstants.FLOAT_LENGTH);

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