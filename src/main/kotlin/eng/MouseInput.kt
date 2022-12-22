package eng

import org.joml.Vector2f
import org.lwjgl.glfw.GLFW.*

class MouseInput(windowHandle: Long) {
    var currentPos: Vector2f private set
    var displVec: Vector2f private set
    private var inWindow: Boolean
    var leftButtonPressed: Boolean private set
    private var previousPos: Vector2f
    var rightButtonPressed: Boolean private set

    init {
        previousPos = Vector2f(-1.0f, -1.0f)
        currentPos = Vector2f()
        displVec = Vector2f()
        leftButtonPressed = false
        rightButtonPressed = false
        inWindow = false

        glfwSetCursorPosCallback(windowHandle) { handle, xpos, ypos ->
            currentPos.x = xpos.toFloat()
            currentPos.y = ypos.toFloat()
        }
        glfwSetCursorEnterCallback(windowHandle) { handle, entered ->
            inWindow = entered
        }
        glfwSetMouseButtonCallback(windowHandle) { handle, button, action, mode ->
            leftButtonPressed = button == GLFW_MOUSE_BUTTON_1 && action == GLFW_PRESS
            rightButtonPressed = button == GLFW_MOUSE_BUTTON_2 && action == GLFW_PRESS
        }
    }

    fun input() {
        displVec.x = 0f
        displVec.y = 0f
        if (previousPos.x > 0 && previousPos.y > 0 && inWindow) {
            val deltax = (currentPos.x - previousPos.x).toDouble()
            val deltay = (currentPos.y - previousPos.y).toDouble()
            val rotateX = deltax != 0.0
            val rotateY = deltay != 0.0
            if (rotateX) {
                displVec.y = deltax.toFloat()
            }
            if (rotateY) {
                displVec.x = deltay.toFloat()
            }
        }
        previousPos.x = currentPos.x
        previousPos.y = currentPos.y
    }
}