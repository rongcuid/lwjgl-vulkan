package eng.scene

import eng.EngineProperties
import org.joml.Matrix4f

class Projection {
    val projectionMatrix: Matrix4f

    init {
        projectionMatrix = Matrix4f()
    }

    fun resize(width: Int, height: Int) {
        val engProps = EngineProperties.instance
        projectionMatrix.identity()
        projectionMatrix.perspective(engProps.fov, width.toFloat() / height.toFloat(),
            engProps.zNear, engProps.zFar, true)
    }
}