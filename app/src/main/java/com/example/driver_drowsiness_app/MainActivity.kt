package com.example.driver_drowsiness_app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener

class MainActivity : AppCompatActivity() {

    private lateinit var locationManager: LocationManager
    private lateinit var locPermView: TextView
    private lateinit var locPermButton: Button
    private val LOCATION_PERMISSION_REQUEST_CODE = 100
    private val REQUEST_ENABLE_GPS = 101
    private val CAMERA_PERMISSION_REQUEST_CODE = 200
    private lateinit var intent: Intent
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        locPermView = findViewById(R.id.locPermView)
        locPermButton = findViewById(R.id.locPermButton)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        intent = Intent(this@MainActivity, Warningspage::class.java)

        locPermButton.setOnClickListener { check() }
        check()
    }

    private fun check() {
        if (isLocationPermissionGranted()) {
            if (isCameraPermissionGranted()) {
                if (isGPSEnabled()) {
                    startActivity(intent)
                    finish()
                } else {
                    enableGPS()
                }
            } else {
                requestCameraPermission()
            }
        } else {
            requestLocationPermission()
        }
    }

    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    private fun isGPSEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun enableGPS() {
        val locationRequest =
                LocationRequest.create().apply { priority = LocationRequest.PRIORITY_HIGH_ACCURACY }

        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)

        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener(OnSuccessListener<LocationSettingsResponse> {})

        task.addOnFailureListener(
                OnFailureListener { exception ->
                    if (exception is ResolvableApiException) {
                        try {
                            // Show dialog to enable GPS
                            exception.startResolutionForResult(
                                    this@MainActivity,
                                    REQUEST_ENABLE_GPS
                            )
                        } catch (sendEx: IntentSender.SendIntentException) {
                            sendEx.printStackTrace()
                        }
                    } else {
                        showToast("Failed to enable GPS.")
                    }
                }
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_GPS) {
            if (resultCode == Activity.RESULT_OK) {
                // User enabled GPS
                showToast("GPS is now enabled.")
                check()
            } else {
                displaypermission(2)
                showToast("GPS is required for using the app.")
            }
        }
    }

    private fun isLocationPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() &&
                                grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    check()
                } else {
                    displaypermission(1)
                }
            }
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() &&
                                grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    check()
                } else {
                    displaypermission(3)
                }
            }
        }
    }

    private fun displaypermission(a: Int) {
        var ans = ""
        if (a == 1) {
            ans = "Location permission must be given in order to use the app"
        } else if (a == 2) {
            ans = "GPS must be turned on in order to use the app"
        } else if (a == 3) {
            ans = "Camera permission must be given in order to use the app"
        }
        locPermButton.visibility = View.VISIBLE
        locPermView.text = ans
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
