// MapUnitsHelper.kt
package com.example.birdwatching

import android.content.Context
import android.content.SharedPreferences

class MapUnitConverter(context: Context) {

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("MapPreferences", Context.MODE_PRIVATE)

    companion object {
        const val METRIC = "metric"
        const val IMPERIAL = "imperial"
        private const val UNIT_SYSTEM_KEY = "unit_system"
    }

    // Set the unit system (metric or imperial)
    fun setUnitSystem(unitSystem: String) {
        sharedPrefs.edit().putString(UNIT_SYSTEM_KEY, unitSystem).apply()
    }

    // Get the current unit system
    fun getUnitSystem(): String {
        return sharedPrefs.getString(UNIT_SYSTEM_KEY, METRIC) ?: METRIC
    }

    // Convert distance to the preferred unit system and format it
    fun formatDistance(distanceInMeters: Double): String {
        return if (getUnitSystem() == METRIC) {
            // Convert to kilometers and format
            val distanceInKm = distanceInMeters / 1000
            String.format("%.2f km", distanceInKm)
        } else {
            // Convert to miles and format
            val distanceInMiles = distanceInMeters / 1609.34
            String.format("%.2f mi", distanceInMiles)
        }
    }
}
