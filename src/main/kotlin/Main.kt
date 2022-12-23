import eng.Engine
import eng.Window
import eng.graph.Render
import eng.graph.vk.ModelData
import eng.graph.vk.ModelData.MeshData
import eng.scene.Entity
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
        val positions = floatArrayOf(
            -0.5f, 0.5f, 0.5f,
            -0.5f, -0.5f, 0.5f,
            0.5f, -0.5f, 0.5f,
            0.5f, 0.5f, 0.5f,
            -0.5f, 0.5f, -0.5f,
            0.5f, 0.5f, -0.5f,
            -0.5f, -0.5f, -0.5f,
            0.5f, -0.5f, -0.5f
        )
        val textCoords = floatArrayOf(
            0.0f, 0.0f,
            0.5f, 0.0f,
            1.0f, 0.0f,
            1.0f, 0.5f,
            1.0f, 1.0f,
            0.5f, 1.0f,
            0.0f, 1.0f,
            0.0f, 0.5f
        )
        val indices = intArrayOf( // Front face
            0, 1, 3, 3, 1, 2,  // Top Face
            4, 0, 3, 5, 4, 3,  // Right face
            3, 2, 7, 5, 3, 7,  // Left face
            6, 1, 0, 6, 0, 4,  // Bottom face
            2, 1, 6, 2, 6, 7,  // Back face
            7, 6, 4, 7, 4, 5
        )
        val modelId = "CubeModel"
        val meshData = MeshData(positions, textCoords, indices)
        val meshDataList = ArrayList<MeshData>()
        meshDataList.add(meshData)
        val modelData = ModelData(modelId, meshDataList)
        val modelDataList = ArrayList<ModelData>()
        modelDataList.add(modelData)
        render.loadModels(modelDataList)

        cubeEntity = Entity("CubeEntity", modelId, Vector3f(0f, 0f, 0f))
        cubeEntity!!.setPosition(0f, 0f, 2f)
        scene.addEntity(cubeEntity!!)
    }
}

fun main(args: Array<String>) {
    Logger.info("Starting application")
    val engine = Engine("Vulkan Book", Main())
    engine.start()
}