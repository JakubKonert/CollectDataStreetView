package com.master.collectdatastreetview
import kotlinx.serialization.Serializable

@Serializable
data class ClusterVertexModel(
    val id: String,
    var vertices: List<VertexModel>,
    var edges: List<ClusterVertexModel>
)
