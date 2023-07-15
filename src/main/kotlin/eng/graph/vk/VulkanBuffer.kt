package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.vma.Vma.*
import org.lwjgl.util.vma.VmaAllocationCreateInfo
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK12.*

class VulkanBuffer(device: Device, size: Long, bufferUsage: Int, memoryUsage: Int, requiredFlags: Int) {
    val allocation: Long
    val buffer: Long
    val device: Device
    val pb: PointerBuffer
    val requestedSize: Long

    var mappedMemory: Long
    init {
        this.device = device
        requestedSize = size
        mappedMemory = MemoryUtil.NULL
        MemoryStack.stackPush().use { stack ->
            val bufferCreateInfo = VkBufferCreateInfo.calloc(stack)
                .`sType$Default`()
                .size(size)
                .usage(bufferUsage)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
            val allocInfo = VmaAllocationCreateInfo.calloc(stack)
                .requiredFlags(requiredFlags)
                .usage(memoryUsage)
            val pAllocation = stack.callocPointer(1)
            val lp = stack.mallocLong(1)
            vkCheck(vmaCreateBuffer(device.memoryAllocator.vmaAllocator, bufferCreateInfo, allocInfo, lp,
                pAllocation, null),
                "Failed to create buffer")
            buffer = lp[0]
            allocation = pAllocation[0]
            pb = MemoryUtil.memAllocPointer(1)
        }
    }
    fun cleanup() {
        MemoryUtil.memFree(pb)
        unmap()
        vmaDestroyBuffer(device.memoryAllocator.vmaAllocator, buffer, allocation)
    }

    fun map(): Long {
        if (mappedMemory == MemoryUtil.NULL) {
            vkCheck(vmaMapMemory(device.memoryAllocator.vmaAllocator, allocation, pb),
                "Failed to map buffer")
            mappedMemory = pb[0]
        }
        return mappedMemory
    }
    fun unmap() {
        if (mappedMemory != MemoryUtil.NULL) {
            vmaUnmapMemory(device.memoryAllocator.vmaAllocator, allocation)
            mappedMemory = MemoryUtil.NULL
        }
    }
}