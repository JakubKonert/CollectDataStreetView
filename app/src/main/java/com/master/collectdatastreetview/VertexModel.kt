package com.master.collectdatastreetview

import kotlinx.serialization.Serializable

@Serializable
data class VertexModel(
    val photo: PhotoModel,
    var edges: MutableList<String>
)