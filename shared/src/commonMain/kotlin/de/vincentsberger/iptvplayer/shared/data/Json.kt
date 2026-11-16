package de.vincentsberger.iptvplayer.shared.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun parseJson(text: String): JsonElement? = try {
    lenientJson.parseToJsonElement(text)
} catch (e: Exception) {
    null
}

/** Text eines Feldes – egal ob der Server Zahl oder Text liefert; null wird zu "". */
internal fun JsonObject.str(key: String): String {
    val v = this[key] ?: return ""
    if (v is JsonNull) return ""
    return (v as? JsonPrimitive)?.content ?: ""
}

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
internal fun JsonElement?.objects(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
