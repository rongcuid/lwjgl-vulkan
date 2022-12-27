import eng.Engine
import eng.MouseInput
import eng.Window
import eng.graph.Render
import eng.scene.*
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.*
import org.tinylog.kotlin.Logger
import java.lang.Math.*


class Main : IAppLogic {

    var angleInc = 0f
    var directionalLight = Light()
    var lightAngle = 0f
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
        if (window.isKeyPressed(GLFW_KEY_LEFT)) {
            angleInc -= 0.05f
        } else if (window.isKeyPressed(GLFW_KEY_RIGHT)) {
            angleInc += 0.05f
        } else {
            angleInc = 0f
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

        lightAngle += angleInc;
        if (lightAngle < 0) {
            lightAngle = 0f
        } else if (lightAngle > 180) {
            lightAngle = 180f
        }
        updateDirectionalLight()
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

        scene.ambientLight.set(0.2f, 0.2f, 0.2f, 1.0f)
        val lights = ArrayList<Light>()
        directionalLight.position.set(0f, 1f, 0f, 0f)
        directionalLight.color.set(1f, 1f, 1f, 1f)
        lights.add(directionalLight)
        updateDirectionalLight()

        val light = Light()
        light.position.set(0f, 1f, 0f, 1f)
        light.color.set(0f, 1f, 0f, 1f)

        val lightArr = lights.toTypedArray()
        scene.lights = lightArr
    }

    private fun updateDirectionalLight() {
        val zValue = cos(toRadians(lightAngle.toDouble())).toFloat()
        val yValue = sin(toRadians(lightAngle.toDouble())).toFloat()
        val lightDirection = directionalLight.position
        lightDirection.x = 0f
        lightDirection.y = yValue
        lightDirection.z = zValue
        lightDirection.normalize()
        lightDirection.w = 0f
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