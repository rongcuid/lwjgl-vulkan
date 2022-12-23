package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.memoryTypeFromProperties
import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*

class VulkanBuffer(device: Device, size: Long, usage: Int, reqMask: Int) {
    val device: Device
    val requestedSize: Long
    val allocationSize: Long
    var mappedMemory: Long
    val buffer: Long
    val memory: Long
    val pb: PointerBuffer

    init {
        this.device = device
        requestedSize = size
        mappedMemory = MemoryUtil.NULL
        MemoryStack.stackPush().use { stack ->
            val bufferCreateInfo = VkBufferCreateInfo.calloc(stack)
                .`sType$Default`()
                .size(size)
                .usage(usage)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
            val lp = stack.mallocLong(1)
            vkCheck(vkCreateBuffer(device.vkDevice, bufferCreateInfo, null, lp),
                "Failed to create buffer")
            buffer = lp[0]
            var memReqs = VkMemoryRequirements.malloc(stack)
            vkGetBufferMemoryRequirements(device.vkDevice, buffer, memReqs)
            val memAlloc = VkMemoryAllocateInfo.calloc(stack)
                .`sType$Default`()
                .allocationSize(memReqs.size())
                .memoryTypeIndex(memoryTypeFromProperties(device.physicalDevice,
                    memReqs.memoryTypeBits(), reqMask))
            vkCheck(vkAllocateMemory(device.vkDevice, memAlloc, null, lp),
                "Failed to allocate memory")
            allocationSize = memAlloc.allocationSize()
            memory = lp[0]
            pb = MemoryUtil.memAllocPointer(1)
            vkCheck(vkBindBufferMemory(device.vkDevice, buffer, memory, 0),
                "Failed to bind buffer memory")
        }
    }
    fun cleanup() {
        MemoryUtil.memFree(pb)
        vkDestroyBuffer(device.vkDevice, buffer, null)
        vkFreeMemory(device.vkDevice, memory, null)
    }

    fun map(): Long {
        if (mappedMemory == MemoryUtil.NULL) {
            vkCheck(vkMapMemory(device.vkDevice, memory, 0, allocationSize, 0, pb),
                "Failed to map buffer")
            mappedMemory = pb[0]
        }
        return mappedMemory
    }
    fun unmap() {
        if (mappedMemory != MemoryUtil.NULL) {
            vkUnmapMemory(device.vkDevice, memory)
            mappedMemory = MemoryUtil.NULL
        }
    }
}