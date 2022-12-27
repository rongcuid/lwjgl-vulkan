package eng.graph.geometry

import eng.EngineProperties
import eng.graph.VulkanModel
import eng.graph.vk.*
import eng.graph.vk.DescriptorPool.DescriptorTypeCount
import eng.graph.vk.DescriptorSet.UniformDescriptorSet
import eng.graph.vk.DescriptorSetLayout.*
import eng.graph.vk.ShaderProgram.ShaderModuleData
import eng.scene.Scene
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*
import java.nio.LongBuffer


class GeometryRenderActivity(
    var swapChain: SwapChain, commandPool: CommandPool,
    val pipelineCache: PipelineCache, val scene: Scene
) {
    val device: Device

    lateinit var pipeline: Pipeline
    val geometryFrameBuffer: GeometryFrameBuffer
    val attachments: List<Attachment> get() = geometryFrameBuffer.geometryAttachments.attachments
    private var shaderProgram: ShaderProgram? = null

    private lateinit var commandBuffers: Array<CommandBuffer>
    private lateinit var fences: Array<Fence>
    private lateinit var descriptorPool: DescriptorPool
    private lateinit var descriptorSetMap: HashMap<String, DescriptorSet>
    private lateinit var projMatrixDescriptorSet: UniformDescriptorSet
    private lateinit var projMatrixUniform: VulkanBuffer
    private lateinit var textureDescriptorSetLayout: SamplerDescriptorSetLayout
    private lateinit var textureSampler: TextureSampler
    private lateinit var uniformDescriptorSetLayout: UniformDescriptorSetLayout
    private lateinit var materialDescriptorSetLayout: DynUniformDescriptorSetLayout
    private lateinit var geometryDescriptorSetLayouts: Array<DescriptorSetLayout>
    private val materialSize: Int
    private lateinit var materialsDescriptorSet: DescriptorSet.DynUniformDescriptorSet
    private lateinit var materialsBuffer: VulkanBuffer
    private lateinit var viewMatricesBuffer: Array<VulkanBuffer>
    private lateinit var viewMatricesDescriptorSets: Array<UniformDescriptorSet>

    init {
        device = swapChain.device
        geometryFrameBuffer = GeometryFrameBuffer(swapChain)
        val numImages = swapChain.numImages
        materialSize = calcMaterialsUniformSize()
        createShaders()
        createDescriptorPool()
        createDescriptorSets(numImages)
        createPipeline()
        createCommandBuffers(commandPool, numImages)
        VulkanUtils.copyMatrixToBuffer(projMatrixUniform, scene.projection.projectionMatrix)
    }

    private fun createCommandBuffers(commandPool: CommandPool, numImages: Int) {
        commandBuffers = Array(numImages) { CommandBuffer(commandPool, true, false) }
        fences = Array(numImages) { Fence(device, true) }
    }

    private fun createPipeline() {
        val pipelineCreationInfo = Pipeline.PipelineCreationInfo(
            geometryFrameBuffer.geometryRenderPass.vkRenderPass,
            shaderProgram!!,
            GeometryAttachments.NUMBER_COLOR_ATTACHMENTS,
            true,
            true,
            GraphConstants.MAT4X4_SIZE,
            VertexBufferStructure(),
            geometryDescriptorSetLayouts
        )
        pipeline = Pipeline(pipelineCache, pipelineCreationInfo)
        pipelineCreationInfo.cleanup()
    }

    private fun createDescriptorSets(numImages: Int) {
        uniformDescriptorSetLayout = UniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_VERTEX_BIT)
        textureDescriptorSetLayout = SamplerDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT)
        materialDescriptorSetLayout = DynUniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT)
        geometryDescriptorSetLayouts = arrayOf<DescriptorSetLayout>(
            uniformDescriptorSetLayout,
            uniformDescriptorSetLayout,
            textureDescriptorSetLayout,
            textureDescriptorSetLayout,
            textureDescriptorSetLayout,
            materialDescriptorSetLayout
        )
        val engineProps = EngineProperties.instance
        descriptorSetMap = HashMap()
        textureSampler = TextureSampler(device, 1)
        projMatrixUniform = VulkanBuffer(
            device, GraphConstants.MAT4X4_SIZE.toLong(), VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0
        )
        projMatrixDescriptorSet =
            UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout, projMatrixUniform, 0)

        viewMatricesBuffer = Array(numImages) {
            VulkanBuffer(
                device, GraphConstants.MAT4X4_SIZE.toLong(), VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0
            )
        }
        viewMatricesDescriptorSets = Array(numImages) {
            UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout, viewMatricesBuffer[it], 0)
        }
        materialsBuffer = VulkanBuffer(
            device,
            (materialSize * engineProps.maxMaterials).toLong(), VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0
        )
        materialsDescriptorSet = DescriptorSet.DynUniformDescriptorSet(
            descriptorPool, materialDescriptorSetLayout,
            materialsBuffer, 0, materialSize.toLong()
        )
    }

    private fun createDescriptorPool() {
        val engineProps: EngineProperties = EngineProperties.instance
        val descriptorTypeCounts: MutableList<DescriptorTypeCount> = ArrayList()
        descriptorTypeCounts.add(DescriptorTypeCount(swapChain.numImages + 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER))
        descriptorTypeCounts.add(
            DescriptorTypeCount(
                engineProps.maxMaterials * 3,
                VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
            )
        )
        descriptorTypeCounts.add(DescriptorTypeCount(1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC))
        descriptorPool = DescriptorPool(device, descriptorTypeCounts)
    }

    private fun createShaders() {
        val engineProperties: EngineProperties = EngineProperties.instance
        if (engineProperties.shaderRecompilation) {
            ShaderCompiler.compileShaderIfChanged(GEOMETRY_VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader)
            ShaderCompiler.compileShaderIfChanged(
                GEOMETRY_FRAGMENT_SHADER_FILE_GLSL,
                Shaderc.shaderc_glsl_fragment_shader
            )
        }
        shaderProgram = ShaderProgram(
            device, arrayOf(
                ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, GEOMETRY_VERTEX_SHADER_FILE_SPV),
                ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, GEOMETRY_FRAGMENT_SHADER_FILE_SPV)
            )
        )
    }

    private fun calcMaterialsUniformSize(): Int {
        val physDevice = device.physicalDevice
        val minUboAlignment = physDevice.vkPhysicalDeviceProperties.limits().minUniformBufferOffsetAlignment()
        val mult = GraphConstants.VEC4_SIZE * 9 / minUboAlignment + 1
        return (mult * minUboAlignment).toInt()
    }

    fun cleanup() {
        pipeline.cleanup()
        materialsBuffer.cleanup()
        viewMatricesBuffer.forEach(VulkanBuffer::cleanup)
        projMatrixUniform.cleanup()
        textureSampler.cleanup()
        materialDescriptorSetLayout.cleanup()
        textureDescriptorSetLayout.cleanup()
        uniformDescriptorSetLayout.cleanup()
        descriptorPool.cleanup()
        shaderProgram!!.cleanup()
        geometryFrameBuffer.cleanup()
        commandBuffers.forEach { obj: CommandBuffer -> obj.cleanup() }
        fences.forEach { obj: Fence -> obj.cleanup() }
    }

    fun registerModels(vulkanModelList: List<VulkanModel>) {
        device.waitIdle()
        var materialCount = 0
        for (vulkanModel in vulkanModelList) {
            for (vulkanMaterial in vulkanModel.vulkanMaterialList) {
                val materialOffset = materialCount * materialSize
                updateTextureDescriptorSet(vulkanMaterial.texture)
                updateTextureDescriptorSet(vulkanMaterial.normalMap)
                updateTextureDescriptorSet(vulkanMaterial.metalRoughMap)
                updateMaterialsBuffer(materialsBuffer, vulkanMaterial, materialOffset)
                materialCount++
            }
        }
    }

    private fun updateMaterialsBuffer(buffer: VulkanBuffer, material: VulkanModel.VulkanMaterial, offset: Int) {
        val mappedMemory = buffer.map()
        val materialBuffer = MemoryUtil.memByteBuffer(mappedMemory, buffer.requestedSize.toInt())
        material.diffuseColor.get(offset, materialBuffer)
        materialBuffer.putFloat(offset + GraphConstants.FLOAT_LENGTH * 4, if (material.hasTexture) 1.0f else 0.0f)
        materialBuffer.putFloat(offset + GraphConstants.FLOAT_LENGTH * 5, if (material.hasNormalMap) 1.0f else 0.0f)
        materialBuffer.putFloat(
            offset + GraphConstants.FLOAT_LENGTH * 6,
            if (material.hasMetalRoughMap) 1.0f else 0.0f
        )
        materialBuffer.putFloat(offset + GraphConstants.FLOAT_LENGTH * 7, material.roughnessFactor)
        materialBuffer.putFloat(offset + GraphConstants.FLOAT_LENGTH * 8, material.metallicFactor)

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

    fun beginRecording(): CommandBuffer {
        val idx = swapChain.currentFrame
        val fence = fences[idx]
        val commandBuffer = commandBuffers[idx]
        fence.fenceWait()
        fence.reset()
        commandBuffer.reset()
        commandBuffer.beginRecording()
        return commandBuffer
    }

    fun endRecording(commandBuffer: CommandBuffer) {
        commandBuffer.endRecording()
    }

    fun recordCommandBuffer(commandBuffer: CommandBuffer, vulkanModelList: List<VulkanModel>) {
        MemoryStack.stackPush().use { stack ->
            val swapChainExtent = swapChain.swapChainExtent
            val width = swapChainExtent.width()
            val height = swapChainExtent.height()
            val idx = swapChain.currentFrame

            val frameBuffer = geometryFrameBuffer.frameBuffer
            val attachments = geometryFrameBuffer.geometryAttachments.attachments
            val clearValues = VkClearValue.calloc(attachments.size, stack)
            for (a in attachments) {
                if (a.isDepthAttachment) {
                    clearValues.apply { it.depthStencil().depth(1.0f) }
                } else {
                    clearValues.apply { it.color().float32(0, 0.0f).float32(1, 0.0f).float32(2, 0.0f).float32(3, 0.0f) }
                }
            }
            clearValues.flip()

            val renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                .`sType$Default`()
                .renderPass(geometryFrameBuffer.geometryRenderPass.vkRenderPass)
                .pClearValues(clearValues)
                .renderArea { it.extent().set(width, height) }
                .framebuffer(frameBuffer.vkFrameBuffer)

            val cmdHandle = commandBuffer.vkCommandBuffer
            vkCmdBeginRenderPass(cmdHandle, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE)

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.vkPipeline)

            val viewport = VkViewport.calloc(1, stack)
                .x(0f).y(height.toFloat())
                .height(-height.toFloat()).width(width.toFloat())
                .minDepth(0f).maxDepth(1f)
            vkCmdSetViewport(cmdHandle, 0, viewport)

            val scissor = VkRect2D.calloc(1, stack)
                .extent { it.width(width).height(height) }
                .offset { it.x(0).y(0) }
            vkCmdSetScissor(cmdHandle, 0, scissor)

            val descriptorSets = stack.mallocLong(6)
                .put(0, projMatrixDescriptorSet.vkDescriptorSet)
                .put(1, viewMatricesDescriptorSets[idx].vkDescriptorSet)
                .put(5, materialsDescriptorSet.vkDescriptorSet)
            VulkanUtils.copyMatrixToBuffer(viewMatricesBuffer[idx], scene.camera.viewMatrix)

            recordEntities(stack, cmdHandle, descriptorSets, vulkanModelList)

            vkCmdEndRenderPass(cmdHandle)
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
            if (entities!!.isEmpty()) {
                materialCount += vulkanModel.vulkanMaterialList.size
                continue
            }
            for (material in vulkanModel.vulkanMaterialList) {
                val materialOffset = materialCount * materialSize
                dynDescrSetOffset.put(0, materialOffset)
                val textureDescriptorSet = descriptorSetMap[material.texture.fileName]!!
                val normalMapDescriptorSet = descriptorSetMap[material.normalMap.fileName]!!
                val metalRoughDescriptorSet = descriptorSetMap[material.metalRoughMap.fileName]!!
                for (mesh in material.vulkanMeshList) {
                    vertexBuffer.put(0, mesh.verticesBuffer.buffer)
                    vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets)
                    vkCmdBindIndexBuffer(cmdHandle, mesh.indicesBuffer.buffer, 0, VK_INDEX_TYPE_UINT32)
                    for (entity in entities) {
                        descriptorSets.put(2, textureDescriptorSet.vkDescriptorSet)
                        descriptorSets.put(3, normalMapDescriptorSet.vkDescriptorSet)
                        descriptorSets.put(4, metalRoughDescriptorSet.vkDescriptorSet)
                        vkCmdBindDescriptorSets(
                            cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                            pipeline.vkPipelineLayout, 0, descriptorSets, dynDescrSetOffset
                        )
                        VulkanUtils.setMatrixAsPushConstant(pipeline, cmdHandle, entity.modelMatrix)
                        vkCmdDrawIndexed(cmdHandle, mesh.numIndices, 1, 0, 0, 0)
                    }
                }
                materialCount++
            }
        }
    }

    fun resize(swapChain: SwapChain) {
        VulkanUtils.copyMatrixToBuffer(projMatrixUniform, scene.projection.projectionMatrix)
        this.swapChain = swapChain
        geometryFrameBuffer.resize(swapChain)
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
                stack.longs(syncSemaphores.geometryCompleteSemaphore.vkSemaphore),
                currentFence
            )
        }
    }

    companion object {
        private const val GEOMETRY_FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/geometry_fragment.glsl"
        private const val GEOMETRY_FRAGMENT_SHADER_FILE_SPV = "$GEOMETRY_FRAGMENT_SHADER_FILE_GLSL.spv"
        private const val GEOMETRY_VERTEX_SHADER_FILE_GLSL = "resources/shaders/geometry_vertex.glsl"
        private const val GEOMETRY_VERTEX_SHADER_FILE_SPV = "$GEOMETRY_VERTEX_SHADER_FILE_GLSL.spv"
    }
}