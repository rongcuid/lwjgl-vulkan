package eng.scene

import org.joml.*

class Entity(id: String, modelId: String, position: Vector3f) {
    val id: String = id
    val modelId: String = modelId
    val modelMatrix: Matrix4f = Matrix4f()
    val position: Vector3f = position
    val rotation: Quaternionf = Quaternionf()
    var scale: Float = 1f
        set(value) {
            field = value
            updateModelMatrix()
        }

    init {
        updateModelMatrix()
    }

    fun setPosition(x: Float, y: Float, z: Float) {
        position.x = x
        position.y = y
        position.z = z
        updateModelMatrix()
    }

    fun updateModelMatrix() {
        modelMatrix.translationRotateScale(position, rotation, scale)
    }
}