package com.durablion.myposition

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay
    private var firstFixReceived = false

    private val LOCATION_PERMISSION_REQUEST = 1001
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialise OSMDroid configuration
        val osmConfig = Configuration.getInstance()
        osmConfig.load(this, PreferenceManager.getDefaultSharedPreferences(this))
        osmConfig.userAgentValue = packageName

        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Default view centred on the world at zoom 3 until we get a fix
        mapView.controller.setZoom(3.0)
        mapView.controller.setCenter(GeoPoint(0.0, 0.0))

        setupLocationOverlay()

        val myLocationButton: FloatingActionButton = findViewById(R.id.myLocationButton)
        myLocationButton.setOnClickListener {
            if (hasLocationPermissions()) {
                recentreOnLocation()
            } else {
                requestLocationPermissions()
            }
        }

        if (hasLocationPermissions()) {
            enableLocation()
        } else {
            requestLocationPermissions()
        }
    }

    private fun setupLocationOverlay() {
        val provider = GpsMyLocationProvider(this)
        locationOverlay = MyLocationNewOverlay(provider, mapView)

        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()

        locationOverlay.runOnFirstFix {
            runOnUiThread {
                if (!firstFixReceived) {
                    firstFixReceived = true
                    val myLocation = locationOverlay.myLocation
                    if (myLocation != null) {
                        mapView.controller.setZoom(17.0)
                        mapView.controller.animateTo(myLocation)
                    }
                }
            }
        }

        mapView.overlays.add(locationOverlay)
    }

    private fun recentreOnLocation() {
        locationOverlay.enableFollowLocation()
        val myLocation = locationOverlay.myLocation
        if (myLocation != null) {
            mapView.controller.animateTo(myLocation)
            mapView.controller.setZoom(17.0)
        } else {
            Toast.makeText(this, "Waiting for GPS fix…", Toast.LENGTH_SHORT).show()
        }
    }

    private fun enableLocation() {
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
    }

    private fun hasLocationPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, LOCATION_PERMISSION_REQUEST)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                enableLocation()
            } else {
                Toast.makeText(
                    this,
                    "Location permission denied. The map cannot show your position.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        if (hasLocationPermissions()) {
            locationOverlay.enableMyLocation()
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        locationOverlay.disableMyLocation()
    }
}
