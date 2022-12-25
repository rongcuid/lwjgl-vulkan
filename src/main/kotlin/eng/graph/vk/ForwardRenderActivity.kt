package eng.graph.vk

import eng.EngineProperties
import eng.graph.VulkanModel
import eng.graph.vk.DescriptorSet.UniformDescriptorSet
import eng.graph.vk.DescriptorSetLayout.SamplerDescriptorSetLayout
import eng.graph.vk.DescriptorSetLayout.UniformDescriptorSetLayout
import eng.scene.Scene
import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*
import java.nio.ByteBuffer
import java.nio.LongBuffer

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

    private val descriptorPool: DescriptorPool
    private val descriptorSetLayouts: Array<DescriptorSetLayout>
    private val descriptorSetMap: MutableMap<String, TextureDescriptorSet>
    private val projMatrixDescriptorSet: UniformDescriptorSet
    private val projMatrixUniform: VulkanBuffer
    private val textureDescriptorSetLayout: SamplerDescriptorSetLayout
    private val textureSampler: TextureSampler
    private val uniformDescriptorSetLayout: UniformDescriptorSetLayout
    private val materialDescriptorSetLayout: DescriptorSetLayout.DynUniformDescriptorSetLayout
    private val materialSize: Int
    private val materialsDescriptorSet: DescriptorSet.DynUniformDescriptorSet
    private val materialsBuffer: VulkanBuffer
    private val viewMatricesBuffer: Array<VulkanBuffer>
    private val viewMatricesDescriptorSets: Array<UniformDescriptorSet>


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
            materialSize = calcMaterialsUniformSize()
            // Create descriptor sets
            uniformDescriptorSetLayout =
                UniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_VERTEX_BIT)
            textureDescriptorSetLayout =
                SamplerDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT)
            materialDescriptorSetLayout =
                DescriptorSetLayout.DynUniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT)
            descriptorSetLayouts = arrayOf(
                uniformDescriptorSetLayout,
                uniformDescriptorSetLayout,
                textureDescriptorSetLayout,
                materialDescriptorSetLayout
            )
            val descriptorTypeCounts = ArrayList<DescriptorPool.DescriptorTypeCount>()
            descriptorTypeCounts.add(
                DescriptorPool.DescriptorTypeCount(
                    swapChain.numImages + 1,
                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER
                )
            )
            descriptorTypeCounts.add(
                DescriptorPool.DescriptorTypeCount(
                    engineProperties.maxMaterials,
                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
                )
            )
            descriptorTypeCounts.add(
                DescriptorPool.DescriptorTypeCount(
                    1,
                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC
                )
            )
            descriptorPool = DescriptorPool(device, descriptorTypeCounts)
            descriptorSetMap = HashMap()
            textureSampler = TextureSampler(device, 1)
            projMatrixUniform = VulkanBuffer(
                device, GraphConstants.MAT4X4_SIZE.toLong(), VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
            )
            projMatrixDescriptorSet = UniformDescriptorSet(
                descriptorPool, uniformDescriptorSetLayout,
                projMatrixUniform, 0
            )
            viewMatricesBuffer = Array(numImages) {
                VulkanBuffer(
                    device, GraphConstants.MAT4X4_SIZE.toLong(), VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
                )
            }
            viewMatricesDescriptorSets = Array(numImages) {
                UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout, viewMatricesBuffer[it], 0)
            }
            materialsBuffer = VulkanBuffer(
                device, (materialSize * engineProperties.maxMaterials).toLong(),
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
            )
            materialsDescriptorSet = DescriptorSet.DynUniformDescriptorSet(
                descriptorPool, materialDescriptorSetLayout,
                materialsBuffer, 0, materialSize.toLong()
            )

            VulkanUtils.copyMatrixToBuffer(projMatrixUniform, scene.projection.projectionMatrix)

            val pipelineCreationInfo = Pipeline.PipelineCreationInfo(
                renderPass.vkRenderPass, fwdShaderProgram,
                1, true, true, GraphConstants.MAT4X4_SIZE * 2,
                VertexBufferStructure(), descriptorSetLayouts
            )
            pipeline = Pipeline(pipelineCache, pipelineCreationInfo)
            pipelineCreationInfo.cleanup()

            commandBuffers = Array(numImages) { CommandBuffer(commandPool, true, false) }
            fences = Array(numImages) { Fence(device, true) }
        }
    }

    private fun calcMaterialsUniformSize(): Int {
        val physDevice = device.physicalDevice
        val minUboAlignment = physDevice.vkPhysicalDeviceProperties.limits().minUniformBufferOffsetAlignment()
        val mult = (GraphConstants.VEC4_SIZE * 9) / minUboAlignment + 1
        return (mult * minUboAlignment).toInt()
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
        materialsBuffer.cleanup()
        viewMatricesBuffer.forEach(VulkanBuffer::cleanup)
        projMatrixUniform.cleanup()
        textureSampler.cleanup()
        descriptorPool.cleanup()
        fwdShaderProgram.cleanup()
        pipelineCache.cleanup()
        depthAttachments.forEach(Attachment::cleanup)
        frameBuffers.forEach(FrameBuffer::cleanup)
        renderPass.cleanup()
        commandBuffers.forEach(CommandBuffer::cleanup)
        fences.forEach(Fence::cleanup)
        descriptorSetLayouts.forEach(DescriptorSetLayout::cleanup)
        materialDescriptorSetLayout.cleanup()
        pipeline.cleanup()
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

            val descriptorSets = stack.mallocLong(4)
                .put(0, projMatrixDescriptorSet.vkDescriptorSet)
                .put(1, viewMatricesDescriptorSets[idx].vkDescriptorSet)
                .put(3, materialsDescriptorSet.vkDescriptorSet)
            VulkanUtils.copyMatrixToBuffer(viewMatricesBuffer[idx], scene.camera.viewMatrix)
            recordEntities(stack, cmdHandle, descriptorSets, vulkanModelList)

            vkCmdEndRenderPass(cmdHandle)
            commandBuffer.endRecording()
        }
    }

    private fun recordEntities(
        stack: MemoryStack,
        cmdHandle: VkCommandBuffer,
        descriptorSets: LongBuffer,
        vulkanModelList: List<VulkanModel>
    ) {
        val offsets = stack.mallocLong(1)
        offsets.put(0, 0)
        val vertexBuffer = stack.mallocLong(1)
        val dynDescrSetOffset = stack.callocInt(1)
        var materialCount = 0
        for (vulkanModel in vulkanModelList) {
            val modelId = vulkanModel.modelId
            val entities = scene.getEntitiesByModelId(modelId)
            if (entities.isNullOrEmpty()) {
                materialCount += vulkanModel.vulkanMaterialList.size
                continue
            }
            for (material in vulkanModel.vulkanMaterialList) {
                if (material.vulkanMeshList.isEmpty()) {
                    materialCount++
                    continue
                }
                val materialOffset = materialCount * materialSize
                dynDescrSetOffset.put(0, materialOffset)
                val textureDescriptorSet = descriptorSetMap[material.texture.fileName]!!
                descriptorSets.put(2, textureDescriptorSet.vkDescriptorSet)
                for (mesh in material.vulkanMeshList) {
                    vertexBuffer.put(0, mesh.verticesBuffer.buffer)
                    vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets)
                    vkCmdBindIndexBuffer(cmdHandle, mesh.indicesBuffer.buffer, 0, VK_INDEX_TYPE_UINT32)
                    for (e in entities) {
                        vkCmdBindDescriptorSets(
                            cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                            pipeline.vkPipelineLayout, 0, descriptorSets, dynDescrSetOffset
                        )
                        VulkanUtils.setMatrixAsPushConstant(pipeline, cmdHandle, e.modelMatrix)
                        vkCmdDrawIndexed(cmdHandle, mesh.numIndices, 1, 0, 0, 0)
                    }
                }
                materialCount++
            }
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
        VulkanUtils.copyMatrixToBuffer(projMatrixUniform, scene.projection.projectionMatrix)
        this.swapChain = swapChain
        frameBuffers.forEach(FrameBuffer::cleanup)
        depthAttachments.forEach(Attachment::cleanup)
        depthAttachments = createDepthImages()
        frameBuffers = createFrameBuffers()
    }

    fun registerModels(vulkanModelList: List<VulkanModel>) {
        device.waitIdle()
        var materialCount = 0
        for (vulkanModel in vulkanModelList) {
            for (vulkanMaterial in vulkanModel.vulkanMaterialList) {
                val materialOffset = materialCount * materialSize
                updateTextureDescriptorSet(vulkanMaterial.texture)
                updateMaterialsBuffer(materialsBuffer, vulkanMaterial, materialOffset)
                materialCount++
            }
        }
    }

    private fun updateMaterialsBuffer(buffer: VulkanBuffer, material: VulkanModel.VulkanMaterial, offset: Int) {
        val mappedMemory = buffer.map()
        val materialBuffer = MemoryUtil.memByteBuffer(mappedMemory, buffer.requestedSize.toInt())
        material.diffuseColor.get(offset, materialBuffer)
        buffer.unmap()
    }

    private fun updateTextureDescriptorSet(texture: Texture) {
        val textureFileName = texture.fileName
        var textureDescriptorSet = descriptorSetMap[textureFileName]
        if (textureDescriptorSet == null) {
            textureDescriptorSet = TextureDescriptorSet(
                descriptorPool, textureDescriptorSetLayout,
                texture, textureSampler, 0
            )
            descriptorSetMap.put(textureFileName, textureDescriptorSet)
        }
    }

    companion object {
        private val FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/fwd_fragment.glsl"
        private val FRAGMENT_SHADER_FILE_SPV = "$FRAGMENT_SHADER_FILE_GLSL.spv"
        private val VERTEX_SHADER_FILE_GLSL = "resources/shaders/fwd_vertex.glsl"
        private val VERTEX_SHADER_FILE_SPV = "$VERTEX_SHADER_FILE_GLSL.spv"
    }
}