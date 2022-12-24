package eng.graph

import eng.EngineProperties
import eng.Window
import eng.graph.vk.*
import eng.scene.ModelData
import eng.scene.Scene
import org.tinylog.kotlin.Logger

class Render(window: Window, scene: Scene) {
    val instance: Instance
    val device: Device
    val graphQueue: Queue.GraphicsQueue
    val physicalDevice: PhysicalDevice
    val pipelineCache: PipelineCache
    val surface: Surface
    var swapChain: SwapChain
    val commandPool: CommandPool
    val presentQueue: Queue.PresentQueue
    val fwdRenderActivity: ForwardRenderActivity
    val vulkanModels: MutableList<VulkanModel>
    val textureCache: TextureCache
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
        fwdRenderActivity = ForwardRenderActivity(swapChain, commandPool, pipelineCache, scene)
        vulkanModels = ArrayList()
        textureCache = TextureCache()
    }

    fun cleanup() {
        textureCache.cleanup()
        presentQueue.waitIdle()
        fwdRenderActivity.cleanup()
        commandPool.cleanup()
        vulkanModels.forEach(VulkanModel::cleanup)
        swapChain.cleanup()
        surface.cleanup()
        device.cleanup()
        physicalDevice.cleanup()
        instance.cleanup()
    }

    fun render(window: Window, scene: Scene) {
        if (window.width <= 0 && window.height <= 0) {
            return
        }
        if (window.resized || swapChain.acquireNextImage()) {
            window.resized = false
            resize(window)
            scene.projection.resize(window.width, window.height)
            swapChain.acquireNextImage()
        }
        fwdRenderActivity.recordCommandBuffer(vulkanModels)
        fwdRenderActivity.submit(presentQueue)
        if (swapChain.presentImage(graphQueue)) {
            window.resized = true
        }
    }
    fun loadModels(modelDataList: List<ModelData>) {
        Logger.debug("Loading {} model(s)", modelDataList.size)
        vulkanModels.addAll(VulkanModel.transformModels(modelDataList, textureCache, commandPool, graphQueue))
        Logger.debug("Loaded {} model(s)", modelDataList.size)

        fwdRenderActivity.registerModels(vulkanModels)
    }
    fun resize(window: Window) {
        val engProps = EngineProperties.instance
        device.waitIdle()
        graphQueue.waitIdle()
        swapChain.cleanup()
        swapChain = SwapChain(device, surface, window, engProps.requestedImages, engProps.vSync)
        fwdRenderActivity.resize(swapChain)
    }
}