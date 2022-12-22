package eng

import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported
import org.lwjgl.glfw.*
import org.lwjgl.glfw.Callbacks.*
import org.lwjgl.system.MemoryUtil
import java.lang.RuntimeException

class Window(title: String, keyCallback: GLFWKeyCallbackI? = null) {
    var height: Int private set
    var width: Int private set
    private val mouseInput: MouseInput
    var resized: Boolean
    var windowHandle: Long private set

    init {
        if (!glfwInit()) {
            throw IllegalStateException("Unable to initialize GLFW")
        }
        if (!glfwVulkanSupported()) {
            throw IllegalStateException("Cannot find a compatible Vulkan installable client driver (ICD)")
        }
//        val vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor())
//            ?: throw IllegalStateException("Cannot get video mode of primary monitor")
//        width = vidMode.width()
//        height = vidMode.height()
        width = 1280
        height = 720

        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_MAXIMIZED, GLFW_FALSE)

        // Create the window
        windowHandle = glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL)
        if (windowHandle == MemoryUtil.NULL) {
            throw RuntimeException("Failed to create a GLFW window")
        }
        glfwSetFramebufferSizeCallback(windowHandle) { window, w, h -> resize(w, h) }
        glfwSetKeyCallback(windowHandle) { window, key, scancode, action, mods ->
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window, true)
            }
            keyCallback?.invoke(window, key, scancode, action, mods)
        }
        mouseInput = MouseInput(windowHandle)
        resized = false
    }

    fun cleanup() {
        glfwFreeCallbacks(windowHandle)
        glfwDestroyWindow(windowHandle)
        glfwTerminate()
    }

    fun isKeyPressed(keycode: Int): Boolean {
        return glfwGetKey(windowHandle, keycode) == GLFW_PRESS
    }

    fun resize(width: Int, height: Int) {
        resized = true
        this.width = width
        this.height = height
    }

    fun shouldClose(): Boolean {
        return glfwWindowShouldClose(windowHandle)
    }

    fun pollEvents() {
        glfwPollEvents()
        mouseInput.input()
    }
}