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
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
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
import com.google.android.material.navigation.NavigationView
import java.io.File
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay

class MainActivity : AppCompatActivity(), SensorEventListener {

    //region Props

    private lateinit var mapView: MapView
    private lateinit var compassView: ImageView
    private lateinit var nearestDistanceInfo: TextView
    private lateinit var clusterCounter: TextView
    private lateinit var logTV: TextView
    private lateinit var btnTakePhoto: Button
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var photoLauncher: ActivityResultLauncher<Intent>
    private lateinit var sensorManager: SensorManager
    private lateinit var userMarker: Marker
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private var currentOrientation: String = "Unknown"
    private var currentDirectionVal: Float = 0.0f
    private var isAutoFocusEnabled = true
    private lateinit var currentLocation: GeoPoint
    private var accelerometerReading = FloatArray(3)
    private var magnetometerReading = FloatArray(3)
    private var currentPhotoFile: File? = null
    private var currnetPhotoName: String = ""
    private var clusterCount: Int = 0
    private var clusterMinDistance: Int = 9
    private var photos: Array<PhotoModel> = emptyArray<PhotoModel>()
    private var clusters: Array<ClusterModel> = emptyArray<ClusterModel>()
    private lateinit var userIcon: Bitmap
    private var isDebugMode: Boolean = false

    companion object {
        const val REQUEST_PERMISSIONS = 100
    }

    //endregion

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(applicationContext, getSharedPreferences("osm_prefs", MODE_PRIVATE))

        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        btnTakePhoto = findViewById(R.id.btn_take_photo)
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view)
        compassView = findViewById(R.id.compassView)
        nearestDistanceInfo = findViewById(R.id.nearestDistanceInfo)
        logTV = findViewById(R.id.logTV)
        clusterCounter = findViewById(R.id.cluster_counter)
        updateClusterCounterText(clusterCount)

        val originalIcon = ContextCompat.getDrawable(this, R.drawable.userlocation)
        val bitmap = (originalIcon as BitmapDrawable).bitmap
        userIcon = Bitmap.createScaledBitmap(bitmap, 25, 28, true)

        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(18.0)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000
        ).setMinUpdateIntervalMillis(500)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val userLocation = GeoPoint(location.latitude, location.longitude)
                    currentLocation = GeoPoint(userLocation.latitude,userLocation.longitude)
                    if (!isDebugMode) {
                        updateUserLocationOnMap(userLocation)
                        updateNearestPhotoDistance(userLocation)
                        updateLogText("Twoja lokalizacja: ${location.latitude} ${location.longitude}")
                    }
                }
            }
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        photoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                savePhotoMetadata()
            }
            btnTakePhoto.isEnabled = true
        }

        btnTakePhoto.setOnClickListener {
            if (checkPermissions()) {
                btnTakePhoto.isEnabled = false
                takePhoto()
            } else {
                requestPermissions()
            }
        }

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        drawerToggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.drawer_open,
            R.string.drawer_close
        )
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.btn_connect_photos -> {
                    val intent = Intent(this, MakeGraphActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.btn_load_photos -> {
                    loadPhotosAndClusters()
                    true
                }
                R.id.btn_show_graph ->{
                    val intent = Intent(this, GraphActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.debug_mode -> {
                    isDebugMode = !isDebugMode
                    menuItem.isChecked = isDebugMode
                    menuItem.title = if(isDebugMode) "Tryb developerski: ${isDebugMode}" else "Tryb developerski: ${isDebugMode}"
                    if (isDebugMode) {
                        enableDeveloperMode()
                    }
                    true
                }
                R.id.autoFocus ->{
                    isAutoFocusEnabled = !isAutoFocusEnabled
                    menuItem.isChecked = !isAutoFocusEnabled
                    menuItem.title = if(isAutoFocusEnabled) "Auto Focus: ON" else "Auto Focus: OFF"

                    var color = if (isAutoFocusEnabled) {
                        ContextCompat.getColor(this, R.color.enabled_color)
                    }
                    else
                    {
                        ContextCompat.getColor(this, R.color.disabled_color)
                    }

                    val spannableTitle = SpannableString(menuItem.title)
                    spannableTitle.setSpan(ForegroundColorSpan(color), 0, spannableTitle.length, 0)
                    menuItem.title = spannableTitle

                    true
                }
                else -> false
            }
        }

        requestPermissions()
    }


    //region LoadData

    private fun loadPhotosAndClusters() {
        loadPhotosFromFile()
        loadClustersFromFile()
    }

    private fun loadPhotosFromFile() {
        val metadataFile = File(getExternalFilesDir(null), "photo_metadata.json")
        if (!metadataFile.exists()) return

        try {
            val photoListFromFile = Json.decodeFromString<List<PhotoModel>>(metadataFile.readText())

            for (photoFromFile in photoListFromFile) {
                if (photos.any { it.id == photoFromFile.id }) continue

                photos = photos.plus(photoFromFile)

                val position = GeoPoint(photoFromFile.latitude, photoFromFile.longitude)
                addMarker(position, "Zdjęcie ID: ${photoFromFile.id}", photoFromFile.id)
            }
        } catch (e: Exception) {
            updateLogText("Błąd podczas odczytu lub deserializacji zdjęć: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun loadClustersFromFile() {
        val clusterFile = File(getExternalFilesDir(null), "clusters.json")
        if (!clusterFile.exists()) return

        try {
            val clusterListFromFile = Json.decodeFromString<List<ClusterModel>>(clusterFile.readText())

            for (clusterFromFile in clusterListFromFile) {
                if (clusters.any { it.id == clusterFromFile.id }) continue

                clusters = clusters.plus(clusterFromFile)

                displayClusterOnMap(clusterFromFile)
            }

            updateClusterCounterText(clusters.size)
        } catch (e: Exception) {
            updateLogText("Błąd podczas odczytu lub deserializacji skupisk: ${e.message}")
            e.printStackTrace()
        }
    }

    //endregion

//region Photo

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
            val metadataFile = File(getExternalFilesDir(null), "photo_metadata.json")
            val clusterFile = File(getExternalFilesDir(null), "clusters.json")

            if (currentLocation != null && currentPhotoFile != null) {
                updateNearestPhotoDistance(GeoPoint(currentLocation.latitude,currentLocation.longitude))
                val photo = PhotoModel(
                    id = currentPhotoFile!!.name,
                    clusterId = null,
                    fileName = currnetPhotoName,
                    latitude = currentLocation.latitude,
                    longitude = currentLocation.longitude,
                    direction = currentOrientation,
                    directionVal = currentDirectionVal
                )

                val existingPhotosInFile: MutableList<PhotoModel> = if (metadataFile.exists() && metadataFile.length() > 0) {
                    val fileContent = metadataFile.readText()
                    Json.decodeFromString(fileContent)
                } else {
                    mutableListOf()
                }

                val existingClustersInFile: MutableList<ClusterModel> = if (clusterFile.exists() && clusterFile.length() > 0) {
                    val clusterContent = clusterFile.readText()
                    Json.decodeFromString(clusterContent)
                } else {
                    mutableListOf()
                }

                val nearestCluster = existingClustersInFile.minByOrNull { cluster ->
                    cluster.photos.minOf { GeoPoint(it.latitude, it.longitude).distanceToAsDouble(
                        GeoPoint(photo.latitude, photo.longitude)) }
                }

                if (nearestCluster != null && GeoPoint(photo.latitude, photo.longitude).distanceToAsDouble(
                        GeoPoint(nearestCluster.photos.first().latitude, nearestCluster.photos.first().longitude)) <= clusterMinDistance
                ) {
                    nearestCluster.photos = nearestCluster.photos.plus(photo)
                    photo.clusterId = nearestCluster.id
                } else {
                    val newCluster = ClusterModel(
                        id = UUID.randomUUID().toString(),
                        photos = mutableListOf(photo),
                    )
                    existingClustersInFile.add(newCluster)
                    photo.clusterId = newCluster.id
                }

                existingPhotosInFile.add(photo)

                val jsonDataPhotos = Json.encodeToString(existingPhotosInFile)
                metadataFile.writeText(jsonDataPhotos)

                val jsonDataClusters = Json.encodeToString(existingClustersInFile)
                clusterFile.writeText(jsonDataClusters)

                clusters = existingClustersInFile.toTypedArray<ClusterModel>()
                photos = existingPhotosInFile.toTypedArray<PhotoModel>()

                addMarker(GeoPoint(currentLocation.latitude, currentLocation.longitude),
                    "Zdjęcie ID: ${currentPhotoFile!!.name}", currentPhotoFile!!.name)

                mapView.overlays.removeAll { it is org.osmdroid.views.overlay.Polygon }
                for (cluster in clusters) {
                    displayClusterOnMap(cluster)
                }

                updateClusterCounterText(clusters.size)
            }

        } else {
            requestPermissions()
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


    private fun showPhotoInfoDialog(photoInfo: String) {
        val existingDialog = supportFragmentManager.findFragmentByTag("PhotoInfoDialog")
        if (existingDialog == null) {
            val dialog = PhotoInfoDialogFragment.newInstance(photoInfo)
            dialog.show(supportFragmentManager, "PhotoInfoDialog")
        }
    }

    private fun updateNearestPhotoDistance(userLocation: GeoPoint) {
        if (photos.isEmpty()) {
            updateNearestDistanceText("Brak wykonanych zdjęć")
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

            updateNearestDistanceText("${"%.1f".format(nearestDistance)} m $direction")
        }
    }

    //endregion

    //region Cluster

    private fun displayClusterOnMap(cluster: ClusterModel) {
        val geoPoints = cluster.photos.map { GeoPoint(it.latitude, it.longitude) }
        if (geoPoints.isNotEmpty()) {
            val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPointsSafe(geoPoints)
            val center = boundingBox.centerWithDateLine

            val radius = geoPoints.maxOfOrNull {
                center.distanceToAsDouble(it)
            } ?: 0.0

            val circleOverlay = org.osmdroid.views.overlay.Polygon(mapView).apply {
                points = createCircle(center, radius + 3)
                fillPaint.color = 0x44FF0000
                outlinePaint.color = 0x00FF0000
                outlinePaint.strokeWidth = 2.0f
                setOnClickListener { _, _,_ ->
                    showClusterInfoDialog(cluster)
                    true
                }
            }
            mapView.overlays.add(circleOverlay)
            mapView.invalidate()
        }
    }

    private fun createCircle(center: GeoPoint, radius: Double): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        val steps = 36
        for (i in 0 until steps) {
            val angle = Math.toRadians((i * 360.0 / steps))
            val latitudeOffset = radius / 6378137.0 * Math.cos(angle)
            val longitudeOffset = radius / (6378137.0 * Math.cos(Math.toRadians(center.latitude))) * Math.sin(angle)
            points.add(GeoPoint(
                center.latitude + Math.toDegrees(latitudeOffset),
                center.longitude + Math.toDegrees(longitudeOffset)
            ))
        }
        points.add(points.first())
        return points
    }

    private fun showClusterInfoDialog(cluster: ClusterModel) {
        val clusterInfo = """
        ID: ${cluster.id}
        Liczba zdjęć: ${cluster.photos.size}
        Zdjęcia:
        ${cluster.photos.joinToString(separator = "\n") { it.fileName }}
    """.trimIndent()

        val dialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)
        dialogBuilder.setTitle("Informacje o skupisku")
        dialogBuilder.setMessage(clusterInfo)
        dialogBuilder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        dialogBuilder.show()
    }

    //endregion

    //region UpdateText

    private fun updateClusterCounterText(count: Int)
    {
        clusterCount = count
        clusterCounter.setText("Liczba skupisk: ${clusterCount}")
    }

    private fun updateNearestDistanceText(text: String)
    {
        nearestDistanceInfo.setText("Najbliższe zdjęcie: ${text}")
    }

    private fun updateLogText(text: String)
    {
        logTV.setText("Log: ${text}")
    }

    //endregion

    //region Compass

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

        currentDirectionVal = azimuth
        val rotation = -azimuth
        compassView.rotation = rotation
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    //endregion

    //region Location

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
        if(isAutoFocusEnabled) mapView.controller.setCenter(userLocation)
        mapView.invalidate()
    }

    //endregion

    //region Permissions

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
                updateLogText("Brak uprawnień do lokalizacji")
            }
        }
    }

    //endregion

    //region DeveloperMode

    private fun enableDeveloperMode() {
        val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (p != null && isDebugMode) {
                    updateUserLocationOnMap(p)
                    updateNearestPhotoDistance(p)
                    updateLogText("Twoja lokalizacja: ${p.latitude} ${p.longitude}")
                    currentLocation = p
                    updateLogText("Lokalizacja ustawiona na: ${p.latitude}, ${p.longitude}")
                }
                return true
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                return false
            }
        })
        mapView.overlays.add(mapEventsOverlay)
    }

    //endregion
}
