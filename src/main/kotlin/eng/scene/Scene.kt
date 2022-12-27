package eng.scene

import eng.Window
import eng.graph.vk.GraphConstants
import org.joml.Vector4f

class Scene(window: Window) {
    val entitiesMap: MutableMap<String, MutableList<Entity>>
    val projection: Projection
    val camera = Camera()
    val ambientLight = Vector4f()
    var lights: Array<Light>? = null
        set(lights) {
            val numLights = lights?.size ?: 0
            if (numLights > GraphConstants.MAX_LIGHTS) {
                throw RuntimeException("Maximum number of lights set to: ${GraphConstants.MAX_LIGHTS}")
            }
            field = lights
        }

    init {
        entitiesMap = HashMap()
        projection = Projection()
        projection.resize(window.width, window.height)
    }
    fun addEntity(entity: Entity) {
        var entities = entitiesMap.get(entity.modelId)
        if (entities == null) {
            entities = ArrayList()
            entitiesMap.put(entity.modelId, entities)
        }
        entities.add(entity)
    }

    fun getEntitiesByModelId(modelId: String): List<Entity>? {
        return entitiesMap[modelId]
    }
    fun removeAllEntities() {
        entitiesMap.clear()
    }
    fun removeEntity(entity: Entity) {
        val entities = entitiesMap[entity.modelId]
        entities?.removeIf{it.id == entity.id}
    }
}