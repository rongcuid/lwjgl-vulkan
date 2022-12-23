package eng.scene

import org.joml.*

class Entity(id: String, modelId: String, position: Vector3f) {
    val id: String
    val modelId: String
    val modelMatrix: Matrix4f
    val position: Vector3f
    val rotation: Quaternionf
    val scale: Float

    init {
        this.id = id
        this.modelId = modelId
        this.position = position
        scale = 1f
        rotation = Quaternionf()
        modelMatrix = Matrix4f()
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