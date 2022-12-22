import eng.*
import eng.graph.Render
import eng.scene.Scene
import org.tinylog.kotlin.Logger

class Main : IAppLogic {
    override fun cleanup() {
        // TODO
    }

    override fun handleInput(window: Window, scene: Scene, diffTimeMillis: Long) {
        // TODO
    }

    override fun init(window: Window, scene: Scene, render: Render) {}
}

fun main(args: Array<String>) {
    Logger.info("Starting application")
    val engine = Engine("Vulkan Book", Main())
    engine.start()
}