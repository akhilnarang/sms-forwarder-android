package dev.akhilnarang.smsforwarder.data

import kotlinx.coroutines.flow.Flow

class DestinationRepository(private val destinationDao: DestinationDao) {
    fun getAllDestinations(): Flow<List<DestinationEntity>> = destinationDao.getAll()

    suspend fun getDestinationById(id: Long): DestinationEntity? = destinationDao.getById(id)

    suspend fun addDestination(destination: DestinationEntity): Long {
        return destinationDao.insert(destination)
    }

    suspend fun updateDestination(destination: DestinationEntity) {
        destinationDao.update(destination)
    }

    suspend fun deleteDestination(destination: DestinationEntity) {
        destinationDao.delete(destination)
    }
}
