package com.master.collectdatastreetview
import kotlinx.serialization.Serializable

@Serializable
data class PhotoModel(
    val id: String,
    var clusterId: String?,
    val fileName: String,
    val latitude: Double,
    val longitude: Double,
    val direction: String,
    val directionVal: Float,
)
