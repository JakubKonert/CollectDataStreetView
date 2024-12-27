package com.master.collectdatastreetview
import kotlinx.serialization.Serializable

@Serializable
data class ClusterModel(
    val id: String,
    var photos: List<PhotoModel>,
)
