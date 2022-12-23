import eng.Engine
import eng.Window
import eng.graph.Render
import eng.graph.vk.ModelData
import eng.graph.vk.ModelData.MeshData
import eng.scene.Scene
import org.tinylog.kotlin.Logger


class Main : IAppLogic {
    override fun cleanup() {
        // TODO
    }

    override fun handleInput(window: Window, scene: Scene, diffTimeMillis: Long) {
        // TODO
    }

    override fun init(window: Window, scene: Scene, render: Render) {
        val modelId = "TriangleModel"
        val meshData = MeshData(
            floatArrayOf(
                -0.5f, -0.5f, 0.0f,
                0.0f, 0.5f, 0.0f,
                0.5f, -0.5f, 0.0f
            ), intArrayOf(0, 1, 2)
        )
        val meshDataList = ArrayList<MeshData>()
        meshDataList.add(meshData)
        val modelData = ModelData(modelId, meshDataList)
        val modelDataList = ArrayList<ModelData>()
        modelDataList.add(modelData)
        render.loadModels(modelDataList)
    }
}

fun main(args: Array<String>) {
    Logger.info("Starting application")
    val engine = Engine("Vulkan Book", Main())
    engine.start()
}