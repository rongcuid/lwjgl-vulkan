package eng.graph

import eng.EngineProperties
import eng.Window
import eng.graph.vk.*
import eng.scene.Scene

class Render(window: Window, scene: Scene) {
    val instance: Instance
    val device: Device
    val graphQueue: Queue.GraphicsQueue
    val physicalDevice: PhysicalDevice
    val surface: Surface
    val swapChain: SwapChain
    val commandPool: CommandPool
    val presentQueue: Queue.PresentQueue
    val fwdRenderActivity: ForwardRenderActivity
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
        fwdRenderActivity = ForwardRenderActivity(swapChain, commandPool)
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
    }

    fun render(window: Window, scene: Scene) {
        swapChain.acquireNextImage()
        fwdRenderActivity.submit(presentQueue)
        swapChain.presentImage(graphQueue)
    }
}