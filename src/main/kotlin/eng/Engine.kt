package eng

import IAppLogic
import eng.graph.Render
import eng.scene.Scene

class Engine(windowTitle: String, appLogic: IAppLogic) {
    private var appLogic: IAppLogic = appLogic
    private var running: Boolean
    private var window: Window
    private var scene: Scene
    private var render: Render

    init {
        this.appLogic = appLogic
        window = Window(windowTitle)
        scene = Scene(window)
        render = Render(window, scene)
        this.appLogic.init(window, scene, render)
        running = false
    }

    fun cleanup() {
        appLogic.cleanup()
        render.cleanup()
        window.cleanup()
    }

    fun run() {
        val engineProperties = EngineProperties.instance
        val initialTime: Long = System.nanoTime()
        val timeU = 1000000000.0 / engineProperties.ups
        var deltaU = 0.0

        var updateTime: Long = initialTime
        while(running && !window.shouldClose()) {
            window.pollEvents()

            scene.camera.hasMoved = false

            val currentTime = System.nanoTime()
            deltaU += (currentTime - initialTime) / timeU
            if (deltaU >= 1) {
                val diffTimeInNanos = currentTime - updateTime
                appLogic.handleInput(window, scene, diffTimeInNanos)
                updateTime = currentTime
                deltaU--
            }
            render.render(window, scene)
        }
        cleanup()
    }

    fun start() {
        running = true
        run()
    }

    fun stop() {
        running = false
    }
}