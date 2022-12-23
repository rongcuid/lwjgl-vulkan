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
        } catch (e: IOException) {
            Logger.error("Could not read [{}] properties file", FILENAME, e)
        }
    }

    companion object {
        private const val DEFAULT_REQUESTED_IMAGES = 3
        private const val DEFAULT_UPS: Int = 30
        private const val FILENAME = "eng.properties"
        val instance: EngineProperties = EngineProperties()
    }
}