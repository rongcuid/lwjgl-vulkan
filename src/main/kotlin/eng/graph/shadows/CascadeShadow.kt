package eng.graph.shadows

import eng.graph.vk.GraphConstants
import eng.scene.Scene
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import java.lang.Math.pow
import kotlin.math.ceil


class CascadeShadow {
    var projViewMatrix = Matrix4f()
    var splitDistance: Float = 0f

    companion object {
        fun updateCascadeShadows(cascadeShadows: List<CascadeShadow>, scene: Scene) {
            val viewMatrix = scene.camera.viewMatrix
            val projMatrix = scene.projection.projectionMatrix
            val lightPos = scene.directionalLight!!.position

            val cascadeSplitLambda = 0.95f

            val cascadeSplits = FloatArray(GraphConstants.SHADOW_MAP_CASCADE_COUNT)

            val nearClip = projMatrix.perspectiveNear()
            val farClip = projMatrix.perspectiveFar()
            val clipRange = farClip - nearClip

            val minZ = nearClip
            val maxZ = nearClip + clipRange

            val range = maxZ - minZ
            val ratio = maxZ / minZ

            // Calculate split depths
            for (i in 0 until GraphConstants.SHADOW_MAP_CASCADE_COUNT) {
                val p = (i + 1) / GraphConstants.SHADOW_MAP_CASCADE_COUNT.toFloat()
                val log = (minZ * pow(ratio.toDouble(), p.toDouble())).toFloat()
                val uniform = minZ + range * p;
                val d = cascadeSplitLambda * (log - uniform) + uniform;
                cascadeSplits[i] = (d - nearClip) / clipRange
            }
            // Calculate orthographic projection matrix for each cascade
            var lastSplitDist = 0.0f
            for (i in 0 until GraphConstants.SHADOW_MAP_CASCADE_COUNT) {
                val splitDist = cascadeSplits[i]

                val frustumCorners = arrayOf(
                    Vector3f(-1.0f, 1.0f, -1.0f),
                    Vector3f(1.0f, 1.0f, -1.0f),
                    Vector3f(1.0f, -1.0f, -1.0f),
                    Vector3f(-1.0f, -1.0f, -1.0f),
                    Vector3f(-1.0f, 1.0f, 1.0f),
                    Vector3f(1.0f, 1.0f, 1.0f),
                    Vector3f(1.0f, -1.0f, 1.0f),
                    Vector3f(-1.0f, -1.0f, 1.0f)
                )
                // Project frustum corners into world space
                val invCam: Matrix4f = Matrix4f(projMatrix).mul(viewMatrix).invert()
                for (j in 0..7) {
                    val invCorner = Vector4f(frustumCorners[j], 1.0f).mul(invCam)
                    frustumCorners[j] =
                        Vector3f(invCorner.x / invCorner.w, invCorner.y / invCorner.w, invCorner.z / invCorner.w)
                }
                for (j in 0 until 4) {
                    val dist = Vector3f(frustumCorners[j+4]).sub(frustumCorners[j])
                    frustumCorners[j + 4] = Vector3f(frustumCorners[j]).add(Vector3f(dist).mul(splitDist))
                    frustumCorners[j] = Vector3f(frustumCorners[j]).add(Vector3f(dist).mul(lastSplitDist))
                }
                // Get frustum center
                val frustumCenter = Vector3f(0f)
                for (j in 0 until 8) {
                    frustumCenter.add(frustumCorners[j])
                }
                frustumCenter.div(8f)

                var radius = 0f
                for (j in 0 until 8) {
                    val distance = Vector3f(frustumCorners[j]).sub(frustumCenter).length()
                    radius = Math.max(radius, distance)
                }
                radius = (ceil(radius * 16f) / 16f)

                val maxExtents = Vector3f(radius)
                val minExtents = Vector3f(maxExtents).mul(-1f)

                val lightDir = Vector3f(lightPos.x, lightPos.y, lightPos.z).mul(-1f).normalize()
                val eye = Vector3f(frustumCenter).sub(Vector3f(lightDir).mul(-minExtents.z))
                val up = Vector3f(0.0f, 1.0f, 0.0f)
                val lightViewMatrix = Matrix4f().lookAt(eye, frustumCenter, up)
                val lightOrthoMatrix = Matrix4f().ortho(
                    minExtents.x,
                    maxExtents.x,
                    minExtents.y,
                    maxExtents.y,
                    0.0f,
                    maxExtents.z - minExtents.z,
                    true
                )

                // Store split distance and matrix in cascade

                // Store split distance and matrix in cascade
                val cascadeShadow = cascadeShadows[i]
                cascadeShadow.splitDistance = (nearClip + splitDist * clipRange) * -1.0f
                cascadeShadow.projViewMatrix = lightOrthoMatrix.mul(lightViewMatrix)

                lastSplitDist = cascadeSplits[i]
            }
        }
    }
}