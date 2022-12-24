package eng.graph.vk

import eng.graph.vk.VulkanUtils.Companion.vkCheck
import org.lwjgl.*
import org.lwjgl.system.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*
import org.tinylog.kotlin.Logger

class Pipeline(pipelineCache: PipelineCache, pipeLineCreationInfo: PipelineCreationInfo) {
    val device: Device
    val vkPipelineLayout: Long
    val vkPipeline: Long

    init {
        Logger.debug("Creating pipeline")
        device = pipelineCache.device
        MemoryStack.stackPush().use { stack ->
            val lp = stack.mallocLong(1)
            val main = stack.UTF8("main")
            val shaderModules = pipeLineCreationInfo.shaderProgram.shaderModules
            val numModules = shaderModules.size
            val shaderStages = VkPipelineShaderStageCreateInfo.calloc(numModules, stack)
            for (i in 0 until numModules) {
                shaderStages[i]
                    .`sType$Default`()
                    .stage(shaderModules[i].shaderStage)
                    .module(shaderModules[i].handle)
                    .pName(main)
            }
            val vkPipelineInputAssemblyStateCreateInfo = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                .`sType$Default`()
                .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
            val vkPipelineViewportStateCreateInfo = VkPipelineViewportStateCreateInfo.calloc(stack)
                .`sType$Default`()
                .viewportCount(1)
                .scissorCount(1)
            val vkPipelineRasterizationStateCreateInfo = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                .`sType$Default`()
                .polygonMode(VK_POLYGON_MODE_FILL)
                .cullMode(VK_CULL_MODE_NONE)
                .frontFace(VK_FRONT_FACE_CLOCKWISE)
                .lineWidth(1f)
            val vkPipelineMultisampleStateCreationInfo = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                .`sType$Default`()
                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
            var ds: VkPipelineDepthStencilStateCreateInfo? = null
            if (pipeLineCreationInfo.hasDepthAttachment) {
                ds = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .`sType$Default`()
                    .depthTestEnable(true)
                    .depthWriteEnable(true)
                    .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL)
                    .depthBoundsTestEnable(false)
                    .stencilTestEnable(false)
            }

            val blendAttState = VkPipelineColorBlendAttachmentState.calloc(
                pipeLineCreationInfo.numColorAttachments, stack
            )
            for (i in 0 until pipeLineCreationInfo.numColorAttachments) {
                blendAttState[i]
                    .colorWriteMask(
                        VK_COLOR_COMPONENT_R_BIT or VK_COLOR_COMPONENT_G_BIT or
                                VK_COLOR_COMPONENT_B_BIT or VK_COLOR_COMPONENT_A_BIT
                    )
            }
            val colorBlendState = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                .`sType$Default`()
                .pAttachments(blendAttState)
            val vkPipelineDynamicStateCreateInfo = VkPipelineDynamicStateCreateInfo.calloc(stack)
                .`sType$Default`()
                .pDynamicStates(
                    stack.ints(
                        VK_DYNAMIC_STATE_VIEWPORT,
                        VK_DYNAMIC_STATE_SCISSOR
                    )
                )
            var vpcr: VkPushConstantRange.Buffer? = null
            if (pipeLineCreationInfo.pushConstantsSize > 0) {
                vpcr = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                    .offset(0)
                    .size(pipeLineCreationInfo.pushConstantsSize)
            }
            val pPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .`sType$Default`()
                .pPushConstantRanges(vpcr)
            vkCheck(
                vkCreatePipelineLayout(device.vkDevice, pPipelineLayoutCreateInfo, null, lp),
                "Failed to create pipeline layout"
            )
            vkPipelineLayout = lp[0]
            val pipeline = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                .`sType$Default`()
                .pStages(shaderStages)
                .pVertexInputState(pipeLineCreationInfo.viInputStateInfo.vi)
                .pInputAssemblyState(vkPipelineInputAssemblyStateCreateInfo)
                .pViewportState(vkPipelineViewportStateCreateInfo)
                .pRasterizationState(vkPipelineRasterizationStateCreateInfo)
                .pMultisampleState(vkPipelineMultisampleStateCreationInfo)
                .pColorBlendState(colorBlendState)
                .pDynamicState(vkPipelineDynamicStateCreateInfo)
                .layout(vkPipelineLayout)
                .renderPass(pipeLineCreationInfo.vkRenderPass)
            if (ds != null) {
                pipeline.pDepthStencilState(ds)
            }
            vkCheck(
                vkCreateGraphicsPipelines(device.vkDevice, pipelineCache.vkPipelineCache, pipeline, null, lp),
                "Error creating graphics pipeline"
            )
            vkPipeline = lp[0]
        }
    }
    fun cleanup() {
        Logger.debug("Destroying pipeline")
        vkDestroyPipelineLayout(device.vkDevice, vkPipelineLayout, null)
        vkDestroyPipeline(device.vkDevice, vkPipeline, null)
    }

    data class PipelineCreationInfo(
        val vkRenderPass: Long, val shaderProgram: ShaderProgram, val numColorAttachments: Int,
        val hasDepthAttachment: Boolean, val pushConstantsSize: Int,
        val viInputStateInfo: VertexInputStateInfo
    ) {
        fun cleanup() {
            viInputStateInfo.cleanup()
        }
    }
}