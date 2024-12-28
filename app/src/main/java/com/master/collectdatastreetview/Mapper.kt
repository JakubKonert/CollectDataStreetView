package com.master.collectdatastreetview

class Mapper {

    public fun mapClusterModelToClusterVertexModel(clusterModel: ClusterModel):ClusterVertexModel
    {
        val verticesList = clusterModel.photos.map { mapPhotoModelToVertexModel(it) }
        return ClusterVertexModel(id = clusterModel.id, vertices = verticesList, edges = mutableListOf())
    }

    public fun mapPhotoModelToVertexModel(photoModel: PhotoModel ):VertexModel{
        return VertexModel(photo = photoModel, edges = mutableListOf())
    }
}