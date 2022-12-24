package eng.graph

import eng.EngineProperties
import eng.graph.vk.Device
import eng.graph.vk.Texture

class TextureCache {
    val textureMap = IndexedLinkedHashMap<String, Texture>()
    fun createTexture(device: Device, texturePath: String?, format: Int): Texture {
        var path = texturePath
        if (texturePath == null || texturePath.trim().isEmpty()) {
            val engProperties = EngineProperties.instance
            path = engProperties.defaultTexturePath
        }
        var texture = textureMap[path!!]
        if (texture == null) {
            texture = Texture(device, path, format)
            textureMap.put(path, texture)
        }
        return texture
    }
    fun cleanup() {
        textureMap.forEach { k, v -> v.cleanup() }
        textureMap.clear()
    }

    fun getAsList(): List<Texture> {
        return ArrayList(textureMap.values)
    }

    fun getPosition(texturePath: String): Int {
        return textureMap.getIndexOf(texturePath)
    }

    fun getTexture(texturePath: String): Texture? {
        return textureMap.get(texturePath.trim())
    }
}