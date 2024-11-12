package com.example.birdwatching

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class Map : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var googleMap: GoogleMap
    private lateinit var mapUnitConverter: MapUnitConverter
    private var currentPolyline: Polyline? = null  // Store the polyline so it can be updated
    private var currentLatLng: LatLng? = null  // Store current location as LatLng

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        // Initialize MapUnitConverter
        mapUnitConverter = MapUnitConverter(this)

        val toggleButton: Button = findViewById(R.id.btn_change_units)
        toggleButton.setOnClickListener {
            toggleUnitSystem()
        }


        // Initialize the FusedLocationProviderClient to get the device's current location
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)


        // Obtain the SupportMapFragment and get notified when the map is ready
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync { map ->
            // Add a marker and move the camera once the map is ready
            googleMap = map
            // Check location permission
            enableMyLocation()

            // Set a marker click listener to draw a line from current location to the tapped marker
            googleMap.setOnMarkerClickListener { marker ->
                drawLineToMarker(marker.position)
                false  // Return false to allow the default behavior (e.g., info window display)
            }
        }

    }


    private fun toggleUnitSystem() {
        val currentSystem = mapUnitConverter.getUnitSystem()
        val newSystem = if (currentSystem == MapUnitConverter.METRIC) MapUnitConverter.IMPERIAL else MapUnitConverter.METRIC
        mapUnitConverter.setUnitSystem(newSystem)

        // Notify user
        Toast.makeText(this, "Unit system changed to ${mapUnitConverter.getUnitSystem()}", Toast.LENGTH_SHORT).show()
    }

    private fun displayDistanceOnMap(distanceInMeters: Double) {
        // Format the distance based on the current unit system
        val formattedDistance = mapUnitConverter.formatDistance(distanceInMeters)

        // Display the formatted distance as a toast message
        Toast.makeText(this, "Distance: $formattedDistance", Toast.LENGTH_SHORT).show()

        // Optional: Add a temporary marker at the midpoint of the line to show the distance
        currentLatLng?.let { startLatLng ->
            // Calculate the midpoint for placing the distance info
            val midLat = (startLatLng.latitude + currentPolyline?.points?.last()?.latitude!!) / 2
            val midLng = (startLatLng.longitude + currentPolyline?.points?.last()?.longitude!!) / 2
            val midpoint = LatLng(midLat, midLng)

            // Add a marker at the midpoint displaying the distance (you could also use an info window)
            val distanceMarker = googleMap.addMarker(
                MarkerOptions()
                    .position(midpoint)
                    .title("Distance")
                    .snippet(formattedDistance)
            )

            // Remove the marker after a few seconds
            distanceMarker?.showInfoWindow()
            googleMap.setOnInfoWindowCloseListener {
                if (distanceMarker != null) {
                    distanceMarker.remove()
                }
            }
        }
    }


    private fun drawLineToMarker(destinationLatLng: LatLng) {
        // Clear any previous polyline
        currentPolyline?.remove()

        // Check if the current location is available
        currentLatLng?.let { startLatLng ->
            val polylineOptions = PolylineOptions()
                .add(startLatLng)
                .add(destinationLatLng)
                .color(ContextCompat.getColor(this, R.color.blue))
                .width(5f)  // Width of the polyline

            // Add the polyline to the map and save it to `currentPolyline`
            currentPolyline = googleMap.addPolyline(polylineOptions)

            // Calculate and display distance (optional)
            val distance = FloatArray(1)
            Location.distanceBetween(
                startLatLng.latitude, startLatLng.longitude,
                destinationLatLng.latitude, destinationLatLng.longitude,
                distance
            )
            displayDistanceOnMap(distance[0].toDouble())
        } ?: Log.e("Map", "Current location is null, cannot draw line.")
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            // Permission is granted, enable location layer on the map
            googleMap.isMyLocationEnabled = true

            // Get the last known location and move the camera
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    currentLatLng = LatLng(it.latitude, it.longitude)
                    googleMap.addMarker(MarkerOptions().position(currentLatLng!!).title("You are here"))
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng!!, 15f))

                    fetchHotspots(it.latitude, it.longitude)
                }
            }
        } else {
            // Request location permission if not granted
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    private fun fetchHotspots(lat: Double, lng: Double) {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.ebird.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(EbirdApi::class.java)
        val apiKey = getString(R.string.my_ebird_api_key)

        Log.d("Map", "API Key: $apiKey") // Log the API key for debugging purposes (ensure to remove it in production)


        service.getHotspots(lat, lng, apiKey, dist = 450, back = 25, fmt = "json").enqueue(object :
            Callback<List<Hotspot>> {
            override fun onResponse(call: Call<List<Hotspot>>, response: Response<List<Hotspot>>) {
                Log.d("Map", "Response received: ${response.code()}")


                if (response.isSuccessful) {
                    Log.d("Map", "Hotspots fetched successfully: ${response.body()?.size} hotspots found.")
                    response.body()?.forEach { hotspot ->
                        val hotspotLatLng = LatLng(hotspot.latitude, hotspot.longitude)
                        Log.d("Map", "Adding marker for hotspot: ${hotspot.name} at ($hotspot.latitude, $hotspot.longitude)")
                        googleMap.addMarker(MarkerOptions().position(hotspotLatLng).title(hotspot.name))
                    }
                } else {
                    Log.e("Map", "Failed to load hotspots: ${response.message()}")
                    Toast.makeText(this@Map, "Failed to load hotspots", Toast.LENGTH_SHORT).show()
                }

                //bird hotspots

                val rietvleiNRLatLng = LatLng(-25.9061, 28.3180)
                googleMap.addMarker(MarkerOptions().position(rietvleiNRLatLng).title("Rietvlei Nature Reserve"))

                val marievaleBSLatLng = LatLng(-26.2886, 28.5210)
                googleMap.addMarker(MarkerOptions().position(marievaleBSLatLng).title("Marievale Bird Sanctuary"))

                val walterSisuluBGLatLng = LatLng(-26.0711, 27.9157)
                googleMap.addMarker(MarkerOptions().position(walterSisuluBGLatLng).title("Walter Sisulu National Botanical Garden"))

                val orTamboAirportLatLng = LatLng(-26.1372, 28.2469)
                googleMap.addMarker(MarkerOptions().position(orTamboAirportLatLng).title("OR Tambo International Airport"))

                val suikerbosrandNRLatLng = LatLng(-26.2621, 28.2002)
                googleMap.addMarker(MarkerOptions().position(suikerbosrandNRLatLng).title("Suikerbosrand NR"))

                val deltaParkLatLng = LatLng(-26.1663, 27.9896)
                googleMap.addMarker(MarkerOptions().position(deltaParkLatLng).title("Delta Park"))

                val eagleCanyonGolfEstateLatLng = LatLng(-26.0510, 27.8624)
                googleMap.addMarker(MarkerOptions().position(eagleCanyonGolfEstateLatLng).title("Eagle Canyon Golf Estate"))

                val sunrockGuesthouseLatLng = LatLng(-26.0984, 28.2236)
                googleMap.addMarker(MarkerOptions().position(sunrockGuesthouseLatLng).title("Sunrock Guesthouse"))

                val austinRobertsBSLatLng = LatLng(-25.7793, 28.2130)
                googleMap.addMarker(MarkerOptions().position(austinRobertsBSLatLng).title("Austin Roberts Bird Sanctuary"))

                val dinokengGRLatLng = LatLng(-25.2617, 28.6600)
                googleMap.addMarker(MarkerOptions().position(dinokengGRLatLng).title("Dinokeng GR"))

                val meyersFarmLatLng = LatLng(-26.1301, 27.8838)
                googleMap.addMarker(MarkerOptions().position(meyersFarmLatLng).title("Meyer's Farm"))

                val kemptonParkLatLng = LatLng(-26.1103, 28.2469)
                googleMap.addMarker(MarkerOptions().position(kemptonParkLatLng).title("Kempton Park"))

                val goldenHarvestParkLatLng = LatLng(-26.0677, 27.9911)
                googleMap.addMarker(MarkerOptions().position(goldenHarvestParkLatLng).title("Golden Harvest Park"))

                val johannesburgBotanicalGardensLatLng = LatLng(-26.1713, 28.0265)
                googleMap.addMarker(MarkerOptions().position(johannesburgBotanicalGardensLatLng).title("Johannesburg Botanical Gardens"))

                val roodeplaatDamNRLatLng = LatLng(-25.6402, 28.4689)
                googleMap.addMarker(MarkerOptions().position(roodeplaatDamNRLatLng).title("Roodeplaat Dam Nature Reserve"))

                val korsmanBirdSanctuaryLatLng = LatLng(-26.0971, 28.1701)
                googleMap.addMarker(MarkerOptions().position(korsmanBirdSanctuaryLatLng).title("Korsman Bird Sanctuary"))

                val universityOfPretoriaMainCampusLatLng = LatLng(-25.7460, 28.1890)
                googleMap.addMarker(MarkerOptions().position(universityOfPretoriaMainCampusLatLng).title("University of Pretoria - Main Campus"))

                val diepslootNRLatLng = LatLng(-25.9990, 27.9866)
                googleMap.addMarker(MarkerOptions().position(diepslootNRLatLng).title("Diepsloot Nature Reserve"))

                val kruinParkLatLng = LatLng(-26.0973, 27.9479)
                googleMap.addMarker(MarkerOptions().position(kruinParkLatLng).title("Kruin Park"))

                val bullfrogDamLatLng = LatLng(-26.0480, 27.9556)
                googleMap.addMarker(MarkerOptions().position(bullfrogDamLatLng).title("Bullfrog Dam"))

                val suikerbosrandNRNorthLatLng = LatLng(-26.2581, 28.2071)
                googleMap.addMarker(MarkerOptions().position(suikerbosrandNRNorthLatLng).title("Suikerbosrand NR - Northeast Corner"))

                val suikerbosrandNRVisitorsCenterLatLng = LatLng(-26.2486, 28.2088)
                googleMap.addMarker(MarkerOptions().position(suikerbosrandNRVisitorsCenterLatLng).title("Suikerbosrand NR - Visitors' Center & Picnic Area"))

                val beaulieuBirdSanctuaryLatLng = LatLng(-26.0368, 28.0680)
                googleMap.addMarker(MarkerOptions().position(beaulieuBirdSanctuaryLatLng).title("Beaulieu Bird Sanctuary"))

                val pretoriaNationalBGLatLng = LatLng(-25.7496, 28.2331)
                googleMap.addMarker(MarkerOptions().position(pretoriaNationalBGLatLng).title("Pretoria National Botanical Garden"))

                val ezemveloNRLatLng = LatLng(-26.1372, 28.1747)
                googleMap.addMarker(MarkerOptions().position(ezemveloNRLatLng).title("Ezemvelo Nature Reserve"))

                val faerieGlenNRLatLng = LatLng(-25.7943, 28.2974)
                googleMap.addMarker(MarkerOptions().position(faerieGlenNRLatLng).title("Faerie Glen Nature Reserve"))

                val kloofendalMunicipalNRLatLng = LatLng(-26.1091, 27.9723)
                googleMap.addMarker(MarkerOptions().position(kloofendalMunicipalNRLatLng).title("Kloofendal Municipal Nature Reserve"))

                val devonGrasslandsIBALatLng = LatLng(-26.2041, 27.8584)
                googleMap.addMarker(MarkerOptions().position(devonGrasslandsIBALatLng).title("Devon Grasslands IBA"))

                val groenkloofNRLatLng = LatLng(-25.7461, 28.2379)
                googleMap.addMarker(MarkerOptions().position(groenkloofNRLatLng).title("Groenkloof NR"))

                val lakesParkLatLng = LatLng(-26.1663, 27.9790)
                googleMap.addMarker(MarkerOptions().position(lakesParkLatLng).title("The Lakes Park"))

                val klipriviersbergNRLatLng = LatLng(-26.2837, 27.9731)
                googleMap.addMarker(MarkerOptions().position(klipriviersbergNRLatLng).title("Klipriviersberg Nature Reserve"))

                val suikerbosrandNRNorthEntranceLatLng = LatLng(-26.2468, 28.1933)
                googleMap.addMarker(MarkerOptions().position(suikerbosrandNRNorthEntranceLatLng).title("Suikerbosrand NR - North Entrance Gate"))

                val panoramaCemeteryLatLng = LatLng(-26.2017, 28.0495)
                googleMap.addMarker(MarkerOptions().position(panoramaCemeteryLatLng).title("Panorama Cemetery, Paul Kruger Rd"))

                val bonaeroPanLatLng = LatLng(-26.0898, 28.2164)
                googleMap.addMarker(MarkerOptions().position(bonaeroPanLatLng).title("Bonaero Pan"))

                val wilgeRiverValleyUpperLatLng = LatLng(-26.0695, 28.1600)
                googleMap.addMarker(MarkerOptions().position(wilgeRiverValleyUpperLatLng).title("Wilge River Valley (Upper)"))

                val thokozaWetlandLatLng = LatLng(-26.2522, 28.2247)
                googleMap.addMarker(MarkerOptions().position(thokozaWetlandLatLng).title("Thokoza Wetland"))

                val jukskeiRiverLatLng = LatLng(-26.0075, 28.1173)
                googleMap.addMarker(MarkerOptions().position(jukskeiRiverLatLng).title("Jukskei River"))

                val marievaleBSHadedaHideLatLng = LatLng(-26.2942, 28.5098)
                googleMap.addMarker(MarkerOptions().position(marievaleBSHadedaHideLatLng).title("Marievale Bird Sanctuary - Hadeda Hide"))

                val deTweedespruitConservancyLatLng = LatLng(-26.0585, 28.0726)
                googleMap.addMarker(MarkerOptions().position(deTweedespruitConservancyLatLng).title("De Tweedespruit Conservancy"))

                val rondebultBSLatLng = LatLng(-26.2941, 28.5194)
                googleMap.addMarker(MarkerOptions().position(rondebultBSLatLng).title("Rondebult Bird Sanctuary"))

                val walkhavenDogParkLatLng = LatLng(-26.0160, 27.9130)
                googleMap.addMarker(MarkerOptions().position(walkhavenDogParkLatLng).title("Walkhaven Dog Park"))

                val glenAustinBirdSanctuaryLatLng = LatLng(-26.0495, 28.1647)
                googleMap.addMarker(MarkerOptions().position(glenAustinBirdSanctuaryLatLng).title("Glen Austin Bird Sanctuary"))

                val moreletaKloofNatureReserveLatLng = LatLng(-25.8388, 28.2672)
                googleMap.addMarker(MarkerOptions().position(moreletaKloofNatureReserveLatLng).title("Moreleta Kloof Nature Reserve"))

                val bronkhorstspruitDamLatLng = LatLng(-25.9657, 28.6877)
                googleMap.addMarker(MarkerOptions().position(bronkhorstspruitDamLatLng).title("Bronkhorstspruit Dam"))

                val brackendownsLatLng = LatLng(-26.2675, 28.0283)
                googleMap.addMarker(MarkerOptions().position(brackendownsLatLng).title("Brackendowns"))

                val strubendamBirdSanctuaryLatLng = LatLng(-26.1808, 27.9585)
                googleMap.addMarker(MarkerOptions().position(strubendamBirdSanctuaryLatLng).title("Strubendam Bird Sanctuary"))

                val modderfonteinReserveLatLng = LatLng(-26.1175, 28.1386)
                googleMap.addMarker(MarkerOptions().position(modderfonteinReserveLatLng).title("Modderfontein Reserve"))

                val suikerbosrandNRidgeTopDriveLatLng = LatLng(-26.2581, 28.1830)
                googleMap.addMarker(MarkerOptions().position(suikerbosrandNRidgeTopDriveLatLng).title("Suikerbosrand NR - Ridge Top Drive"))

                val presidentRidgeBirdSanctuaryLatLng = LatLng(-26.1234, 28.0366)
                googleMap.addMarker(MarkerOptions().position(presidentRidgeBirdSanctuaryLatLng).title("President Ridge Bird Sanctuary"))

                val rooiwalWTPLatLng = LatLng(-25.7048, 28.2515)
                googleMap.addMarker(MarkerOptions().position(rooiwalWTPLatLng).title("Rooiwal WTP"))

                val airportGameLodgeLatLng = LatLng(-26.1243, 28.1996)
                googleMap.addMarker(MarkerOptions().position(airportGameLodgeLatLng).title("Airport Game Lodge"))

                val rietvleiNatureReserveDuplicateLatLng = LatLng(-25.9061, 28.3180)
                googleMap.addMarker(MarkerOptions().position(rietvleiNatureReserveDuplicateLatLng).title("Rietvlei Nature Reserve (Duplicate)"))

                val seringveldConservancyLatLng = LatLng(-25.7860, 28.2057)
                googleMap.addMarker(MarkerOptions().position(seringveldConservancyLatLng).title("Seringveld Conservancy"))

                val suikerbosrandNRHolhoekPicnicAreaLatLng = LatLng(-26.2708, 28.2042)
                googleMap.addMarker(MarkerOptions().position(suikerbosrandNRHolhoekPicnicAreaLatLng).title("Suikerbosrand NR - Holhoek Picnic Area"))

                val randomHarvestNurseryLatLng = LatLng(-26.0515, 28.0288)
                googleMap.addMarker(MarkerOptions().position(randomHarvestNurseryLatLng).title("Random Harvest Nursery"))

                val lonehillParkLatLng = LatLng(-26.0185, 28.0408)
                googleMap.addMarker(MarkerOptions().position(lonehillParkLatLng).title("Lonehill Park and Municipal Nature Reserve"))

                val buffelsdriftConservancyLatLng = LatLng(-26.0106, 28.1370)
                googleMap.addMarker(MarkerOptions().position(buffelsdriftConservancyLatLng).title("Buffelsdrift Conservancy"))

                val marievaleBirdSanctuaryShorebirdPondLatLng = LatLng(-26.2893, 28.5147)
                googleMap.addMarker(MarkerOptions().position(marievaleBirdSanctuaryShorebirdPondLatLng).title("Marievale Bird Sanctuary - Shorebird Pond"))

                val northcliffRidgeEcoParkLatLng = LatLng(-26.1658, 28.0279)
                googleMap.addMarker(MarkerOptions().position(northcliffRidgeEcoParkLatLng).title("Northcliff Ridge Eco Park"))

                val tsakaneDamsLatLng = LatLng(-26.3205, 28.4863)
                googleMap.addMarker(MarkerOptions().position(tsakaneDamsLatLng).title("Tsakane Dams"))

                val zooLakeLatLng = LatLng(-26.1834, 28.0086)
                googleMap.addMarker(MarkerOptions().position(zooLakeLatLng).title("Zoo Lake"))

                val celebrationRetirementVillageOlievenpoortLatLng = LatLng(-26.0710, 28.3138)
                googleMap.addMarker(MarkerOptions().position(celebrationRetirementVillageOlievenpoortLatLng).title("Celebration Retirement Village, Olievenpoort"))

                val littleFallsNatureReserveLatLng = LatLng(-26.0921, 27.9268)
                googleMap.addMarker(MarkerOptions().position(littleFallsNatureReserveLatLng).title("Little Falls Nature Reserve"))

                val gnuValleyFarmLatLng = LatLng(-26.0506, 28.2004)
                googleMap.addMarker(MarkerOptions().position(gnuValleyFarmLatLng).title("Gnu Valley Farm"))

                val jamesAndEthelGrayParkLatLng = LatLng(-26.1278, 28.0584)
                googleMap.addMarker(MarkerOptions().position(jamesAndEthelGrayParkLatLng).title("James and Ethel Gray Park"))

                val candlewoodsWetlandLatLng = LatLng(-26.1403, 28.0151)
                googleMap.addMarker(MarkerOptions().position(candlewoodsWetlandLatLng).title("Candlewoods Wetland"))

                val devonBirdingRoutesLatLng = LatLng(-26.1735, 28.0168)
                googleMap.addMarker(MarkerOptions().position(devonBirdingRoutesLatLng).title("Devon Birding Routes"))

                val elandsvleiWetlandLatLng = LatLng(-26.1862, 28.2273)
                googleMap.addMarker(MarkerOptions().position(elandsvleiWetlandLatLng).title("Elandsvlei Wetland"))

                val dealesRockLatLng = LatLng(-26.1142, 28.0770)
                googleMap.addMarker(MarkerOptions().position(dealesRockLatLng).title("Deales Rock"))

                val rietvleiNatureReserveCoffeeShopLatLng = LatLng(-25.9061, 28.3180)
                googleMap.addMarker(MarkerOptions().position(rietvleiNatureReserveCoffeeShopLatLng).title("Rietvlei Nature Reserve - Coffee Shop"))

                val rietvleiNatureReserveOtterBridgeLatLng = LatLng(-25.9061, 28.3180)
                googleMap.addMarker(MarkerOptions().position(rietvleiNatureReserveOtterBridgeLatLng).title("Rietvlei Nature Reserve - Otter Bridge"))

                val bishopBirdParkLatLng = LatLng(-25.8034, 28.2721)
                googleMap.addMarker(MarkerOptions().position(bishopBirdParkLatLng).title("Bishop Bird Park"))

                val dainfernLatLng = LatLng(-25.9893, 28.0165)
                googleMap.addMarker(MarkerOptions().position(dainfernLatLng).title("Dainfern"))

                val janCilliersParkLatLng = LatLng(-25.7582, 28.2118)
                googleMap.addMarker(MarkerOptions().position(janCilliersParkLatLng).title("Jan Cilliers Park"))

                val mongenaPGRMongenaDamLatLng = LatLng(-25.2440, 28.6630)
                googleMap.addMarker(MarkerOptions().position(mongenaPGRMongenaDamLatLng).title("Mongena PGR - Mongena Dam"))

                val abeBaileyNatureReserveLatLng = LatLng(-26.0621, 27.9328)
                googleMap.addMarker(MarkerOptions().position(abeBaileyNatureReserveLatLng).title("Abe Bailey Nature Reserve"))

                val rietfonteinNatureReserveLatLng = LatLng(-25.8830, 28.1752)
                googleMap.addMarker(MarkerOptions().position(rietfonteinNatureReserveLatLng).title("Rietfontein Nature Reserve"))

                val marievaleBirdSanctuaryOtterHideLatLng = LatLng(-26.2946, 28.5096)
                googleMap.addMarker(MarkerOptions().position(marievaleBirdSanctuaryOtterHideLatLng).title("Marievale Bird Sanctuary - Otter Hide"))

                val rietvleiNatureReserveIslandViewLatLng = LatLng(-25.9061, 28.3180)
                googleMap.addMarker(MarkerOptions().position(rietvleiNatureReserveIslandViewLatLng).title("Rietvlei Nature Reserve - Island View"))

                val pantikiRoadLatLng = LatLng(-26.0802, 27.9016)
                googleMap.addMarker(MarkerOptions().position(pantikiRoadLatLng).title("Pantiki Road"))

                val janSmutsHouseMuseumLatLng = LatLng(-25.9926, 28.0450)
                googleMap.addMarker(MarkerOptions().position(janSmutsHouseMuseumLatLng).title("Jan Smuts House Museum"))

                val universityOfPretoriaExperimentalFarmLatLng = LatLng(-25.7633, 28.2264)
                googleMap.addMarker(MarkerOptions().position(universityOfPretoriaExperimentalFarmLatLng).title("University of Pretoria - Experimental Farm"))

                val vlaklaagteGrasslandsLatLng = LatLng(-26.1920, 28.0927)
                googleMap.addMarker(MarkerOptions().position(vlaklaagteGrasslandsLatLng).title("Vlaklaagte Grasslands"))

                val cradleOfHumankindWHSLatLng = LatLng(-25.9870, 27.6501)
                googleMap.addMarker(MarkerOptions().position(cradleOfHumankindWHSLatLng).title("Cradle of Humankind WHS"))

                val darrenwoodDamLatLng = LatLng(-26.1142, 27.9924)
                googleMap.addMarker(MarkerOptions().position(darrenwoodDamLatLng).title("Darrenwood Dam"))

                val rietvleiNatureReserveCootsCornerLatLng = LatLng(-25.9061, 28.3180)
                googleMap.addMarker(MarkerOptions().position(rietvleiNatureReserveCootsCornerLatLng).title("Rietvlei Nature Reserve - Coots Corner"))

                val seringveldConservancyRinkhalsWegLatLng = LatLng(-25.7863, 28.2005)
                googleMap.addMarker(MarkerOptions().position(seringveldConservancyRinkhalsWegLatLng).title("Seringveld Conservancy - Rinkhals Weg"))

                val suikerbosNRSedavenEstateLatLng = LatLng(-26.3093, 28.1617)
                googleMap.addMarker(MarkerOptions().position(suikerbosNRSedavenEstateLatLng).title("Suikerbos NR - Sedaven Estate (Restricted Access)"))

                val vaaloewerLatLng = LatLng(-26.1172, 28.5562)
                googleMap.addMarker(MarkerOptions().position(vaaloewerLatLng).title("Vaaloewer"))

                val blaaupanLatLng = LatLng(-26.0212, 28.0404)
                googleMap.addMarker(MarkerOptions().position(blaaupanLatLng).title("Blaaupan"))

                val dinokengGRKyleuBushCampLatLng = LatLng(-25.3500, 28.8000)
                googleMap.addMarker(MarkerOptions().position(dinokengGRKyleuBushCampLatLng).title("Dinokeng GR - Kyleu Bush Camp"))

                val johannesburgHiltonSandtonLatLng = LatLng(-26.0693, 28.0531)
                googleMap.addMarker(MarkerOptions().position(johannesburgHiltonSandtonLatLng).title("Johannesburg - Hilton Sandton"))

                val kwalataLodgeLatLng = LatLng(-25.1440, 28.6783)
                googleMap.addMarker(MarkerOptions().position(kwalataLodgeLatLng).title("Kwalata Lodge"))

                val marievaleBirdSanctuaryFlamingoHideLatLng = LatLng(-26.2879, 28.5090)
                googleMap.addMarker(MarkerOptions().position(marievaleBirdSanctuaryFlamingoHideLatLng).title("Marievale Bird Sanctuary - Flamingo Hide"))

                val mongenaPGRMongenaGameLodgeLatLng = LatLng(-25.2475, 28.6580)
                googleMap.addMarker(MarkerOptions().position(mongenaPGRMongenaGameLodgeLatLng).title("Mongena PGR - Mongena Game Lodge"))

                val mushroomParkLatLng = LatLng(-26.0679, 27.9424)
                googleMap.addMarker(MarkerOptions().position(mushroomParkLatLng).title("Mushroom Park"))

                val walterSisuluNationalBotanicalGardenRuimsigFallsLatLng = LatLng(-26.0904, 27.9035)
                googleMap.addMarker(MarkerOptions().position(walterSisuluNationalBotanicalGardenRuimsigFallsLatLng).title("Walter Sisulu National Botanical Garden - Ruimsig Falls"))

                val loerieParkLatLng = LatLng(-26.1440, 27.8974)
                googleMap.addMarker(MarkerOptions().position(loerieParkLatLng).title("Loerie Park"))

                val luiperdskloofLodgeLatLng = LatLng(-25.5980, 28.7547)
                googleMap.addMarker(MarkerOptions().position(luiperdskloofLodgeLatLng).title("Luiperdskloof Lodge"))

                val theWildsLatLng = LatLng(-26.1883, 28.0356)
                googleMap.addMarker(MarkerOptions().position(theWildsLatLng).title("The Wilds"))



            }

            override fun onFailure(call: Call<List<Hotspot>>, t: Throwable) {
                Log.e("Map", "Error fetching hotspots", t)
                Toast.makeText(this@Map, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // Permission granted, enable location
                enableMyLocation()
            }
        }
    }

    companion object {
        const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }
}