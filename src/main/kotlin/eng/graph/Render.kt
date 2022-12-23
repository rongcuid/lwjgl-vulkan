package eng.graph

import eng.EngineProperties
import eng.Window
import eng.graph.vk.*
import eng.scene.Scene
import org.tinylog.kotlin.Logger

class Render(window: Window, scene: Scene) {
    val instance: Instance
    val device: Device
    val graphQueue: Queue.GraphicsQueue
    val physicalDevice: PhysicalDevice
    val pipelineCache: PipelineCache
    val surface: Surface
    val swapChain: SwapChain
    val commandPool: CommandPool
    val presentQueue: Queue.PresentQueue
    val fwdRenderActivity: ForwardRenderActivity
    val vulkanModels: MutableList<VulkanModel>
    init {
        val engProps = EngineProperties.instance
        instance = Instance(engProps.validate)
        physicalDevice = PhysicalDevice.createPhysicalDevice(instance, engProps.physDeviceName)
        device = Device(physicalDevice)
        surface = Surface(physicalDevice, window.windowHandle)
        graphQueue = Queue.GraphicsQueue(device, 0)
        presentQueue = Queue.PresentQueue(device, surface, 0)
        swapChain = SwapChain(device, surface, window, engProps.requestedImages, engProps.vSync)
        commandPool = CommandPool(device, graphQueue.queueFamilyIndex)
        pipelineCache = PipelineCache(device)
        fwdRenderActivity = ForwardRenderActivity(swapChain, commandPool, pipelineCache)
        vulkanModels = ArrayList()
    }

    fun cleanup() {
        presentQueue.waitIdle()
        fwdRenderActivity.cleanup()
        commandPool.cleanup()
        swapChain.cleanup()
        surface.cleanup()
        device.cleanup()
        physicalDevice.cleanup()
        instance.cleanup()
        vulkanModels.forEach(VulkanModel::cleanup)
    }

    fun render(window: Window, scene: Scene) {
        swapChain.acquireNextImage()
        fwdRenderActivity.recordCommandBuffer(vulkanModels)
        fwdRenderActivity.submit(presentQueue)
        swapChain.presentImage(graphQueue)
    }

    fun loadModels(modelDataList: List<ModelData>) {
        Logger.debug("Loading {} model(s)", modelDataList.size)
        vulkanModels.addAll(VulkanModel.transformModels(modelDataList, commandPool, graphQueue))
        Logger.debug("Loaded {} model(s)", modelDataList.size)
    }
}