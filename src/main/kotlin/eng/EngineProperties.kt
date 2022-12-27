package eng

import org.tinylog.Logger
import java.io.IOException
import java.util.*

class EngineProperties {

    var ups: Int = DEFAULT_UPS
    var validate: Boolean = false
    var physDeviceName: String? = null
    var vSync: Boolean = true
    var requestedImages: Int = DEFAULT_REQUESTED_IMAGES
    var shaderRecompilation: Boolean = false
    var fov: Float = DEFAULT_FOV
    var zFar: Float = DEFAULT_Z_FAR
    var zNear: Float = DEFAULT_Z_NEAR
    var defaultTexturePath: String? = null
    var maxMaterials: Int = DEFAULT_MAX_MATERIALS
    var shadowBias: Float = DEFAULT_SHADOW_BIAS
    var shadowDebug: Boolean = false
    var shadowMapSize: Int = DEFAULT_SHADOW_MAP_SIZE
    var shadowPcf: Boolean = false

    init {
        val props = Properties()
        try {
            val stream = EngineProperties::class.java.getResourceAsStream("/$FILENAME")!!
            props.load(stream)
            ups = props.getOrDefault("ups", DEFAULT_UPS).toString().toInt()
            validate = props.getOrDefault("vkValidate", false).toString().toBoolean()
            physDeviceName = props.getProperty("physDeviceName")
            requestedImages = props.getOrDefault("requestedImages", DEFAULT_REQUESTED_IMAGES).toString().toInt()
            vSync = props.getOrDefault("vsync", true).toString().toBoolean()
            shaderRecompilation = props.getOrDefault("shaderRecompilation", false).toString().toBoolean()
            fov = Math.toRadians(props.getOrDefault("fov", DEFAULT_FOV).toString().toDouble()).toFloat()
            zFar = props.getOrDefault("zFar", DEFAULT_Z_FAR).toString().toFloat()
            zNear = props.getOrDefault("zNear", DEFAULT_Z_NEAR).toString().toFloat()
            defaultTexturePath = props.getProperty("defaultTexturePath")
            maxMaterials = props.getOrDefault("maxMaterials", DEFAULT_MAX_MATERIALS).toString().toInt()
            shadowPcf = props.getOrDefault("shadowPcf", false).toString().toBoolean()
            shadowBias = props.getOrDefault("shadowMapSize", DEFAULT_SHADOW_BIAS).toString().toFloat()
            shadowMapSize = props.getOrDefault("shadowMapSize", DEFAULT_SHADOW_MAP_SIZE).toString().toInt()
            shadowDebug = props.getOrDefault("shadowDebug", false).toString().toBoolean()
        } catch (e: IOException) {
            Logger.error("Could not read [{}] properties file", FILENAME, e)
        }
    }

    companion object {
        private const val DEFAULT_REQUESTED_IMAGES = 3
        private const val DEFAULT_UPS: Int = 30
        private const val FILENAME = "eng.properties"
        private const val DEFAULT_FOV = 60f
        private const val DEFAULT_Z_FAR = 100f
        private const val DEFAULT_Z_NEAR = 1f
        private const val DEFAULT_MAX_MATERIALS = 500
        private const val DEFAULT_SHADOW_BIAS = 0.00005f
        private const val DEFAULT_SHADOW_MAP_SIZE = 2048
        val instance: EngineProperties = EngineProperties()
    }
}