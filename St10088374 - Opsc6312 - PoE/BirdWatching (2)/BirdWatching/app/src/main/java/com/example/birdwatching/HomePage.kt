package com.example.birdwatching

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button

class HomePage : AppCompatActivity() {
    
    private lateinit var btn_view_map : Button
    private lateinit var btn_sights : Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_page)

        btn_view_map =  findViewById(R.id.hotspots_btn)
        btn_sights = findViewById(R.id.add_sighting_btn)

        btn_view_map.setOnClickListener(View.OnClickListener { startActivity(Intent(this, Map::class.java)) })
        btn_sights.setOnClickListener(View.OnClickListener { startActivity(Intent(this, Markers::class.java)) })
    }
}