package com.master.collectdatastreetview

import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MakeGraphActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var btnBack: Button
    private lateinit var btnSaveGraph: Button
    private lateinit var btnLoadGraph: Button
    private val photoMarkers = mutableMapOf<String, Marker>()
    private val graphEdges = mutableListOf<Pair<String, String>>()
    private val lineOverlays = mutableMapOf<String, Polyline>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osm_prefs", MODE_PRIVATE))
        setContentView(R.layout.activity_make_graph)

        mapView = findViewById(R.id.mapView)
        btnBack = findViewById(R.id.btn_back)
        btnSaveGraph = findViewById(R.id.btn_save_graph)
        btnLoadGraph = findViewById(R.id.btn_load_graph)

        btnBack.setOnClickListener {
            finish()
        }

        btnSaveGraph.setOnClickListener {
            saveGraphToMediaStore()
            checkGraphFileExistence()
        }

        btnLoadGraph.setOnClickListener {
            if (graphEdgesExist()) {
                Toast.makeText(this, "Najpierw usuń istniejące połączenia", Toast.LENGTH_SHORT).show()
            } else {
                loadGraphFromMediaStore()
            }

        }

        setupMap()
        checkGraphFileExistence()
    }

    private fun setupMap() {
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(15.0)
        loadPhotosWithMarkers()
    }

    private fun checkGraphFileExistence() {
        val uri = getGraphFileUri()
        btnLoadGraph.isEnabled = uri != null
    }

    private fun loadPhotosWithMarkers() {
        val metadataFile = File(getExternalFilesDir(null), "photo_metadata.json")
        if (!metadataFile.exists()) return

        try {
            val photoListFromFile = Json.decodeFromString<List<PhotoModel>>(metadataFile.readText())

            if (photoListFromFile.isEmpty()) return

            for (photoFromFile in photoListFromFile) {
                if (photos.any { it.id == photoFromFile.id }) continue

                photos = photos.plus(photoFromFile)

                val position = GeoPoint(photoFromFile.latitude, photoFromFile.longitude)
                addMarker(photoFromFile, position)
            }
            mapView.controller.setCenter(GeoPoint(photoListFromFile.first().latitude, photoListFromFile.first().longitude))
        } catch (e: Exception) {
            println("Błąd podczas odczytu lub deserializacji pliku JSON: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun addMarker(photoInfo: PhotoModel, position: GeoPoint) {
        val marker = Marker(mapView)
        marker.position = position
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "Zdjęcie ID: $photoInfo.id"
        marker.id = photoInfo.id
        marker.relatedObject = photoInfo

        marker.setOnMarkerClickListener { clickedMarker, _ ->
            if (selectedMarker == null) {
                selectedMarker = clickedMarker
                Toast.makeText(this, "Marker wybrany: ${clickedMarker.id}", Toast.LENGTH_SHORT).show()
            } else if (selectedMarker != clickedMarker) {
                createConnection(selectedMarker!!, clickedMarker)
                selectedMarker = null
            } else {
                selectedMarker = null
            }
            true
        }

        mapView.overlays.add(marker)
        photoMarkers[photoInfo.id] = marker
    }

    private fun createConnection(markerA: Marker, markerB: Marker) {
        val idA = markerA.id
        val idB = markerB.id

        if (edgeExists(idA, idB)) {
            Toast.makeText(this, "Połączenie już istnieje", Toast.LENGTH_SHORT).show()
            return
        }

        val line = Polyline()
        line.addPoint(markerA.position)
        line.addPoint(markerB.position)
        line.outlinePaint.apply {
            color = android.graphics.Color.RED
            strokeWidth = 15f
            isAntiAlias = true
        }

        line.setOnClickListener { _, _, _ ->
            removeConnection(idA, idB)
            true
        }

        graphEdges.add(Pair(idA, idB))
        val lineId = generateEdgeId(idA, idB)
        lineOverlays[lineId] = line
        mapView.overlays.add(line)
        mapView.invalidate()
    }

    private fun removeConnection(idA: String, idB: String) {
        val lineId = generateEdgeId(idA, idB)
        val line = lineOverlays.remove(lineId)
        if (line != null) {
            mapView.overlays.remove(line)
            graphEdges.removeAll { (a, b) -> (a == idA && b == idB) || (a == idB && b == idA) }
            mapView.invalidate()
            Toast.makeText(this, "Połączenie usunięte", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getGraphFileUri(): Uri? {
        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        val selection = MediaStore.Files.FileColumns.DISPLAY_NAME + "=?"
        val selectionArgs = arrayOf("graph_data.json")
        val cursor = contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection, selection, selectionArgs, null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                return Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), id.toString())
            }
        }
        return null
    }

    private fun edgeExists(idA: String, idB: String): Boolean {
        return graphEdges.any { (a, b) -> (a == idA && b == idB) || (a == idB && b == idA) }
    }

    private fun generateEdgeId(idA: String, idB: String): String {
        return if (idA < idB) "$idA-$idB" else "$idB-$idA"
    }

    private fun saveGraphToMediaStore() {
        val vertices = photoMarkers.map { (photoId, marker) ->
            val connectedVertices = graphEdges
                .filter { it.first == photoId || it.second == photoId }
                .flatMap { listOf(it.first, it.second) }
                .filter { it != photoId }
                .distinct()

            VertexModel(
                photo = marker.relatedObject as PhotoModel,
                edges = connectedVertices,
            )
        }

        val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
        val graphModel = GraphModel(
            name = "Graf_$date",
            date = date,
            vertices = vertices
        )

        val jsonString = Json.encodeToString(graphModel)
        saveGraphToFile(jsonString)
    }

    private fun saveGraphToFile(jsonData: String) {
        val file = File(getExternalFilesDir(null), "graph_data.json")
        file.writeText(jsonData)
        Toast.makeText(this, "Graf zapisany do graph_data.json", Toast.LENGTH_SHORT).show()
    }

    private fun loadGraphFromMediaStore() {
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
        photoMarkers.clear()
        lineOverlays.clear()
        mapView.overlays.clear()

        graphModel.vertices.forEach { vertex ->
            val position = GeoPoint(vertex.photo.latitude, vertex.photo.longitude)
            addMarker(vertex.photo, position)
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

    private fun graphEdgesExist(): Boolean {
        return lineOverlays.isNotEmpty()
    }
}
