package com.pathfinder.data

import androidx.room.withTransaction
import com.pathfinder.data.db.AppDatabase
import com.pathfinder.data.db.TrackPointEntity
import com.pathfinder.data.db.TrackSource
import com.pathfinder.data.db.TrailEntity
import com.pathfinder.data.db.TrailPointEntity
import com.pathfinder.data.db.TrailSummary
import com.pathfinder.data.db.TrailWithPoints
import com.pathfinder.geo.DeclinationProvider
import com.pathfinder.geo.TrailCalculator
import com.pathfinder.geo.TrailEditing
import com.pathfinder.transfer.PackagePoint
import com.pathfinder.transfer.PackageTrackPoint
import com.pathfinder.transfer.TrailPackage
import kotlinx.coroutines.flow.Flow
import java.io.File

/** Dados de um novo ponto, antes dos cálculos. */
data class NewPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val name: String,
    val notes: String,
    val photoPath: String?,
)

class TrailRepository(
    private val db: AppDatabase,
    private val declination: DeclinationProvider,
) {
    private val trailDao = db.trailDao()
    private val pointDao = db.trailPointDao()
    private val trackDao = db.trackPointDao()

    fun observeTrails(): Flow<List<TrailSummary>> = trailDao.observeSummaries()

    fun observeTrail(id: Long): Flow<TrailWithPoints?> = trailDao.observeWithPoints(id)

    suspend fun getTrail(id: Long): TrailEntity? = trailDao.getById(id)

    suspend fun createTrail(name: String, deviationDistanceMeters: Int, deviationTimeSeconds: Int): Long {
        val now = System.currentTimeMillis()
        return trailDao.insert(
            TrailEntity(
                name = name.trim(),
                createdAt = now,
                updatedAt = now,
                deviationDistanceMeters = deviationDistanceMeters,
                deviationTimeSeconds = deviationTimeSeconds,
            )
        )
    }

    /** Altera nome e limites de desvio da trilha. */
    suspend fun updateTrail(id: Long, name: String, deviationDistanceMeters: Int, deviationTimeSeconds: Int) {
        val trail = trailDao.getById(id) ?: return
        trailDao.update(
            trail.copy(
                name = name.trim(),
                deviationDistanceMeters = deviationDistanceMeters,
                deviationTimeSeconds = deviationTimeSeconds,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun deleteTrail(id: Long) {
        val photos = pointDao.getForTrail(id).mapNotNull { it.photoPath }
        trailDao.deleteById(id)
        photos.forEach { File(it).delete() }
    }

    /**
     * Declinação usada pela trilha. Fica fixa depois do primeiro ponto, para que
     * todos os azimutes magnéticos (e a ficha impressa) usem o mesmo valor.
     */
    fun declinationFor(trail: TrailEntity, latitude: Double, longitude: Double, altitude: Double?): Double =
        trail.declinationDegrees
            ?: declination.declinationAt(latitude, longitude, altitude ?: 0.0, System.currentTimeMillis())

    /** Acrescenta um ponto ao final da trilha. */
    suspend fun addPoint(trailId: Long, point: NewPoint): Long = insertPoint(trailId, Int.MAX_VALUE, point)

    /**
     * Insere um ponto na posição [index] (0 = nova partida; maior ou igual ao
     * número de pontos = no final) e recalcula a trilha.
     */
    suspend fun insertPoint(trailId: Long, index: Int, point: NewPoint): Long = db.withTransaction {
        val trail = requireNotNull(trailDao.getById(trailId)) { "Trilha $trailId não existe" }
        val existing = pointDao.getForTrail(trailId)
        val id = pointDao.insert(
            TrailPointEntity(
                trailId = trailId,
                sequence = existing.size,
                name = point.name.trim(),
                notes = point.notes.trim(),
                latitude = point.latitude,
                longitude = point.longitude,
                altitude = point.altitude,
                photoPath = point.photoPath,
                createdAt = System.currentTimeMillis(),
            )
        )
        val inserted = requireNotNull(pointDao.getById(id))
        saveOrder(trail, TrailEditing.insert(existing, inserted, index))
        id
    }

    /** Altera nome, observações e foto. A foto antiga é apagada se for trocada. */
    suspend fun updatePointDetails(pointId: Long, name: String, notes: String, photoPath: String?) {
        val point = pointDao.getById(pointId) ?: return
        pointDao.update(listOf(point.copy(name = name.trim(), notes = notes.trim(), photoPath = photoPath)))
        touch(point.trailId)
        point.photoPath?.takeIf { it != photoPath }?.let { File(it).delete() }
    }

    /** Move o ponto para outra posição geográfica e recalcula a trilha. */
    suspend fun movePoint(pointId: Long, latitude: Double, longitude: Double) = db.withTransaction {
        val point = pointDao.getById(pointId) ?: return@withTransaction
        val trail = trailDao.getById(point.trailId) ?: return@withTransaction
        // A altitude da leitura original deixa de valer na nova posição.
        val moved = point.copy(latitude = latitude, longitude = longitude, altitude = null)
        saveOrder(trail, pointDao.getForTrail(trail.id).map { if (it.id == pointId) moved else it })
    }

    /** Muda a ordem do ponto na trilha e recalcula. */
    suspend fun reorderPoint(pointId: Long, newIndex: Int) = db.withTransaction {
        val point = pointDao.getById(pointId) ?: return@withTransaction
        val trail = trailDao.getById(point.trailId) ?: return@withTransaction
        saveOrder(trail, TrailEditing.move(pointDao.getForTrail(trail.id), { it.id }, pointId, newIndex))
    }

    suspend fun deletePoint(pointId: Long) {
        val point = pointDao.getById(pointId) ?: return
        db.withTransaction {
            val trail = trailDao.getById(point.trailId) ?: return@withTransaction
            pointDao.delete(point)
            saveOrder(trail, pointDao.getForTrail(trail.id))
        }
        point.photoPath?.let { File(it).delete() }
    }

    /**
     * Grava os pontos na ordem dada, recalculando UTM, azimutes e distâncias.
     * Fixa a declinação da trilha no primeiro ponto, se ainda não houver.
     */
    private suspend fun saveOrder(trail: TrailEntity, ordered: List<TrailPointEntity>) {
        val first = ordered.firstOrNull()
        val decl = when {
            trail.declinationDegrees != null -> trail.declinationDegrees
            first != null -> declinationFor(trail, first.latitude, first.longitude, first.altitude)
            else -> null
        }
        if (decl != null) {
            val renumbered = ordered.mapIndexed { i, p -> p.copy(sequence = i) }
            pointDao.update(TrailCalculator.recalculate(renumbered, decl))
        }
        trailDao.update(trail.copy(declinationDegrees = decl, updatedAt = System.currentTimeMillis()))
    }

    private suspend fun touch(trailId: Long) {
        trailDao.getById(trailId)?.let { trailDao.update(it.copy(updatedAt = System.currentTimeMillis())) }
    }

    /** Pacote para enviar a trilha pela rede ou exportar como arquivo. */
    suspend fun exportPackage(trailId: Long): TrailPackage? {
        val trail = trailDao.getById(trailId) ?: return null
        return TrailPackage(
            uuid = trail.uuid,
            name = trail.name,
            notes = trail.notes,
            createdAt = trail.createdAt,
            deviationDistanceMeters = trail.deviationDistanceMeters,
            deviationTimeSeconds = trail.deviationTimeSeconds,
            declinationDegrees = trail.declinationDegrees,
            points = pointDao.getForTrail(trailId).map {
                PackagePoint(it.name, it.notes, it.latitude, it.longitude, it.altitude)
            },
            track = trackDao.getTrack(trailId, TrackSource.CREATION).map {
                PackageTrackPoint(it.latitude, it.longitude, it.altitude, it.timestamp)
            },
        )
    }

    /** Resultado de uma importação. */
    data class ImportResult(val trailId: Long, val replaced: Boolean)

    /**
     * Importa a trilha. Se já existir uma com o mesmo uuid (reenvio do
     * coordenador), ela é substituída; os percursos de navegação são mantidos.
     */
    suspend fun importPackage(pkg: TrailPackage): ImportResult = db.withTransaction {
        val now = System.currentTimeMillis()
        val existing = trailDao.getByUuid(pkg.uuid)
        val first = pkg.points.firstOrNull()
        val decl = pkg.declinationDegrees
            ?: first?.let { declination.declinationAt(it.latitude, it.longitude, it.altitude ?: 0.0, now) }
        val trail = TrailEntity(
            id = existing?.id ?: 0,
            uuid = pkg.uuid,
            name = pkg.name.trim().ifEmpty { "Trilha importada" },
            notes = pkg.notes,
            createdAt = pkg.createdAt,
            updatedAt = now,
            deviationDistanceMeters = pkg.deviationDistanceMeters,
            deviationTimeSeconds = pkg.deviationTimeSeconds,
            declinationDegrees = decl,
        )
        val trailId = if (existing != null) {
            pointDao.getForTrail(existing.id).mapNotNull { it.photoPath }.forEach { File(it).delete() }
            pointDao.deleteForTrail(existing.id)
            trackDao.clear(existing.id, TrackSource.CREATION)
            trailDao.update(trail)
            existing.id
        } else {
            trailDao.insert(trail)
        }
        val points = pkg.points.mapIndexed { i, p ->
            TrailPointEntity(
                trailId = trailId,
                sequence = i,
                name = p.name,
                notes = p.notes,
                latitude = p.latitude,
                longitude = p.longitude,
                altitude = p.altitude,
                createdAt = now,
            )
        }
        pointDao.insertAll(if (decl != null) TrailCalculator.recalculate(points, decl) else points)
        trackDao.insertAll(
            pkg.track.map {
                TrackPointEntity(
                    trailId = trailId,
                    source = TrackSource.CREATION,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    altitude = it.altitude,
                    timestamp = it.timestamp,
                )
            }
        )
        ImportResult(trailId, replaced = existing != null)
    }

    fun observeTrack(trailId: Long, source: TrackSource): Flow<List<TrackPointEntity>> =
        trackDao.observeTrack(trailId, source)

    suspend fun addTrackPoint(point: TrackPointEntity) {
        trackDao.insert(point)
    }
}
