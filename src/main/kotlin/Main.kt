import eng.Engine
import eng.Window
import eng.graph.Render
import eng.scene.ModelData
import eng.scene.ModelData.MeshData
import eng.scene.Entity
import eng.scene.ModelLoader
import eng.scene.Scene
import org.joml.Vector3f
import org.tinylog.kotlin.Logger


class Main : IAppLogic {
    var angle = 0f
    var cubeEntity: Entity? = null
    var rotatingAngle = Vector3f(1f, 1f, 1f)
    override fun cleanup() {
        // TODO
    }

    override fun handleInput(window: Window, scene: Scene, diffTimeMillis: Long) {
        angle += 1f
        if (angle >= 360f) {
            angle -= 360f
        }
        cubeEntity!!.rotation.identity().rotateAxis(Math.toRadians(angle.toDouble()).toFloat(), rotatingAngle)
        cubeEntity!!.updateModelMatrix()
    }

    override fun init(window: Window, scene: Scene, render: Render) {
        val modelDataList = ArrayList<ModelData>()
        val modelId = "CubeModel"
        val modelData = ModelLoader.loadModel(modelId, "resources/models/cube/cube.obj",
            "resources/models/cube")
        modelDataList.add(modelData)
        cubeEntity = Entity("CubeEntity", modelId, Vector3f(0f, 0f, 0f))
        cubeEntity!!.setPosition(0f, 0f, -2f)
        scene.addEntity(cubeEntity!!)

        render.loadModels(modelDataList)
    }
}

fun main(args: Array<String>) {
    Logger.info("Starting application")
    val engine = Engine("Vulkan Book", Main())
    engine.start()
}