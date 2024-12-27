package com.master.collectdatastreetview

import kotlinx.serialization.Serializable

@Serializable
data class GraphModel(
    val name: String,
    val date: String,
    var vertices: List<ClusterVertexModel>
)
