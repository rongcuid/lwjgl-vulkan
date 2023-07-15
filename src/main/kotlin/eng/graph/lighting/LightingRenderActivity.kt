package eng.graph.lighting

import eng.EngineProperties
import eng.graph.shadows.CascadeShadow
import eng.graph.vk.*
import eng.graph.vk.DescriptorPool.DescriptorTypeCount
import eng.scene.Light
import eng.scene.Scene
import org.joml.Matrix4f
import org.joml.Vector4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER
import org.lwjgl.vulkan.VK12.*


class LightingRenderActivity(
    var swapChain: SwapChain, commandPool: CommandPool, val pipelineCache: PipelineCache,
    attachments: List<Attachment>, val scene: Scene
) {
    private lateinit var pipeline: Pipeline
    val device: Device = swapChain.device
    val lightingFrameBuffer = LightingFrameBuffer(swapChain)

    private val auxVec = Vector4f()

    private lateinit var commandBuffers: Array<CommandBuffer>
    private lateinit var shaderProgram: ShaderProgram
    private var lightSpecConstants = LightSpecConstants()
    private lateinit var fences: Array<Fence>
    private lateinit var descriptorPool: DescriptorPool
    private lateinit var descriptorSetLayout: Array<DescriptorSetLayout>
    private lateinit var attachmentsLayout: AttachmentsLayout
    private lateinit var attachmentsDescriptorSet: AttachmentsDescriptorSet
    private lateinit var invMatricesBuffers: Array<VulkanBuffer>
    private lateinit var invMatricesDescriptorSet: Array<DescriptorSet.UniformDescriptorSet>
    private lateinit var lightsBuffers: Array<VulkanBuffer>
    private lateinit var lightsMatricesDescriptorSets: Array<DescriptorSet.UniformDescriptorSet>
    private lateinit var shadowsMatricesBuffers: Array<VulkanBuffer>
    private lateinit var shadowsMatricesDescriptorSets: Array<DescriptorSet.UniformDescriptorSet>
    private lateinit var uniformDescriptorSetLayout: DescriptorSetLayout.UniformDescriptorSetLayout

    init {
        val numImages = swapChain.numImages
        createShaders()
        createDescriptorPool(attachments)
        createUniforms(numImages)
        createDescriptorSets(attachments, numImages)
        createPipeline(pipelineCache)
        createCommandBuffers(commandPool, numImages)
        for (i in 0 until numImages) {
            preRecordCommandBuffer(i)
        }
    }

    private fun createUniforms(numImages: Int) {
        invMatricesBuffers = Array(numImages) {
            VulkanBuffer(
                device, GraphConstants.MAT4X4_SIZE.toLong() * 2, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0
            )
        }
        lightsBuffers = Array(numImages) {
            VulkanBuffer(
                device, (GraphConstants.INT_LENGTH * 4 +
                        GraphConstants.VEC4_SIZE * 2 * GraphConstants.MAX_LIGHTS + GraphConstants.VEC4_SIZE).toLong(),
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0
            )
        }
        shadowsMatricesBuffers = Array(numImages) {
            VulkanBuffer(device,
                ((GraphConstants.MAT4X4_SIZE + GraphConstants.VEC4_SIZE) * GraphConstants.SHADOW_MAP_CASCADE_COUNT).toLong(),
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0)
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
            renderArea.offset().set(0, 0)
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
                .extent { it.width(width).height(height) }
                .offset { it.x(0).y(0) }
            vkCmdSetScissor(cmdHandle, 0, scissor)

            val descriptorSets = stack.mallocLong(4)
                .put(0, attachmentsDescriptorSet.vkDescriptorSet)
                .put(1, lightsMatricesDescriptorSets[idx].vkDescriptorSet)
                .put(2, invMatricesDescriptorSet[idx].vkDescriptorSet)
                .put(3, shadowsMatricesDescriptorSets[idx].vkDescriptorSet)
            vkCmdBindDescriptorSets(
                cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                pipeline.vkPipelineLayout, 0, descriptorSets, null
            )
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

    private fun createDescriptorSets(attachments: List<Attachment>, numImages: Int) {
        attachmentsLayout = AttachmentsLayout(device, attachments.size)
        uniformDescriptorSetLayout = DescriptorSetLayout.UniformDescriptorSetLayout(device, 0,
            VK_SHADER_STAGE_FRAGMENT_BIT)
        descriptorSetLayout = arrayOf(
            attachmentsLayout,
            uniformDescriptorSetLayout,
            uniformDescriptorSetLayout
        )
        attachmentsDescriptorSet = AttachmentsDescriptorSet(descriptorPool, attachmentsLayout, attachments, 0)
        invMatricesDescriptorSet = Array(numImages) {
            DescriptorSet.UniformDescriptorSet(
                descriptorPool, uniformDescriptorSetLayout,
                shadowsMatricesBuffers[it], 0
            )
        }
        lightsMatricesDescriptorSets = Array(numImages) {
            DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout,
                lightsBuffers[it], 0)
        }
        shadowsMatricesDescriptorSets = Array(numImages) {
            DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout,
                shadowsMatricesBuffers[it], 0)
        }
    }

    private fun createDescriptorPool(attachments: List<Attachment>) {
        val descriptorTypeCounts = ArrayList<DescriptorTypeCount>()
        descriptorTypeCounts.add(
            DescriptorTypeCount(
                attachments.size,
                VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
            )
        )
        descriptorTypeCounts.add(
            DescriptorTypeCount(swapChain.numImages + 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
        )
        descriptorTypeCounts.add(DescriptorTypeCount(swapChain.numImages * 3, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER))
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
                ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, LIGHTING_FRAGMENT_SHADER_FILE_SPV,
                    lightSpecConstants.specInfo)
            )
        )
    }

    fun cleanup() {
        uniformDescriptorSetLayout.cleanup()
        attachmentsDescriptorSet.cleanup()
        attachmentsLayout.cleanup()
        descriptorPool.cleanup()
        lightsBuffers.forEach { it.cleanup() }
        pipeline.cleanup()
        invMatricesBuffers.forEach {it.cleanup()}
        lightingFrameBuffer.cleanup()
        lightSpecConstants.cleanup()
        shadowsMatricesBuffers.forEach{it.cleanup()}
        shaderProgram.cleanup()
        commandBuffers.forEach(CommandBuffer::cleanup)
        fences.forEach(Fence::cleanup)
    }

    fun prepareCommandBuffer(cascadeShadows: List<CascadeShadow>) {
        val idx = swapChain.currentFrame
        val fence = fences[idx]
        fence.fenceWait()
        fence.reset()

        updateLights(scene.ambientLight, scene.lights, scene.camera.viewMatrix, lightsBuffers[idx])
        updateInvMatrices(scene, invMatricesBuffers[idx])
        updateCascadeShadowMatrices(cascadeShadows, shadowsMatricesBuffers[idx])
    }

    private fun updateCascadeShadowMatrices(cascadeShadows: List<CascadeShadow>, shadowsUniformBuffer: VulkanBuffer) {
        val mappedMemory = shadowsUniformBuffer.map()
        val buffer = MemoryUtil.memByteBuffer(mappedMemory, shadowsUniformBuffer.requestedSize.toInt())
        var offset = 0
        for (cascadeShadow in cascadeShadows) {
            cascadeShadow.projViewMatrix.get(offset, buffer)
            buffer.putFloat(offset + GraphConstants.MAT4X4_SIZE, cascadeShadow.splitDistance)
            offset += GraphConstants.MAT4X4_SIZE + GraphConstants.VEC4_SIZE
        }
        shadowsUniformBuffer.unmap()
    }

    private fun updateLights(
        ambientLight: Vector4f,
        lights: Array<Light>?,
        viewMatrix: Matrix4f,
        lightsBuffer: VulkanBuffer
    ) {
        val mappedMemory = lightsBuffer.map()
        val uniformBuffer = MemoryUtil.memByteBuffer(mappedMemory, lightsBuffer.requestedSize.toInt())

        ambientLight.get(0, uniformBuffer)
        var offset = GraphConstants.VEC4_SIZE
        val numLights = lights?.size ?: 0
        uniformBuffer.putInt(offset, numLights)
        offset += GraphConstants.VEC4_SIZE
        for (i in 0 until numLights) {
            val light = lights!![i]
            auxVec.set(light.position)
            auxVec.mul(viewMatrix)
            auxVec.w = light.position.w
            auxVec.get(offset, uniformBuffer)
            offset += GraphConstants.VEC4_SIZE
            light.color.get(offset, uniformBuffer)
            offset += GraphConstants.VEC4_SIZE
        }
        lightsBuffer.unmap()
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

    private fun updateInvMatrices(scene: Scene, invMatricesBuffer: VulkanBuffer) {
        val invProj = Matrix4f(scene.projection.projectionMatrix).invert()
        val invView = Matrix4f(scene.camera.viewMatrix).invert()
        VulkanUtils.copyMatrixToBuffer(invMatricesBuffer, invProj, 0)
        VulkanUtils.copyMatrixToBuffer(invMatricesBuffer, invProj, GraphConstants.MAT4X4_SIZE)
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