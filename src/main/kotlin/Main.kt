import eng.Engine
import eng.MouseInput
import eng.Window
import eng.graph.Render
import eng.scene.*
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.*
import org.tinylog.kotlin.Logger


class Main : IAppLogic {
    override fun cleanup() {
        // TODO
    }

    override fun handleInput(window: Window, scene: Scene, diffTimeMillis: Long) {
        val move = diffTimeMillis * MOVEMENT_SPEED
        val camera: Camera = scene.camera
        if (window.isKeyPressed(GLFW_KEY_W)) {
            camera.moveForward(move)
        } else if (window.isKeyPressed(GLFW_KEY_S)) {
            camera.moveBackwards(move)
        }
        if (window.isKeyPressed(GLFW_KEY_A)) {
            camera.moveLeft(move)
        } else if (window.isKeyPressed(GLFW_KEY_D)) {
            camera.moveRight(move)
        }
        if (window.isKeyPressed(GLFW_KEY_UP)) {
            camera.moveUp(move)
        } else if (window.isKeyPressed(GLFW_KEY_DOWN)) {
            camera.moveDown(move)
        }

        val mouseInput: MouseInput = window.mouseInput
        if (mouseInput.rightButtonPressed) {
            val displVec: Vector2f = mouseInput.displVec
            camera.addRotation(
                Math.toRadians((-displVec.x * MOUSE_SENSITIVITY).toDouble()).toFloat(), Math.toRadians(
                    (-displVec.y * MOUSE_SENSITIVITY).toDouble()
                ).toFloat()
            )
        }
    }

    override fun init(window: Window, scene: Scene, render: Render) {
        val modelDataList: MutableList<ModelData> = ArrayList()

        val sponzaModelId = "sponza-model"
        val sponzaModelData = ModelLoader.loadModel(
            sponzaModelId, "resources/models/sponza/Sponza.gltf",
            "resources/models/sponza"
        )
        modelDataList.add(sponzaModelData)
        val sponzaEntity = Entity("SponzaEntity", sponzaModelId, Vector3f(0.0f, 0.0f, 0.0f))
        scene.addEntity(sponzaEntity)

        render.loadModels(modelDataList)

        val camera: Camera = scene.camera
        camera.setPosition(0.0f, 5.0f, -0.0f)
        camera.setRotation(Math.toRadians(20.0).toFloat(), Math.toRadians(90.0).toFloat())
    }

    companion object {
        const val MOUSE_SENSITIVITY = 0.1f
        const val MOVEMENT_SPEED = 10.0f / 1e9f
    }
}

fun main(args: Array<String>) {
    Logger.info("Starting application")
    val engine = Engine("Vulkan Book", Main())
    engine.start()
}