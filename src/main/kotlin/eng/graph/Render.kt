package eng.graph

import eng.EngineProperties
import eng.Window
import eng.graph.vk.Instance
import eng.scene.Scene

class Render(window: Window, scene: Scene) {
    val instance: Instance

    init {
        val engProps = EngineProperties.instance
        instance = Instance(engProps.validate)
    }
    fun cleanup() {
        instance.cleanup()
    }

    fun render(window: Window, scene: Scene) {
//        TODO("Not yet implemented")
    }
}