package eng

import org.tinylog.Logger
import java.io.IOException
import java.util.Properties

class EngineProperties {

    var ups: Int = DEFAULT_UPS
        private set

    init {
        var props = Properties()
        try {
            val stream = EngineProperties::class.java.getResourceAsStream("/$FILENAME")!!
            props.load(stream)
            ups = Integer.parseInt(props.getOrDefault("ups", DEFAULT_UPS).toString())
        } catch (e: IOException) {
            Logger.error("Could not read [{}] properties file", FILENAME, e)
        }
    }

    companion object {
        private final val DEFAULT_UPS: Int = 30
        private final val FILENAME = "eng.properties"
        final val instance: EngineProperties = EngineProperties()
    }
}