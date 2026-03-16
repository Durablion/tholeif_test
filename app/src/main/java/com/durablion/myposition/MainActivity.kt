package com.durablion.myposition

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var mapView: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay
    private lateinit var recordButton: Button
    private lateinit var statsLabel: TextView
    private lateinit var locationManager: LocationManager

    private var firstFixReceived = false
    private var isRecording = false
    private val routePoints = mutableListOf<GeoPoint>()
    private var routePolyline: Polyline? = null
    private var totalDistanceM = 0.0
    private var lastLocation: Location? = null

    private val LOCATION_PERMISSION_REQUEST = 1001
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val osmConfig = Configuration.getInstance()
        osmConfig.load(this, PreferenceManager.getDefaultSharedPreferences(this))
        osmConfig.userAgentValue = packageName

        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(3.0)
        mapView.controller.setCenter(GeoPoint(0.0, 0.0))

        recordButton = findViewById(R.id.recordButton)
        statsLabel = findViewById(R.id.statsLabel)

        setupLocationOverlay()

        val myLocationButton: FloatingActionButton = findViewById(R.id.myLocationButton)
        myLocationButton.setOnClickListener {
            if (hasLocationPermissions()) {
                recentreOnLocation()
            } else {
                requestLocationPermissions()
            }
        }

        recordButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        if (hasLocationPermissions()) {
            enableLocation()
        } else {
            requestLocationPermissions()
        }
    }

    private fun startRecording() {
        isRecording = true
        routePoints.clear()
        totalDistanceM = 0.0
        lastLocation = null

        routePolyline?.let { mapView.overlays.remove(it) }
        routePolyline = Polyline().apply {
            outlinePaint.color = Color.RED
            outlinePaint.strokeWidth = 10f
        }
        mapView.overlays.add(routePolyline)

        recordButton.text = getString(R.string.stop_recording)
        recordButton.setBackgroundColor(Color.RED)
        statsLabel.text = getString(R.string.recording_started)
        Toast.makeText(this, getString(R.string.recording_started), Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        isRecording = false
        recordButton.text = getString(R.string.start_recording)
        recordButton.setBackgroundColor(Color.parseColor("#4CAF50"))
        val km = totalDistanceM / 1000.0
        statsLabel.text = getString(R.string.recording_stopped, routePoints.size, km)
        Toast.makeText(this, getString(R.string.recording_stopped, routePoints.size, km), Toast.LENGTH_LONG).show()
    }

    override fun onLocationChanged(location: Location) {
        // Centre map on first fix
        if (!firstFixReceived) {
            firstFixReceived = true
            runOnUiThread {
                mapView.controller.setZoom(17.0)
                mapView.controller.animateTo(GeoPoint(location.latitude, location.longitude))
            }
        }

        if (!isRecording) return

        val point = GeoPoint(location.latitude, location.longitude)
        routePoints.add(point)
        lastLocation?.let { prev -> totalDistanceM += prev.distanceTo(location) }
        lastLocation = location

        runOnUiThread {
            routePolyline?.setPoints(ArrayList(routePoints))
            mapView.invalidate()
            val km = totalDistanceM / 1000.0
            statsLabel.text = getString(R.string.recording_live, routePoints.size, km)
        }
    }

    private fun setupLocationOverlay() {
        val provider = GpsMyLocationProvider(this)
        locationOverlay = MyLocationNewOverlay(provider, mapView)
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        mapView.overlays.add(locationOverlay)
    }

    private fun recentreOnLocation() {
        locationOverlay.enableFollowLocation()
        val myLocation = locationOverlay.myLocation
        if (myLocation != null) {
            mapView.controller.animateTo(myLocation)
            mapView.controller.setZoom(17.0)
        } else {
            Toast.makeText(this, getString(R.string.waiting_for_gps), Toast.LENGTH_SHORT).show()
        }
    }

    private fun enableLocation() {
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 2f, this)
        }
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
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableLocation()
            } else {
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
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
        locationManager.removeUpdates(this)
    }
}
