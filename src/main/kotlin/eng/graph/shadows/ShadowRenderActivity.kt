package eng.graph.shadows

import eng.EngineProperties
import eng.graph.VulkanModel
import eng.graph.geometry.GeometryAttachments
import eng.graph.vk.*
import eng.graph.vk.DescriptorPool.DescriptorTypeCount
import eng.graph.vk.DescriptorSet.UniformDescriptorSet
import eng.graph.vk.DescriptorSetLayout.UniformDescriptorSetLayout
import eng.graph.vk.ShaderProgram.ShaderModuleData
import eng.scene.Entity
import eng.scene.Scene
import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*
import java.nio.LongBuffer


class ShadowRenderActivity(swapChain: SwapChain, pipelineCache: PipelineCache, scene: Scene) {
    private val device: Device
    private val scene: Scene
    val shadowsFrameBuffer: ShadowsFrameBuffer
    private var swapChain: SwapChain
    val depthAttachment: Attachment
        get() = shadowsFrameBuffer.depthAttachment

    lateinit var cascadeShadows: MutableList<CascadeShadow>
    private lateinit var descriptorPool: DescriptorPool
    private lateinit var descriptorSetLayouts: Array<DescriptorSetLayout>
    private lateinit var pipeLine: Pipeline
    private lateinit var projMatrixDescriptorSet: Array<UniformDescriptorSet>
    private lateinit var shaderProgram: ShaderProgram
    private lateinit var shadowsUniforms: Array<VulkanBuffer>
    private lateinit var uniformDescriptorSetLayout: UniformDescriptorSetLayout

    init {
        this.swapChain = swapChain
        this.scene = scene
        device = swapChain.device
        val numImages = swapChain.numImages
        shadowsFrameBuffer = ShadowsFrameBuffer(device)
        createShaders()
        createDescriptorPool(numImages)
        createDescriptorSets(numImages)
        createPipeline(pipelineCache)
        createShadowCascades()
    }

    private fun createShaders() {
        val engineProperties: EngineProperties = EngineProperties.instance
        if (engineProperties.shaderRecompilation) {
            ShaderCompiler.compileShaderIfChanged(SHADOW_VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader)
            ShaderCompiler.compileShaderIfChanged(
                SHADOW_GEOMETRY_SHADER_FILE_GLSL,
                Shaderc.shaderc_glsl_geometry_shader
            )
        }
        shaderProgram = ShaderProgram(
            device, arrayOf(
                ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, SHADOW_VERTEX_SHADER_FILE_SPV),
                ShaderModuleData(VK_SHADER_STAGE_GEOMETRY_BIT, SHADOW_GEOMETRY_SHADER_FILE_SPV)
            )
        )
    }

    private fun createDescriptorPool(numImages: Int) {
        val descriptorTypeCounts: MutableList<DescriptorTypeCount> = ArrayList()
        descriptorTypeCounts.add(DescriptorTypeCount(numImages, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER))
        descriptorPool = DescriptorPool(device, descriptorTypeCounts)
    }

    private fun createDescriptorSets(numImages: Int) {
        uniformDescriptorSetLayout = UniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_GEOMETRY_BIT)
        descriptorSetLayouts = arrayOf(
            uniformDescriptorSetLayout
        )
        shadowsUniforms = Array(numImages) {
            VulkanBuffer(
                device, (GraphConstants.MAT4X4_SIZE * GraphConstants.SHADOW_MAP_CASCADE_COUNT).toLong(),
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0
            )
        }
        projMatrixDescriptorSet = Array(numImages) {
            UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout, shadowsUniforms[it], 0)
        }
    }

    private fun createPipeline(pipelineCache: PipelineCache) {
        val pipeLineCreationInfo = Pipeline.PipelineCreationInfo(
            shadowsFrameBuffer.shadowsRenderPass.vkRenderPass, shaderProgram,
            GeometryAttachments.NUMBER_COLOR_ATTACHMENTS, true, true, GraphConstants.MAT4X4_SIZE,
            VertexBufferStructure(), descriptorSetLayouts
        )
        pipeLine = Pipeline(pipelineCache, pipeLineCreationInfo)
    }

    private fun createShadowCascades() {
        cascadeShadows = ArrayList()
        for (i in 0 until GraphConstants.SHADOW_MAP_CASCADE_COUNT) {
            val cascadeShadow = CascadeShadow()
            cascadeShadows.add(cascadeShadow)
        }
    }

    fun cleanup() {
        pipeLine.cleanup()
        shadowsUniforms.forEach { it.cleanup() }
        uniformDescriptorSetLayout.cleanup()
        descriptorPool.cleanup()
        shaderProgram.cleanup()
        shadowsFrameBuffer.cleanup()
    }

    fun recordCommandBuffer(commandBuffer: CommandBuffer, vulkanModelList: List<VulkanModel>) {
        MemoryStack.stackPush().use { stack ->
            if (scene.lightChanged || scene.camera.hasMoved) {
                CascadeShadow.updateCascadeShadows(cascadeShadows, scene)
            }
            val idx = swapChain.currentFrame
            updateProjViewBuffers(idx)
            val clearValues = VkClearValue.calloc(1, stack)
            clearValues.apply(
                0
            ) { v: VkClearValue -> v.depthStencil().depth(1.0f) }
            val engineProperties: EngineProperties = EngineProperties.instance
            val shadowMapSize: Int = engineProperties.shadowMapSize
            val cmdHandle = commandBuffer.vkCommandBuffer
            val viewport = VkViewport.calloc(1, stack)
                .x(0f)
                .y(shadowMapSize.toFloat())
                .height(-shadowMapSize.toFloat())
                .width(shadowMapSize.toFloat())
                .minDepth(0.0f)
                .maxDepth(1.0f)
            vkCmdSetViewport(cmdHandle, 0, viewport)
            val scissor = VkRect2D.calloc(1, stack)
                .extent { it: VkExtent2D ->
                    it
                        .width(shadowMapSize)
                        .height(shadowMapSize)
                }
                .offset { it: VkOffset2D ->
                    it
                        .x(0)
                        .y(0)
                }
            vkCmdSetScissor(cmdHandle, 0, scissor)
            val frameBuffer = shadowsFrameBuffer.frameBuffer
            val renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(shadowsFrameBuffer.shadowsRenderPass.vkRenderPass)
                .pClearValues(clearValues)
                .renderArea { a: VkRect2D -> a.extent()[shadowMapSize] = shadowMapSize }
                .framebuffer(frameBuffer.vkFrameBuffer)
            vkCmdBeginRenderPass(cmdHandle, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE)
            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeLine.vkPipeline)
            val descriptorSets: LongBuffer = stack.mallocLong(1)
                .put(0, projMatrixDescriptorSet[idx].vkDescriptorSet)
            vkCmdBindDescriptorSets(
                cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                pipeLine.vkPipelineLayout, 0, descriptorSets, null
            )
            recordEntities(stack, cmdHandle, vulkanModelList)
            vkCmdEndRenderPass(cmdHandle)
        }
    }

    private fun recordEntities(stack: MemoryStack, cmdHandle: VkCommandBuffer, vulkanModelList: List<VulkanModel>) {
        val offsets = stack.mallocLong(1)
        offsets.put(0, 0L)
        val vertexBuffer = stack.mallocLong(1)
        for (vulkanModel in vulkanModelList) {
            val modelId = vulkanModel.modelId
            val entities: List<Entity>? = scene.getEntitiesByModelId(modelId)
            if (entities.isNullOrEmpty()) {
                continue
            }
            for (material in vulkanModel.vulkanMaterialList) {
                for (mesh in material.vulkanMeshList) {
                    vertexBuffer.put(0, mesh.verticesBuffer.buffer)
                    vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets)
                    vkCmdBindIndexBuffer(cmdHandle, mesh.indicesBuffer.buffer, 0, VK_INDEX_TYPE_UINT32)
                    for (entity in entities) {
                        setPushConstant(pipeLine, cmdHandle, entity.modelMatrix)
                        vkCmdDrawIndexed(cmdHandle, mesh.numIndices, 1, 0, 0, 0)
                    }
                }
            }
        }
    }

    fun resize(swapChain: SwapChain) {
        this.swapChain = swapChain
        CascadeShadow.updateCascadeShadows(cascadeShadows, scene)
    }

    private fun setPushConstant(pipeLine: Pipeline, cmdHandle: VkCommandBuffer, matrix: Matrix4f) {
        MemoryStack.stackPush().use { stack ->
            val pushConstantBuffer = stack.malloc(GraphConstants.MAT4X4_SIZE)
            matrix.get(0, pushConstantBuffer)
            vkCmdPushConstants(
                cmdHandle, pipeLine.vkPipelineLayout,
                VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstantBuffer
            )
        }
    }

    private fun updateProjViewBuffers(idx: Int) {
        var offset = 0
        for (cascadeShadow in cascadeShadows) {
            VulkanUtils.copyMatrixToBuffer(shadowsUniforms[idx], cascadeShadow.projViewMatrix, offset)
            offset += GraphConstants.MAT4X4_SIZE
        }
    }

    companion object {
        private const val SHADOW_GEOMETRY_SHADER_FILE_GLSL = "resources/shaders/shadow_geometry.glsl"
        private const val SHADOW_GEOMETRY_SHADER_FILE_SPV = "$SHADOW_GEOMETRY_SHADER_FILE_GLSL.spv"
        private const val SHADOW_VERTEX_SHADER_FILE_GLSL = "resources/shaders/shadow_vertex.glsl"
        private const val SHADOW_VERTEX_SHADER_FILE_SPV = "$SHADOW_VERTEX_SHADER_FILE_GLSL.spv"

    }
}