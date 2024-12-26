package com.master.collectdatastreetview

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.serialization.json.Json
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GraphActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var btnBack: Button
    private lateinit var btnLoad: Button
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osm_prefs", MODE_PRIVATE))
        setContentView(R.layout.activity_graph)

        mapView = findViewById(R.id.mapView)
        btnBack = findViewById(R.id.btn_back_to_main)
        btnLoad = findViewById(R.id.btn_load_graph)
        btnSave = findViewById(R.id.btn_saveRun)

        btnBack.setOnClickListener {
            finish()
        }

        btnLoad.setOnClickListener{
            loadGraphFromFile()
        }

        btnSave.setOnClickListener{
            showConfirmationDialog()
        }

        btnSave.isEnabled = areGeneratedFilesExists()

        btnLoad.isEnabled = getGraphFileUri() != null

        setupMap()

    }


    private fun setupMap() {
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(15.0)
    }


    private fun loadGraphFromFile() {
        val file = File(getExternalFilesDir(null), "graph_data.json")
        if (!file.exists()) {
            Toast.makeText(this, "Plik graph_data.json nie istnieje!", Toast.LENGTH_SHORT).show()
            return
        }

        val jsonData = file.readText()
        try {
            val graphModel = Json.decodeFromString<GraphModel>(jsonData)
            drawGraphOnMap(graphModel)
            Toast.makeText(this, "Graf załadowany pomyślnie", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Błąd podczas wczytywania grafu", Toast.LENGTH_SHORT).show()
        }
    }


    private fun drawGraphOnMap(graphModel: GraphModel) {
        mapView.overlays.clear()

        val photoMarkers = mutableMapOf<String, Marker>()
        graphModel.vertices.forEach { vertex ->
            val position = GeoPoint(vertex.photo.latitude, vertex.photo.longitude)
            val marker = addMarker(vertex.photo, position)
            photoMarkers[vertex.photo.id] = marker
        }

        graphModel.vertices.forEach { vertex ->
            vertex.edges.forEach { connectedVertexId ->
                val markerA = photoMarkers[vertex.photo.id]
                val markerB = photoMarkers[connectedVertexId]
                if (markerA != null && markerB != null) {
                    createConnection(markerA, markerB)
                }
            }
        }
    }


    private fun addMarker(photo: PhotoModel, position: GeoPoint): Marker {
        val marker = Marker(mapView)
        marker.position = position
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "ID: ${photo.id}\nPlik: ${photo.fileName}\nLat: ${position.latitude}, Lon: ${position.longitude}"
        marker.id = photo.id

        marker.setOnMarkerClickListener { clickedMarker, _ ->
            Toast.makeText(this, marker.title, Toast.LENGTH_LONG).show()
            true
        }

        mapView.overlays.add(marker)
        return marker
    }


    private fun createConnection(markerA: Marker, markerB: Marker) {
        val line = Polyline()
        line.addPoint(markerA.position)
        line.addPoint(markerB.position)
        line.outlinePaint.apply {
            color = android.graphics.Color.RED
            strokeWidth = 15f
            isAntiAlias = true
        }
        mapView.overlays.add(line)
    }


    private fun getGraphFileUri(): Uri? {
        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        val selection = MediaStore.Files.FileColumns.DISPLAY_NAME + "=?"
        val selectionArgs = arrayOf("graph_data.json")

        val cursor = contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                return Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), id.toString())
            }
        }
        return null
    }


    private fun showConfirmationDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Potwierdzenie")
        builder.setMessage("Czy na pewno chcesz zapisać tę trasę?")

        builder.setPositiveButton("Tak") { dialog, _ ->
            dialog.dismiss()
            saveRun()
        }

        builder.setNegativeButton("Nie") { dialog, _ ->
            dialog.dismiss()
        }

        val alertDialog = builder.create()
        alertDialog.show()
    }


    private fun saveRun()
    {
        val runsDir = File(getExternalFilesDir(null), "Runs")
        if (!runsDir.exists()) {
            runsDir.mkdirs()
        }

        val currentDate = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
        val runFolder = File(runsDir, "Run_$currentDate")
        if (!runFolder.exists()) {
            runFolder.mkdirs()
        }

        val photoDir = File(getExternalFilesDir(null), "photos")
        val metadataFile = File(getExternalFilesDir(null), "photo_metadata.txt")
        val graphFile = File(getExternalFilesDir(null), "graph_data.json")

        if (photoDir.exists()) {
            val newPhotoDir = File(runFolder, "photos")
            moveFolder(photoDir, newPhotoDir)
        }

        if (metadataFile.exists()) {
            moveFile(metadataFile, File(runFolder, "photo_metadata.txt"))
        }

        if (graphFile.exists()) {
            moveFile(graphFile, File(runFolder, "graph_data.json"))
        }

        Toast.makeText(this, "Zapisano", Toast.LENGTH_SHORT).show()
    }


    private fun moveFile(source: File, destination: File) {
        val sourceChannel: FileChannel? = FileInputStream(source).channel
        val destChannel: FileChannel? = FileOutputStream(destination).channel
        try {
            sourceChannel?.transferTo(0, sourceChannel.size(), destChannel)
        } finally {
            sourceChannel?.close()
            destChannel?.close()
        }
        source.delete()
    }


    private fun moveFolder(sourceFolder: File, destinationFolder: File) {
        if (!destinationFolder.exists()) {
            destinationFolder.mkdirs()
        }
        sourceFolder.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                moveFolder(file, File(destinationFolder, file.name))
            } else {
                moveFile(file, File(destinationFolder, file.name))
            }
        }
        sourceFolder.delete()
    }


    private fun areGeneratedFilesExists():Boolean{
        val photoDir = File(getExternalFilesDir(null), "photos")
        val metadataFile = File(getExternalFilesDir(null), "photo_metadata.txt")
        val graphFile = File(getExternalFilesDir(null), "graph_data.json")

        val isMetadataFileAvailable = metadataFile.exists()

        val isGraphFileAvailable = graphFile.exists()

        val arePhotosAvailable = photoDir.exists() && photoDir.listFiles()?.isNotEmpty() == true

        return isMetadataFileAvailable || isGraphFileAvailable || arePhotosAvailable
    }
}
