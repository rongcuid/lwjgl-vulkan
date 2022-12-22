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

    init {
        val engProps = EngineProperties.instance
        instance = Instance(engProps.validate)
        physicalDevice = PhysicalDevice.createPhysicalDevice(instance, engProps.physDeviceName)
        device = Device(physicalDevice)
        surface = Surface(physicalDevice, window.windowHandle)
        graphQueue = Queue.GraphicsQueue(device, 0)
    }
    fun cleanup() {
        surface.cleanup()
        device.cleanup()
        physicalDevice.cleanup()
        instance.cleanup()
    }

    fun render(window: Window, scene: Scene) {
//        TODO("Not yet implemented")
    }
}