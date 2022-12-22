package eng.graph.vk

import org.lwjgl.vulkan.VK13.VK_SUCCESS

class VulkanUtils {
    companion object {
        fun vkCheck(err: Int, errMsg: String) {
            if (err != VK_SUCCESS) {
                throw RuntimeException("$errMsg: $err")
            }
        }
    }
}