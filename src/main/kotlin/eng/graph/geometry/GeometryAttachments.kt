package eng.graph.geometry

import eng.graph.vk.Attachment
import eng.graph.vk.Device
import org.lwjgl.vulkan.VK13.*

class GeometryAttachments(device: Device, width: Int, height: Int) {
    val attachments: List<Attachment>
    val depthAttachment: Attachment
    val height: Int
    val width: Int

    init {
        this.width = width
        this.height = height
        attachments = ArrayList()
        // Albedo attachment
        attachments.add(Attachment(device, width, height,
            VK_FORMAT_R16G16B16A16_SFLOAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT))
        // Normals attachment
        attachments.add(Attachment(device, width, height,
            VK_FORMAT_A2B10G10R10_UNORM_PACK32, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT))
        // PBR attachment
        attachments.add(Attachment(device, width, height,
            VK_FORMAT_R16G16B16A16_SFLOAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT))
        // Depth attachment
        depthAttachment = Attachment(device, width, height,
            VK_FORMAT_D32_SFLOAT, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
        attachments.add(depthAttachment)
    }

    fun cleanup() {
        attachments.forEach(Attachment::cleanup)
    }

    companion object {
        val NUMBER_ATTACHMENTS: Int = 4
        val NUMBER_COLOR_ATTACHMENTS = NUMBER_ATTACHMENTS - 1
    }
}