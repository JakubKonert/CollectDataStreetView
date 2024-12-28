package com.master.collectdatastreetview

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MakeGraphActivity : AppCompatActivity() {

    //region Props
    private lateinit var mapView: MapView
    private lateinit var btnBack: Button
    private lateinit var btnSaveGraph: Button

    private var clusters: MutableList<ClusterModel> = mutableListOf()
    private var graphClusters: MutableList<ClusterVertexModel> = mutableListOf()
    private val mapper = Mapper()

    private var selectedCluster: ClusterVertexModel? = null
    private val connectionLines = mutableMapOf<Polyline, Pair<ClusterVertexModel, ClusterVertexModel>>()

    //endregion

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osm_prefs", MODE_PRIVATE))
        setContentView(R.layout.activity_make_graph)

        mapView = findViewById(R.id.mapView)
        btnBack = findViewById(R.id.btn_back)
        btnSaveGraph = findViewById(R.id.btn_save_graph)

        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(20.0)

        btnBack.setOnClickListener {
            finish()
        }

        btnSaveGraph.setOnClickListener{
            saveGraphToFile()
        }

        loadClustersToMemory()
        loadOrInitializeGraph()
    }

    //region LoadData

    private fun loadClustersToMemory() {
        val clusterFile = File(getExternalFilesDir(null), "clusters.json")

        if (!clusterFile.exists()) {
            return
        }

        try {
            val clusterListFromFile: List<ClusterModel> = Json.decodeFromString(clusterFile.readText())

            for (cluster in clusterListFromFile) {
                if (clusters.none { it.id == cluster.id }) {
                    clusters.add(cluster)
                }
            }

            Toast.makeText(this, "Klastry wczytane do pamięci.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Błąd podczas wczytywania klastrów.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadOrInitializeGraph() {
        val graphFile = File(getExternalFilesDir(null), "graph.json")
        if (graphFile.exists() && graphFile.length() > 0L) {
            try {
                val graphModel = Json.decodeFromString<GraphModel>(graphFile.readText())

                graphClusters.clear()
                graphClusters.addAll(graphModel.vertices)

                for (cluster in graphClusters) {
                    displayClusterOnMap(cluster)
                }

                for (cluster in graphClusters) {
                    for (connectedClusterId in cluster.edges) {
                        val connectedCluster = graphClusters.find { it.id == connectedClusterId }
                        if (connectedCluster != null) {
                            connectClusters(cluster, connectedCluster)
                        }
                    }
                }

                Toast.makeText(this, "Wczytano graf z pliku.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Błąd podczas wczytywania grafu. Wyświetlanie domyślne.", Toast.LENGTH_SHORT).show()
                initializeGraphFromClusters()
            }
        } else {
            initializeGraphFromClusters()
        }
        graphClusters.firstOrNull()?.vertices?.firstOrNull()?.let{
            mapView.controller.setCenter(GeoPoint(it.photo.latitude, it.photo.longitude))
        }
    }

    //endregion

    //region SaveData

        private fun saveGraphToFile() {
            val graphFile = File(getExternalFilesDir(null), "graph.json")
            val currentDate = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(
                Date()
            )

            try {
                val graphModel = GraphModel(
                    name = "Graph_$currentDate",
                    date = currentDate,
                    vertices = graphClusters
                )

                val graphData = Json.encodeToString(graphModel)
                graphFile.writeText(graphData)

                Toast.makeText(this, "Zapisano do graph.json", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Nie udało się zapisać.", Toast.LENGTH_SHORT).show()
            }
        }

    //endregion

    //region Cluster

    private fun initializeGraphFromClusters() {
        mapClustersToGraph()
        for (cluster in graphClusters) {
            displayClusterOnMap(cluster)
        }
    }

    private fun mapClustersToGraph() {
        graphClusters.clear()
        for (cluster in clusters) {
            if (graphClusters.any { it.id == cluster.id }) continue

            val vertices = cluster.photos.map { photo ->
                mapper.mapPhotoModelToVertexModel(photo)
            }

            for (vertex in vertices) {
                vertex.edges.addAll(
                    vertices.filter { it.photo.id != vertex.photo.id }
                        .map { it.photo.id }
                )
            }

            graphClusters.add(
                ClusterVertexModel(
                    id = cluster.id,
                    vertices = vertices,
                    edges = emptyList()
                )
            )
        }
    }

    //endregion

    //region Map

    private fun displayClusterOnMap(cluster: ClusterVertexModel) {
        val geoPoints = cluster.vertices.map { GeoPoint(it.photo.latitude, it.photo.longitude) }
        if (geoPoints.isNotEmpty()) {
            val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPointsSafe(geoPoints)
            val center = boundingBox.centerWithDateLine

            val radius = geoPoints.maxOfOrNull {
                center.distanceToAsDouble(it)
            } ?: 0.0

            val circleOverlay = Polygon(mapView).apply {
                id = cluster.id
                points = createCircle(center, radius + 5)
                fillPaint.color = 0x44FF0000
                outlinePaint.color = 0x00FF0000
                outlinePaint.strokeWidth = 2.0f
                setOnClickListener { _, _, _ ->
                    onClusterClicked(cluster.id)
                    true
                }
            }

            mapView.overlays.add(circleOverlay)
        }
    }

    private fun onClusterClicked(clusterId: String) {
        val clickedCluster = graphClusters.find { it.id == clusterId }

        if (clickedCluster != null) {
            if (selectedCluster == null) {
                selectedCluster = clickedCluster
                Toast.makeText(this, "Wybrano skupisko: ${clickedCluster.id}", Toast.LENGTH_SHORT).show()
            } else {
                if (selectedCluster != clickedCluster) {
                    connectClusters(selectedCluster!!, clickedCluster)
                    selectedCluster = null
                } else {
                    Toast.makeText(this, "Anulowanie wybrania", Toast.LENGTH_SHORT).show()
                    selectedCluster = null
                }
            }
        }
    }

    private fun connectClusters(cluster1: ClusterVertexModel, cluster2: ClusterVertexModel) {
        if (!cluster1.edges.contains(cluster2.id)) {
            cluster1.edges = cluster1.edges.plus(cluster2.id)
        }

        if (!cluster2.edges.contains(cluster1.id)) {
            cluster2.edges = cluster2.edges.plus(cluster1.id)
        }

        if (connectionLines.values.any { it == Pair(cluster1, cluster2) || it == Pair(cluster2, cluster1) }) {
            Toast.makeText(this, "Połączenie już istnieje między: ${cluster1.id} and ${cluster2.id}", Toast.LENGTH_SHORT).show()
            return
        }

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
                removeConnection(this)
                true
            }
        }
        polyline.outlinePaint.apply {
            color = android.graphics.Color.RED
            strokeWidth = 5f
            isAntiAlias = true
        }

        connectionLines[polyline] = Pair(cluster1, cluster2)
        mapView.overlays.add(polyline)
        mapView.invalidate()

        Toast.makeText(this, "Połączono: ${cluster1.id} and ${cluster2.id}", Toast.LENGTH_SHORT).show()
    }

    private fun removeConnection(line: Polyline) {
        val clusters = connectionLines[line]

        if (clusters != null) {
            val (cluster1, cluster2) = clusters

            cluster1.edges = cluster1.edges.filter { it != cluster2.id }
            cluster2.edges = cluster2.edges.filter { it != cluster1.id }

            mapView.overlays.remove(line)
            connectionLines.remove(line)
            mapView.invalidate()

            Toast.makeText(this, "Usunięto połączenie: ${cluster1.id} and ${cluster2.id}", Toast.LENGTH_SHORT).show()
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

    //endregion

}
