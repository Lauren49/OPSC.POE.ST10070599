package com.example.birdwatching

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class Markers : AppCompatActivity() {

    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var database: DatabaseReference // Database reference for Firebase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_markers)

        // Initialize the FusedLocationProviderClient to get the device's current location
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        database = FirebaseDatabase.getInstance().getReference("Markers") // Initialize Firebase reference


        val fab: FloatingActionButton = findViewById(R.id.fab_add_marker)
        fab.setOnClickListener {
            showAddMarkerDialog()
        }

        // Obtain the SupportMapFragment and get notified when the map is ready
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_marker) as SupportMapFragment
        mapFragment.getMapAsync { map ->
            // Add a marker and move the camera once the map is ready
            googleMap = map
            // Check location permission
            enableMyLocation()
            loadMarkersFromFirebase() // Load saved markers from Firebase
        }

    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            // Permission is granted, enable location layer on the map
            googleMap.isMyLocationEnabled = true

            // Get the last known location and move the camera
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val currentLatLng = LatLng(it.latitude, it.longitude)
                    googleMap.addMarker(MarkerOptions().position(currentLatLng).title("You are here"))
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                }
            }
        } else {
            // Request location permission if not granted
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                Map.LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun showAddMarkerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_marker, null)
        val builder = AlertDialog.Builder(this)
            .setTitle("Add Birding Spot")
            .setView(dialogView)
            .setPositiveButton("Add") { dialog, _ ->
                val locationName = dialogView.findViewById<EditText>(R.id.et_location_name).text.toString()
                val birdName = dialogView.findViewById<EditText>(R.id.et_bird_name).text.toString()
                val birdDescription = dialogView.findViewById<EditText>(R.id.et_bird_description).text.toString()

                getUserCurrentLocation { userLatLng ->
                googleMap.addMarker(
                    MarkerOptions()
                        .position(userLatLng)
                        .title("$locationName - $birdName")
                        .snippet(birdDescription)
                )

                    // Create BirdMarker object
                    val birdMarker = BirdMarker(
                        locationName = locationName,
                        birdName = birdName,
                        birdDescription = birdDescription,
                        latitude = userLatLng.latitude,
                        longitude = userLatLng.longitude
                    )

                    // Save to Firebase Realtime Database
                    saveMarkerToFirebase(locationName, birdName, birdDescription, userLatLng)
            }

                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }

        builder.create().show()
    }

    private fun getUserCurrentLocation(callback: (LatLng) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            // Fetch the last known location
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    callback(currentLatLng)
                } else {
                    // Provide a default location or handle null appropriately
                    callback(LatLng(-25.7461, 28.2379)) // Example fallback
                }
            }
        } else {
            // Request location permission if not granted
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                Map.LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun loadMarkersFromFirebase() {
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (markerSnapshot in dataSnapshot.children) {
                    val markerData = markerSnapshot.getValue(MarkerData::class.java)
                    markerData?.let {
                        val latLng = LatLng(it.latitude, it.longitude)
                        googleMap.addMarker(
                            MarkerOptions().position(latLng)
                                .title(it.locationName + " - " + it.birdName)
                                .snippet(it.birdDescription)
                        )
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Handle error if needed
            }
        })
    }

    private fun saveMarkerToFirebase(locationName: String, birdName: String, birdDescription: String, latLng: LatLng) {
        val markerId = database.push().key // Generate a unique ID for each marker
        val markerData = MarkerData(locationName, birdName, birdDescription, latLng.latitude, latLng.longitude)
        markerId?.let { database.child(it).setValue(markerData) }
    }

    data class MarkerData(
        val locationName: String = "",
        val birdName: String = "",
        val birdDescription: String = "",
        val latitude: Double = 0.0,
        val longitude: Double = 0.0
    )

}