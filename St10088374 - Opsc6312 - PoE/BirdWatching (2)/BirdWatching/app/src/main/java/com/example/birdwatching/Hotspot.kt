package com.example.birdwatching

data class Hotspot(
    val locId: String,     // Unique identifier for the location
    val name: String,      // Name of the hotspot
    val latitude: Double,   // Latitude of the hotspot
    val longitude: Double   // Longitude of the hotspot
)
