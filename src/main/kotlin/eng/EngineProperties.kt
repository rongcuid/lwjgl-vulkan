package eng

import org.tinylog.Logger
import java.io.IOException
import java.util.*

class EngineProperties {

    var ups: Int = DEFAULT_UPS
    var validate: Boolean = false
    var physDeviceName: String? = null

    init {
        val props = Properties()
        try {
            val stream = EngineProperties::class.java.getResourceAsStream("/$FILENAME")!!
            props.load(stream)
            ups = props.getOrDefault("ups", DEFAULT_UPS).toString().toInt()
            validate = props.getOrDefault("vkValidate", false).toString().toBoolean()
            physDeviceName = props.getProperty("physDeviceName")
        } catch (e: IOException) {
            Logger.error("Could not read [{}] properties file", FILENAME, e)
        }
    }

    companion object {
        private const val DEFAULT_UPS: Int = 30
        private const val FILENAME = "eng.properties"
        val instance: EngineProperties = EngineProperties()
    }
}