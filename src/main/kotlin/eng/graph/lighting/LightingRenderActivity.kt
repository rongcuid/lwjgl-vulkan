package eng.graph.lighting

import eng.EngineProperties
import eng.graph.vk.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.VK13.*
import org.lwjgl.vulkan.VkClearValue
import org.lwjgl.vulkan.VkRect2D
import org.lwjgl.vulkan.VkRenderPassBeginInfo
import org.lwjgl.vulkan.VkViewport

class LightingRenderActivity(
    var swapChain: SwapChain, commandPool: CommandPool, val pipelineCache: PipelineCache,
    attachments: List<Attachment>
) {
    private lateinit var pipeline: Pipeline
    val device: Device = swapChain.device
    val lightingFrameBuffer = LightingFrameBuffer(swapChain)

    private lateinit var commandBuffers: Array<CommandBuffer>
    private lateinit var shaderProgram: ShaderProgram
    private lateinit var fences: Array<Fence>
    private lateinit var descriptorPool: DescriptorPool
    private lateinit var descriptorSetLayout: Array<DescriptorSetLayout>
    private lateinit var attachmentsLayout: AttachmentsLayout
    private lateinit var attachmentsDescriptorSet: AttachmentsDescriptorSet

    init {
        val numImages = swapChain.numImages
        createShaders()
        createDescriptorPool(attachments)
        createDescriptorSets(attachments)
        createPipeline(pipelineCache)
        createCommandBuffers(commandPool, numImages)
        for (i in 0 until numImages) {
            preRecordCommandBuffer(i)
        }
    }

    private fun preRecordCommandBuffer(idx: Int) {
        MemoryStack.stackPush().use { stack ->
            val swapChainExtent = swapChain.swapChainExtent
            val width = swapChainExtent.width()
            val height = swapChainExtent.height()

            val frameBuffer = lightingFrameBuffer.frameBuffers[idx]
            val commandBuffer = commandBuffers[idx]

            commandBuffer.reset()
            val clearValues = VkClearValue.calloc(1, stack)
            clearValues.apply(0) {
                it.color()
                    .float32(0, 0f).float32(1, 0f).float32(2, 0f).float32(3, 1f)
            }
            val renderArea = VkRect2D.calloc(stack)
            renderArea.offset().set(0,0)
            renderArea.extent().set(width, height)

            val renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                .`sType$Default`()
                .renderPass(lightingFrameBuffer.lightingRenderPass.vkRenderPass)
                .pClearValues(clearValues)
                .framebuffer(frameBuffer.vkFrameBuffer)
                .renderArea(renderArea)

            commandBuffer.beginRecording()
            val cmdHandle = commandBuffer.vkCommandBuffer
            vkCmdBeginRenderPass(cmdHandle, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE)

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.vkPipeline)

            val viewport = VkViewport.calloc(1, stack)
                .x(0f).y(height.toFloat())
                .height(-height.toFloat()).width(width.toFloat())
                .minDepth(0f).maxDepth(1f)
            vkCmdSetViewport(cmdHandle, 0, viewport)

            val scissor = VkRect2D.calloc(1, stack)
                .extent{it.width(width).height(height)}
                .offset{it.x(0).y(0)}
            vkCmdSetScissor(cmdHandle, 0, scissor)

            val descriptorSets = stack.mallocLong(1)
                .put(0, attachmentsDescriptorSet.vkDescriptorSet)
            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                pipeline.vkPipelineLayout, 0, descriptorSets, null)
            vkCmdDraw(cmdHandle, 3, 1, 0, 0)
            vkCmdEndRenderPass(cmdHandle)
            commandBuffer.endRecording()
        }
    }

    private fun createCommandBuffers(commandPool: CommandPool, numImages: Int) {
        commandBuffers = Array(numImages) { CommandBuffer(commandPool, true, false) }
        fences = Array(numImages) { Fence(device, true) }
    }

    private fun createPipeline(pipelineCache: PipelineCache) {
        val pipelineCreationInfo = Pipeline.PipelineCreationInfo(
            lightingFrameBuffer.lightingRenderPass.vkRenderPass, shaderProgram, 1, false, false, 0,
            EmptyVertexBufferStructure(), descriptorSetLayout
        )
        pipeline = Pipeline(pipelineCache, pipelineCreationInfo)
        pipelineCreationInfo.cleanup()
    }

    private fun createDescriptorSets(attachments: List<Attachment>) {
        attachmentsLayout = AttachmentsLayout(device, attachments.size)
        descriptorSetLayout = arrayOf(
            attachmentsLayout
        )
        attachmentsDescriptorSet = AttachmentsDescriptorSet(descriptorPool, attachmentsLayout, attachments, 0)
    }

    private fun createDescriptorPool(attachments: List<Attachment>) {
        val descriptorTypeCounts = ArrayList<DescriptorPool.DescriptorTypeCount>()
        descriptorTypeCounts.add(
            DescriptorPool.DescriptorTypeCount(
                attachments.size,
                VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
            )
        )
        descriptorPool = DescriptorPool(device, descriptorTypeCounts)
    }

    private fun createShaders() {
        val engineProperties = EngineProperties.instance
        if (engineProperties.shaderRecompilation) {
            ShaderCompiler.compileShaderIfChanged(LIGHTING_VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader)
            ShaderCompiler.compileShaderIfChanged(
                LIGHTING_FRAGMENT_SHADER_FILE_GLSL,
                Shaderc.shaderc_glsl_fragment_shader
            )
        }
        shaderProgram = ShaderProgram(
            device, arrayOf(
                ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, LIGHTING_VERTEX_SHADER_FILE_SPV),
                ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, LIGHTING_FRAGMENT_SHADER_FILE_SPV)
            )
        )
    }

    fun cleanup() {
        attachmentsDescriptorSet.cleanup()
        attachmentsLayout.cleanup()
        descriptorPool.cleanup()
        pipeline.cleanup()
        lightingFrameBuffer.cleanup()
        shaderProgram.cleanup()
        commandBuffers.forEach(CommandBuffer::cleanup)
        fences.forEach(Fence::cleanup)
    }

    fun prepareCommandBuffer() {
        val idx = swapChain.currentFrame
        val fence = fences[idx]
        fence.fenceWait()
        fence.reset()
    }

    fun resize(swapChain: SwapChain, attachments: List<Attachment>) {
        this.swapChain = swapChain
        attachmentsDescriptorSet.update(attachments)
        lightingFrameBuffer.resize(swapChain)
        val numImages = swapChain.numImages
        for (i in 0 until numImages) {
            preRecordCommandBuffer(i)
        }
    }

    fun submit(queue: Queue) {
        MemoryStack.stackPush().use { stack ->
            val idx = swapChain.currentFrame
            val commandBuffer = commandBuffers[idx]
            val currentFence = fences[idx]
            val syncSemaphores = swapChain.syncSemaphoresList[idx]
            queue.submit(
                stack.pointers(commandBuffer.vkCommandBuffer),
                stack.longs(syncSemaphores.geometryCompleteSemaphore.vkSemaphore),
                stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT),
                stack.longs(syncSemaphores.renderCompleteSemaphore.vkSemaphore),
                currentFence
            )
        }
    }
    companion object {
        private const val LIGHTING_FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/lighting_fragment.glsl"
        private const val LIGHTING_FRAGMENT_SHADER_FILE_SPV = "$LIGHTING_FRAGMENT_SHADER_FILE_GLSL.spv"
        private const val LIGHTING_VERTEX_SHADER_FILE_GLSL = "resources/shaders/lighting_vertex.glsl"
        private const val LIGHTING_VERTEX_SHADER_FILE_SPV = "$LIGHTING_VERTEX_SHADER_FILE_GLSL.spv"
    }
}