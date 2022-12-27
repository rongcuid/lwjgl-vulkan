package eng.scene

import org.joml.Vector4f

class ModelData(val modelId: String, val meshDataList: List<MeshData>, val materialList: List<Material>) {

    data class MeshData(
        val positions: FloatArray,
        val normals: FloatArray, val tangents: FloatArray, val biTangents: FloatArray,
        val textCoords: FloatArray?, val indices: IntArray,
        val materialIdx: Int
    )

    data class Material(
        val texturePath: String? = null, val normalMapPath: String? = null, val metalRoughMap: String? = null,
        val diffuseColor: Vector4f = DEFAULT_COLOR,
        val roughnessFactor: Float = 0f, val metallicFactor: Float = 0f
    ) {
        companion object {
            val DEFAULT_COLOR = Vector4f(1f, 1f, 1f, 1f)
        }
    }
}