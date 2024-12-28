package com.master.collectdatastreetview

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.serialization.json.Json
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GraphActivity : AppCompatActivity() {

    //region Props

    private lateinit var mapView: MapView
    private lateinit var btnBack: Button
    private lateinit var btnLoad: Button
    private lateinit var btnSave: Button

    private var clusterVertices: MutableList<ClusterVertexModel> = mutableListOf()

    //endregion

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
            loadGraphAndDisplayOnMap()
        }

        btnSave.setOnClickListener{
            showConfirmationDialog()
        }

        btnSave.isEnabled = areGeneratedFilesExists()

        btnLoad.isEnabled = isGraphFile()

        setupMap()

    }


    //region Setup

    private fun setupMap() {
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(20.0)
    }

    private fun isGraphFile(): Boolean {
        val graphFile = File(getExternalFilesDir(null), "graph.json")

        if (graphFile.exists()) {
            val hasData = graphFile.length() > 0
            return hasData
        }
        return false
    }

    //endregion

    //region LoadData

    private fun loadGraphAndDisplayOnMap() {
        val graphFile = File(getExternalFilesDir(null), "graph.json")
        if (!graphFile.exists()) {
            Toast.makeText(this, "Brak pliku grafu.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val graphModel: GraphModel = Json.decodeFromString(graphFile.readText())
            clusterVertices.clear()
            clusterVertices.addAll(graphModel.vertices)

            mapView.overlays.clear()

            clusterVertices.firstOrNull()?.vertices?.firstOrNull()?.let {
                mapView.controller.setCenter(GeoPoint(it.photo.latitude, it.photo.longitude))
            }

            for (cluster in clusterVertices) {
                displayClusterOnMap(cluster)
            }

            for (cluster in clusterVertices) {
                for (edgeId in cluster.edges) {
                    val targetCluster = clusterVertices.find { it.id == edgeId }
                    if (targetCluster != null) {
                        drawEdgeOnMap(cluster, targetCluster)
                    }
                }
            }

            Toast.makeText(this, "Graf wczytany.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Błąd wczytywania grafu.", Toast.LENGTH_SHORT).show()
        }
    }

    //endregion

    //region Map

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

    private fun displayClusterOnMap(cluster: ClusterVertexModel) {
        val geoPoints = cluster.vertices.map { GeoPoint(it.photo.latitude, it.photo.longitude) }
        if (geoPoints.isNotEmpty()) {
            val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPointsSafe(geoPoints)
            val center = boundingBox.centerWithDateLine

            val radius = geoPoints.maxOfOrNull {
                center.distanceToAsDouble(it)
            } ?: 0.0

            val circleOverlay = Polygon(mapView).apply {
                points = createCircle(center, radius)
                fillPaint.color = 0x44FF0000
                outlinePaint.color = 0x00FF0000
                outlinePaint.strokeWidth = 2.0f
                setOnClickListener { _, _, _ ->
                    showClusterInfoDialog(cluster)
                    true
                }
            }

            mapView.overlays.add(circleOverlay)
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

    private fun drawEdgeOnMap(cluster1: ClusterVertexModel, cluster2: ClusterVertexModel) {
        val geoPoint1 = GeoPoint(
            cluster1.vertices.first().photo.latitude,
            cluster1.vertices.first().photo.longitude
        )
        val geoPoint2 = GeoPoint(
            cluster2.vertices.first().photo.latitude,
            cluster2.vertices.first().photo.longitude
        )

        val polyline = Polyline(mapView).apply {
            addPoint(geoPoint1)
            addPoint(geoPoint2)
            setOnClickListener { _, _, _ ->
                true
            }
        }
        polyline.outlinePaint.apply {
            color = android.graphics.Color.RED
            strokeWidth = 5f
            isAntiAlias = true
        }

        mapView.overlays.add(polyline)
        mapView.invalidate()
    }

    private fun showClusterInfoDialog(cluster: ClusterVertexModel) {
        val clusterInfo = """
        ID: ${cluster.id}
        
        Vertices:
        ${cluster.vertices.joinToString(separator = "\n\n") { it.photo.id }}
        
        Edges:
        ${cluster.edges.joinToString(separator = "\n\n")}
        """.trimIndent()

        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle("Informacje o skupisku")
        dialogBuilder.setMessage(clusterInfo)
        dialogBuilder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        dialogBuilder.show()
    }

    //endregion

    //region SaveData

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
        val metadataFile = File(getExternalFilesDir(null), "photo_metadata.json")
        val graphFile = File(getExternalFilesDir(null), "graph.json")
        val clustersFile = File(getExternalFilesDir(null), "clusters.json")

        if (photoDir.exists()) {
            val newPhotoDir = File(runFolder, "photos")
            moveFolder(photoDir, newPhotoDir)
        }

        if (metadataFile.exists()) {
            moveFile(metadataFile, File(runFolder, "photo_metadata.json"))
        }

        if (graphFile.exists()) {
            moveFile(graphFile, File(runFolder, "graph.json"))
        }

        if (clustersFile.exists()) {
            moveFile(clustersFile, File(runFolder, "clusters.json"))
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
        val metadataFile = File(getExternalFilesDir(null), "photo_metadata.json")
        val graphFile = File(getExternalFilesDir(null), "graph.json")
        val clustersFile = File(getExternalFilesDir(null), "clusters.json")

        val isMetadataFileAvailable = metadataFile.exists()

        val isGraphFileAvailable = graphFile.exists()

        val isclustersFileAvailable = clustersFile.exists()

        val arePhotosAvailable = photoDir.exists() && photoDir.listFiles()?.isNotEmpty() == true

        return isMetadataFileAvailable && isGraphFileAvailable && isclustersFileAvailable && arePhotosAvailable
    }

    //endregion
}
