package eng.graph.vk

import eng.EngineProperties
import eng.graph.vk.VulkanUtils.Companion.vkCheck
import eng.scene.Scene
import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*
import java.nio.ByteBuffer

class ForwardRenderActivity(
    swapChain: SwapChain, commandPool: CommandPool,
    pipelineCache: PipelineCache,
    scene: Scene
) {
    var swapChain: SwapChain
    val renderPass: SwapChainRenderPass
    var frameBuffers: Array<FrameBuffer>
    val commandBuffers: Array<CommandBuffer>
    val fences: Array<Fence>
    val fwdShaderProgram: ShaderProgram
    val pipeline: Pipeline
    val pipelineCache: PipelineCache
    val device: Device
    var depthAttachments: Array<Attachment>
    val scene: Scene

    init {
        this.swapChain = swapChain
        this.pipelineCache = pipelineCache
        this.device = swapChain.device
        this.scene = scene
        MemoryStack.stackPush().use { stack ->
            val device = swapChain.device
            val swapChainExtent = swapChain.swapChainExtent
            val imageViews = swapChain.imageViews
            val numImages = imageViews.size
            depthAttachments = createDepthImages()
            renderPass = SwapChainRenderPass(swapChain, depthAttachments[0].image.format)
            frameBuffers = createFrameBuffers()
            val engineProperties = EngineProperties.instance
            if (engineProperties.shaderRecompilation) {
                ShaderCompiler.compileShaderIfChanged(VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader)
                ShaderCompiler.compileShaderIfChanged(FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader)
            }
            fwdShaderProgram = ShaderProgram(
                device, arrayOf(
                    ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, VERTEX_SHADER_FILE_SPV),
                    ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, FRAGMENT_SHADER_FILE_SPV)
                )
            )
            val pipelineCreationInfo = Pipeline.PipelineCreationInfo(
                renderPass.vkRenderPass, fwdShaderProgram,
                1, true, GraphConstants.MAT4X4_SIZE * 2,
                VertexBufferStructure()
            )
            pipeline = Pipeline(pipelineCache, pipelineCreationInfo)
            pipelineCreationInfo.cleanup()

            commandBuffers = Array(numImages) { CommandBuffer(commandPool, true, false) }
            fences = Array(numImages) { Fence(device, true) }
        }
    }

    private fun createFrameBuffers(): Array<FrameBuffer> {
        MemoryStack.stackPush().use { stack ->
            val swapChainExtent = swapChain.swapChainExtent
            val imageViews = swapChain.imageViews
            val numImages = swapChain.numImages
            val pAttachments = stack.mallocLong(2)
            return Array(numImages) {
                pAttachments.put(0, imageViews[it].vkImageView)
                pAttachments.put(1, depthAttachments[it].imageView.vkImageView)
                FrameBuffer(
                    device, swapChainExtent.width(), swapChainExtent.height(),
                    pAttachments, renderPass.vkRenderPass
                )
            }
        }
    }

    private fun createDepthImages(): Array<Attachment> {
        val numImages = swapChain.numImages
        val swapChainExtent = swapChain.swapChainExtent
        return Array(numImages) {
            Attachment(
                device, swapChainExtent.width(), swapChainExtent.height(),
                VK_FORMAT_D32_SFLOAT, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT
            )
        }
    }

    fun cleanup() {
        depthAttachments.forEach(Attachment::cleanup)
        frameBuffers.forEach(FrameBuffer::cleanup)
        renderPass.cleanup()
        commandBuffers.forEach(CommandBuffer::cleanup)
        fences.forEach(Fence::cleanup)
    }

    fun recordCommandBuffer(vulkanModelList: List<VulkanModel>) {
        MemoryStack.stackPush().use { stack ->
            val swapChainExtent = swapChain.swapChainExtent
            val width = swapChainExtent.width()
            val height = swapChainExtent.height()
            val idx = swapChain.currentFrame

            val fence = fences[idx]
            val commandBuffer = commandBuffers[idx]
            val frameBuffer = frameBuffers[idx]

            fence.fenceWait()
            fence.reset()

            commandBuffer.reset()
            val clearValues = VkClearValue.calloc(2, stack)
            clearValues.apply(0) { v ->
                v.color().float32(0, 0.5f).float32(1, 0.7f).float32(2, 0.9f).float32(3, 1f)
            }
            clearValues.apply(1) {
                it.depthStencil().depth(1.0f)
            }
            val renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                .`sType$Default`()
                .renderPass(renderPass.vkRenderPass)
                .pClearValues(clearValues)
                .renderArea { a -> a.extent().set(width, height) }
                .framebuffer(frameBuffer.vkFrameBuffer)

            commandBuffer.beginRecording()
            val cmdHandle = commandBuffer.vkCommandBuffer
            vkCmdBeginRenderPass(cmdHandle, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE)
            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.vkPipeline)

            val viewport = VkViewport.calloc(1, stack)
                .x(0f)
                .y(height.toFloat())
                .height(-height.toFloat())
                .width(width.toFloat())
                .minDepth(0f)
                .maxDepth(1f)
            vkCmdSetViewport(cmdHandle, 0, viewport)

            val scissor = VkRect2D.calloc(1, stack)
                .extent { it.width(width).height(height) }
                .offset { it.x(0).y(0) }
            vkCmdSetScissor(cmdHandle, 0, scissor)

            val offsets = stack.mallocLong(1)
            offsets.put(0, 0)
            val vertexBuffer = stack.mallocLong(1)
            val pushConstantBuffer = stack.malloc(GraphConstants.MAT4X4_SIZE * 2)
            for (vulkanModel in vulkanModelList) {
                val modelId = vulkanModel.modelId
                val entities = scene.getEntitiesByModelId(modelId)
                if (entities.isNullOrEmpty()) {
                    continue
                }
                for (mesh in vulkanModel.vulkanMeshList) {
                    vertexBuffer.put(0, mesh.verticesBuffer.buffer)
                    vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets)
                    vkCmdBindIndexBuffer(cmdHandle, mesh.indicesBuffer.buffer, 0, VK_INDEX_TYPE_UINT32)
                    for (e in entities) {
                        setPushConstants(
                            cmdHandle,
                            scene.projection.projectionMatrix,
                            e.modelMatrix,
                            pushConstantBuffer
                        )
                        vkCmdDrawIndexed(cmdHandle, mesh.numIndices, 1, 0, 0, 0)
                    }
                }
            }
            vkCmdEndRenderPass(cmdHandle)
            commandBuffer.endRecording()
        }
    }

    private fun setPushConstants(
        cmdHandle: VkCommandBuffer,
        projMatrix: Matrix4f,
        modelMatrix: Matrix4f,
        pushConstantBuffer: ByteBuffer
    ) {
        projMatrix.get(pushConstantBuffer)
        modelMatrix.get(GraphConstants.MAT4X4_SIZE, pushConstantBuffer)
        vkCmdPushConstants(
            cmdHandle, pipeline.vkPipelineLayout,
            VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstantBuffer
        )
    }

    fun submit(queue: Queue) {
        MemoryStack.stackPush().use { stack ->
            val idx = swapChain.currentFrame
            val commandBuffer = commandBuffers[idx]
            val currentFence = fences[idx]
            val syncSemaphores = swapChain.syncSemaphoresList[idx]
            queue.submit(
                stack.pointers(commandBuffer.vkCommandBuffer),
                stack.longs(syncSemaphores.imgAcquisitionSemaphore.vkSemaphore),
                stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT),
                stack.longs(syncSemaphores.renderCompleteSemaphore.vkSemaphore),
                currentFence
            )
        }
    }

    fun resize(swapChain: SwapChain) {
        this.swapChain = swapChain
        frameBuffers.forEach(FrameBuffer::cleanup)
        depthAttachments.forEach(Attachment::cleanup)
        depthAttachments = createDepthImages()
        frameBuffers = createFrameBuffers()
    }

    companion object {
        private val FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/fwd_fragment.glsl"
        private val FRAGMENT_SHADER_FILE_SPV = "$FRAGMENT_SHADER_FILE_GLSL.spv"
        private val VERTEX_SHADER_FILE_GLSL = "resources/shaders/fwd_vertex.glsl"
        private val VERTEX_SHADER_FILE_SPV = "$VERTEX_SHADER_FILE_GLSL.spv"
    }
}