package com.example.driver_drowsiness_app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.drowsiness_app.GeocodeResponse
import com.example.drowsiness_app.RetrofitInstance
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

class Warningspage : AppCompatActivity() {

    private var EAR_THRESHOLD = 0.19
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
    private var lastRequestTime = 0L
    private var driveStartTime = 0L

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
        lastRequestTime = System.currentTimeMillis()
        driveStartTime = lastRequestTime
        startLocationUpdates()

        val options =
                FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        //
                        // .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                        .enableTracking()
                        .build()

        val detector = FaceDetection.getClient(options)

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
                        if (currentTime - lastRequestTime >= 1000) {
                            val img = camView.bitmap
                            img?.let {
                                val image = InputImage.fromBitmap(it, 0)
                                detector.process(image)
                                        .addOnSuccessListener { faces ->
                                            if (faces.size == 0) {
                                                displayDrowsy("NO FACES DETECTED!!")
                                            } else {
                                                println("Number of detected faces: ${faces.size}")
                                                for (face in faces) {
                                                    plotPointsOnEyes(face)
                                                }
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            println(
                                                    "Face Detection frontal face detections from tensorflow model errors: " +
                                                            e
                                            )
                                        }
                            }
                            lastRequestTime = currentTime
                        }
                        if (currentTime - driveStartTime >= 3 * 60 * 60 * 1000) {
                            displayDrowsy("Its time to take a break")
                        }
                    }
                }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun plotPointsOnEyes(face: Face) {
        val overlay = findViewById<ImageView>(R.id.overlay)
        println("came to point the points")
        val bitmap = Bitmap.createBitmap(overlay.width, overlay.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint =
                Paint().apply {
                    color = Color.RED
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                }

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val bounds = face.boundingBox
        canvas.drawRect(bounds, paint)

        var eare = 0.0
        var c = 0
        val left_eye = face.getContour(FaceContour.LEFT_EYE)?.points
        if (left_eye != null) {
            val p1 = left_eye[3]
            val p2 = left_eye[13]
            val p3 = left_eye[5]
            val p4 = left_eye[11]
            val p5 = left_eye[0]
            val p6 = left_eye[8]
            val d1 = findDistance(p1, p2)
            val d2 = findDistance(p3, p4)
            val d3 = findDistance(p5, p6)
            eare += ((d1 + d2) / (2.0 * d3))
            c += 1
        }

        val right_eye = face.getContour(FaceContour.RIGHT_EYE)?.points
        if (right_eye != null) {
            val p1 = right_eye[3]
            val p2 = right_eye[13]
            val p3 = right_eye[5]
            val p4 = right_eye[11]
            val p5 = right_eye[0]
            val p6 = right_eye[8]
            val d1 = findDistance(p1, p2)
            val d2 = findDistance(p3, p4)
            val d3 = findDistance(p5, p6)
            eare += ((d1 + d2) / (2.0 * d3))
            c += 1
        }

        if (c == 2) eare /= 2

        if (eare <= EAR_THRESHOLD) displayDrowsy("DROWSY!!")
        else drowsyView.visibility = TextView.INVISIBLE

        overlay.setImageBitmap(bitmap)
    }

    private fun findDistance(point1: PointF, point2: PointF): Double {
        val a = point1.x.toDouble()
        val b = point1.y.toDouble()
        val c = point2.x.toDouble()
        val d = point2.y.toDouble()
        val a1 = (c - a).pow(2.0)
        val a2 = (d - b).pow(2.0)
        return sqrt(a1 + a2)
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

    private fun roundLocation(k: Double): Double {
        val m = 10.0.pow(2)
        return round(k * m) / m
    }

    private fun displaySpeedOnScreen(speed: Double) {
        runOnUiThread { currentspeed.text = "Current speed: ${String.format("%.2f", speed)} km/h" }
    }

    private fun setSpeedLimitOnScreen() {
        runOnUiThread { speedlimitview.text = "Speed limit: $speedlimit km/h" }
        checkOverSpeeding()
    }
    override fun onBackPressed() {
        super.onBackPressed()
        finishAffinity()
    }
    fun displayDrowsy(an: String) {
        drowsyView.visibility = TextView.VISIBLE
        runOnUiThread { drowsyView.text = an }
    }
}
