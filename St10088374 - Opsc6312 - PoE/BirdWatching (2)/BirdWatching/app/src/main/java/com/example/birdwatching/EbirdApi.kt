package com.example.birdwatching

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface EbirdApi {
    @GET("v2/ref/hotspot/geo")
    fun getHotspots(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Header("X-eBirdApiToken") apiKey: String,
        @Query("dist") dist: Int = 450,  // Optional: search radius in km (default 25km)
        @Query("back") back: Int = 30,  // Optional: only hotspots visited up to 'back' days ago
        @Query("fmt") fmt: String = "json"
    ): Call<List<Hotspot>>  // The response will be a list of Hotspot objects
}