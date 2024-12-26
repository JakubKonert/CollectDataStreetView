package com.master.collectdatastreetview

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.File
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var mapView: MapView
    private lateinit var compassView: ImageView
    private lateinit var nearestDistanceInfo: TextView
    private lateinit var logTV: TextView
    private lateinit var btnTakePhoto: Button
    private lateinit var btnLoadPhotos: Button
    private lateinit var btnShowGraph: Button
    private lateinit var btnConnectPhotos: Button

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var photoLauncher: ActivityResultLauncher<Intent>
    private lateinit var sensorManager: SensorManager
    private lateinit var userMarker: Marker
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private var currentOrientation: String = "Unknown"
    private var currentDirectionVal: Float = 0.0f
    private lateinit var currentLocation: GeoPoint

    private var accelerometerReading = FloatArray(3)
    private var magnetometerReading = FloatArray(3)
    private var currentPhotoFile: File? = null
    private var currnetPhotoName: String = ""
    private var photos: Array<PhotoModel> = emptyArray<PhotoModel>()
    private lateinit var userIcon: Bitmap;

    companion object {
        const val REQUEST_PERMISSIONS = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(applicationContext, getSharedPreferences("osm_prefs", MODE_PRIVATE))

        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        btnTakePhoto = findViewById(R.id.btn_take_photo)
        btnLoadPhotos = findViewById(R.id.btn_load_photos)
        btnShowGraph = findViewById(R.id.btn_show_graph)
        btnConnectPhotos = findViewById(R.id.btn_connect_photos)
        compassView = findViewById(R.id.compassView)
        nearestDistanceInfo = findViewById(R.id.nearestDistanceInfo)
        logTV = findViewById(R.id.logTV)

        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(18.0)

        val originalIcon = ContextCompat.getDrawable(this, R.drawable.userlocation)
        val bitmap = (originalIcon as BitmapDrawable).bitmap
        userIcon = Bitmap.createScaledBitmap(bitmap, 25, 28, true)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000
        ).setMinUpdateIntervalMillis(2000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val userLocation = GeoPoint(location.latitude, location.longitude)
                    currentLocation = GeoPoint(userLocation.latitude,userLocation.longitude)
                    updateUserLocationOnMap(userLocation)
                    updateNearestPhotoDistance(userLocation)
                    logTV.text = "Log: Twoja lokalizacja: ${location.latitude} ${location.longitude}"
                }
            }
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        photoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                savePhotoMetadata()
            }
        }

        btnTakePhoto.setOnClickListener {
            if (checkPermissions()) {
                takePhoto()
            } else {
                requestPermissions()
            }
        }

        btnLoadPhotos.setOnClickListener {
            loadPhotosFromFile()
        }

        btnShowGraph.setOnClickListener {
            val intent = Intent(this, GraphActivity::class.java)
            startActivity(intent)
        }

        btnConnectPhotos.setOnClickListener {
            val intent = Intent(this, MakeGraphActivity::class.java)
            startActivity(intent)
        }

        requestPermissions()
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun updateUserLocationOnMap(userLocation: GeoPoint) {
        if (!::userMarker.isInitialized) {
            userMarker = Marker(mapView)
            userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            userMarker.icon = BitmapDrawable(resources, userIcon)
            mapView.overlays.add(userMarker)
        }

        userMarker.position = userLocation
        mapView.controller.setCenter(userLocation)
        mapView.invalidate()
    }

    private fun takePhoto() {
        val photoName = "IMG_${UUID.randomUUID()}.jpg"
        val photosDir = File(getExternalFilesDir(null), "photos")
        if (!photosDir.exists()) photosDir.mkdirs()
        currentPhotoFile = File(photosDir, photoName)
        currnetPhotoName = photoName

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        val photoUri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            currentPhotoFile!!
        )

        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        photoLauncher.launch(intent)

    }

    private fun savePhotoMetadata() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val metadataFile = File(getExternalFilesDir(null), "photo_metadata.txt")
            if (currentLocation != null && currentPhotoFile != null) {
                updateNearestPhotoDistance(GeoPoint(currentLocation.latitude,currentLocation.longitude))
                val photo = PhotoModel(
                    id = currentPhotoFile!!.name,
                    fileName = currnetPhotoName,
                    latitude = currentLocation.latitude,
                    longitude = currentLocation.longitude,
                    direction = currentOrientation,
                    directionVal = currentDirectionVal
                )
                photos = photos.plus(photo)
                val jsonData = Json.encodeToString(photo)
                metadataFile.appendText(jsonData + "\n")
                addMarker(GeoPoint(currentLocation.latitude, currentLocation.longitude),
                    "Zdjęcie ID: ${currentPhotoFile!!.name}", currentPhotoFile!!.name)
            }

        } else {
            requestPermissions()
        }
    }

    private fun loadPhotosFromFile() {
        val metadataFile = File(getExternalFilesDir(null), "photo_metadata.txt")
        if (!metadataFile.exists()) return

        val photoListFromFile = metadataFile.readLines().mapNotNull { line ->
            try {
                Json.decodeFromString<PhotoModel>(line)
            } catch (e: Exception) {
                println("Błąd podczas deserializacji linii: $line")
                e.printStackTrace()
                null
            }
        }

        for (photoFromFile in photoListFromFile)
        {
            if(photos.any{it.id == photoFromFile.id}) continue
            photos = photos.plus(photoFromFile)
            val position = GeoPoint(photoFromFile.latitude, photoFromFile.longitude)
            addMarker(position, "Zdjęcie ID: $photoFromFile.id", photoFromFile.id)
        }
    }

    private fun addMarker(position: GeoPoint, title: String, id: String?) {
        val marker = Marker(mapView)
        marker.position = position
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = title
        marker.isDraggable = false
        if (id == null)
        {
            marker.id = "0"
        }
        else{
            marker.id = id
        }

        marker.setOnMarkerClickListener { clickedMarker, _ ->
            val photo = photos.find { it.id == clickedMarker.id }
            if (photo != null) {
                val photoInfo = """
                ID: ${photo.id}
                Nazwa pliku: ${photo.fileName}
                Szerokość: ${photo.latitude}
                Długość: ${photo.longitude}
                Kierunek: ${photo.direction}
            """.trimIndent()
                showPhotoInfoDialog(photoInfo)
            }
            true
        }

        mapView.overlays.add(marker)
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_UI)
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            accelerometerReading = event.values
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            magnetometerReading = event.values
        }

        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)

        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        currentOrientation = when (azimuth) {
            in -22.5..22.5 -> "N"
            in 22.5..67.5 -> "NE"
            in 67.5..112.5 -> "E"
            in 112.5..157.5 -> "SE"
            in -67.5..-22.5 -> "NW"
            in -112.5..-67.5 -> "W"
            in -157.5..-112.5 -> "SW"
            else -> "S"
        }

        val rotation = -azimuth
        compassView.rotation = rotation
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun showPhotoInfoDialog(photoInfo: String) {
        val existingDialog = supportFragmentManager.findFragmentByTag("PhotoInfoDialog")
        if (existingDialog == null) {
            val dialog = PhotoInfoDialogFragment.newInstance(photoInfo)
            dialog.show(supportFragmentManager, "PhotoInfoDialog")
        }
    }

    private fun updateNearestPhotoDistance(userLocation: GeoPoint) {
        if (photos.isEmpty()) {
            nearestDistanceInfo.text = "Brak wykonanych zdjęć"
            return
        }

        var nearestDistance = Double.MAX_VALUE
        var nearestPhoto: PhotoModel? = null

        photos.forEach { photo ->
            val photoLocation = GeoPoint(photo.latitude, photo.longitude)
            val distance = userLocation.distanceToAsDouble(photoLocation)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestPhoto = photo
            }
        }

        nearestPhoto?.let { photo ->
            val photoLocation = GeoPoint(photo.latitude, photo.longitude)
            val bearing = userLocation.bearingTo(photoLocation).toFloat()

            val direction = when (bearing) {
                in -22.5..22.5 -> "↑"
                in 22.5..67.5 -> "↗"
                in 67.5..112.5 -> "→"
                in 112.5..157.5 -> "↘"
                in -67.5..-22.5 -> "↖"
                in -112.5..-67.5 -> "←"
                in -157.5..-112.5 -> "↙"
                else -> "↓"
            }

            nearestDistanceInfo.text = "Najbliższe zdjęcie: ${"%.1f".format(nearestDistance)} m $direction"
        }
    }

    private fun checkPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ), REQUEST_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            } else {
                logTV.text = "Log: Brak uprawnień do lokalizacji"
            }
        }
    }

}
