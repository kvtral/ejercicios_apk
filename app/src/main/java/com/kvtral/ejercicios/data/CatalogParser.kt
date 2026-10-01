package com.kvtral.ejercicios.data

import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

fun parseCatalog(text: String): Catalog = AppJson.decodeFromString(Catalog.serializer(), text)
