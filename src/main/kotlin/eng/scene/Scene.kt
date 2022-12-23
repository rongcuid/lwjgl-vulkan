package eng.scene

import eng.Window

class Scene(window: Window) {
    val entitiesMap: MutableMap<String, MutableList<Entity>>
    val projection: Projection

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
        val entities = entitiesMap.get(entity.modelId)
        entities?.removeIf{it.id == entity.id}
    }
}