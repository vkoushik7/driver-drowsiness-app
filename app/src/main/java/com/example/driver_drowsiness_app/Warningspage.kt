package com.example.driver_drowsiness_app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.drowsiness_app.GeocodeResponse
import com.example.drowsiness_app.RetrofitInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.pow
import kotlin.math.round

class Warningspage : AppCompatActivity() {

    private lateinit var locationManager: LocationManager
    private lateinit var currentspeed: TextView
    private lateinit var overspeed: TextView
    private lateinit var speedlimitview: TextView
    private lateinit var drowsyView: TextView
    private lateinit var camView: TextureView
    private lateinit var intent: Intent
    private val roadSpeedCache = mutableMapOf<Pair<Double, Double>, Int>()
    private var presentspeed: Double = 0.0
    private var speedlimit: Int = 0
    private var mapsapikey: String? = ""
    private var serverapikey: String? = ""
    private val handler = Handler(Looper.getMainLooper())
    private var lastRequestTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_warningspage)
        intent = Intent(this@Warningspage, MainActivity::class.java)
        currentspeed = findViewById(R.id.currentspeed)
        overspeed = findViewById(R.id.overspeed)
        speedlimitview = findViewById(R.id.speedlimit)
        drowsyView = findViewById(R.id.drowsyView)
        camView = findViewById(R.id.camView)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        mapsapikey = getApiKeyFromMetadata()
        serverapikey = getServerKeyFromMetadata()
        startLocationUpdates()

        camView.surfaceTextureListener =
                object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int
                    ) {
                        openCamera()
                    }

                    override fun onSurfaceTextureSizeChanged(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int
                    ) {
                        // Handle size changes
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                        // Handle updates
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastRequestTime >= 500) { // 500 milliseconds = 0.5 second
                            val img = camView.bitmap
                            img?.let { check_drowsiness(it) }
                            lastRequestTime = currentTime
                        }
                    }
                }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun openCamera() {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList[1]

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                        PackageManager.PERMISSION_GRANTED
        ) {
            startActivity(intent)
        }

        cameraManager.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        val surfaceTexture = camView.surfaceTexture
                        val surface = Surface(surfaceTexture)
                        val captureRequestBuilder =
                                camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        captureRequestBuilder.addTarget(surface)

                        camera.createCaptureSession(
                                listOf(surface),
                                object : CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(session: CameraCaptureSession) {
                                        val captureRequest = captureRequestBuilder.build()
                                        session.setRepeatingRequest(captureRequest, null, null)
                                    }

                                    override fun onConfigureFailed(session: CameraCaptureSession) {
                                        // Handle configuration failure
                                    }
                                },
                                null
                        )
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        // Handle camera disconnection
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        // Handle camera error
                    }
                },
                null
        )
    }
    private fun getRoadTypeCheck(latitude: Double, longitude: Double) {
        val latlng = Pair(latitude, longitude)
        if (roadSpeedCache.containsKey(latlng)) {
            speedlimit = roadSpeedCache[latlng]!!
            setSpeedLimitOnScreen()
        } else {
            getRoadType(latitude, longitude)
        }
    }

    private fun getRoadType(latitude: Double, longitude: Double) {
        val latlng = "$latitude,$longitude"
        val call = RetrofitInstance.apiInterface.getGeocode(latlng, mapsapikey.toString())
        println("its working here with coord: $latlng")
        call.enqueue(
                object : Callback<GeocodeResponse> {
                    override fun onResponse(
                            call: Call<GeocodeResponse>,
                            response: Response<GeocodeResponse>
                    ) {
                        if (response.isSuccessful) {
                            val geocodeResponse = response.body()
                            if (geocodeResponse != null && geocodeResponse.results.isNotEmpty()) {
                                val firstTwoResults = geocodeResponse.results.take(2)
                                val typesArrays =
                                        firstTwoResults.map { result ->
                                            result.address_components.take(2).flatMap { it.types }
                                        }
                                setSpeedLimit(typesArrays, latitude, longitude)
                                println("Types are successfully extracted")
                            } else {
                                println("API call failed with status code ${response.code()}")
                            }
                        } else {
                            println("API call failed with status code ${response.code()}")
                        }
                    }

                    override fun onFailure(call: Call<GeocodeResponse>, t: Throwable) {
                        println("API call failed with error: ${t.message}")
                    }
                }
        )
    }

    private fun setSpeedLimit(arr: List<List<String>>, latitude: Double, longitude: Double) {
        speedlimit = 90
        for (i in arr) {
            if (i.contains("route")) {
                speedlimit = 90
                break
            } else if (i.contains("sublocality")) {
                speedlimit = 50
                break
            } else if (i.contains("administrative_area_level_2")) {
                speedlimit = 70
                break
            } else if (i.contains("administrative_area_level_1")) {
                speedlimit = 80
                break
            } else if (i.contains("country")) {
                speedlimit = 100
                break
            }
        }
        println("Speed limit is calculated as $speedlimit")
        roadSpeedCache[Pair(latitude, longitude)] = speedlimit
        setSpeedLimitOnScreen()
    }

    private fun checkOverSpeeding() {
        if (presentspeed > speedlimit) {
            overspeed.visibility = TextView.VISIBLE
        } else {
            overspeed.visibility = TextView.INVISIBLE
        }
    }

    private fun getApiKeyFromMetadata(): String? {
        return try {
            val applicationInfo =
                    packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            applicationInfo.metaData.getString("com.google.android.geo.API_KEY")
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            null
        }
    }

    private fun getServerKeyFromMetadata(): String? {
        return try {
            val applicationInfo =
                    packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            applicationInfo.metaData.getString("com.google.android.geo.SERVER_KEY")
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            null
        }
    }
    private fun startLocationUpdates() {
        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L,
                    0f,
                    locationListener
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private val locationListener: LocationListener =
            object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    val speed = location.speed
                    val speedKmh = speed * 3.6
                    presentspeed = speedKmh
                    displaySpeedOnScreen(speedKmh)
                    getRoadTypeCheck(
                            roundLocation(location.latitude),
                            roundLocation(location.longitude)
                    )
                }
                @Deprecated("Deprecated")
                override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}

                override fun onProviderEnabled(provider: String) {}

                override fun onProviderDisabled(provider: String) {
                    startActivity(intent)
                }
            }

    fun check_drowsiness(img: Bitmap) {
        val bimg = bitmapToBase64(img)
        CoroutineScope(Dispatchers.Main).launch {
            val stres = checkfromapi(bimg)
            println("stres: ${stres}")
            var res = stres?.toInt()
            withContext(Dispatchers.Main) { displayDrowsyOnScreen(res!!) }
        }
    }
    fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    suspend fun checkfromapi(base64Image: String): String? =
            withContext(Dispatchers.IO) {
                // println("start of checkfrom api function")
                val client = OkHttpClient()
                var isOk = false
                val jsonObject = JSONObject()
                jsonObject.put("image", base64Image)

                val requestBody =
                        RequestBody.create(
                                "application/json; charset=utf-8".toMediaTypeOrNull(),
                                jsonObject.toString()
                        )

                val request =
                        Request.Builder()
                                .url("http://54.89.143.165:5000/check_drowsiness")
                                .post(requestBody)
                                .addHeader("x-api-key", serverapikey!!)
                                .build()

                // println("came upto request complete")

                try {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            // println("Unexpected code $response")
                            throw IOException("Unexpected code $response")
                        }
                        val responseBody = response.body?.string()
                        println("returning the value from api: $responseBody")
                        return@withContext responseBody
                    }
                } catch (e: Exception) {
                    // Handle the exception (e.g., show an error message to the user, log the error,
                    // etc.)
                    println("this is from catch expression ${e}")
                    return@withContext "-1"
                    null
                }
            }

    private fun roundLocation(k: Double): Double {
        val m = 10.0.pow(2)
        return round(k * m) / m
    }

    private fun displayDrowsyOnScreen(a: Int) {
        var str: String = ""
        if (a == 1) str = "DROWSY"
        else if (a == 2) str = "NOT DROWSY" else if (a == -1) str = "NO FACE DETECTED!!"
        drowsyView.text = str
    }
    private fun displaySpeedOnScreen(speed: Double) {
        currentspeed.text = "Current speed: ${String.format("%.2f", speed)} km/h"
    }

    private fun setSpeedLimitOnScreen() {
        speedlimitview.text = "Speed limit: $speedlimit km/h"
        checkOverSpeeding()
    }
    override fun onBackPressed() {
        super.onBackPressed()
        finishAffinity()
    }
}
