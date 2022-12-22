import eng.Window
import eng.graph.Render
import eng.scene.Scene

interface IAppLogic {
    fun cleanup()
    fun handleInput(window: Window, scene: Scene, diffTimeMillis: Long)
    fun init(window: Window, scene: Scene, render: Render)
}