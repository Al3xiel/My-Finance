package com.al3xiel.my_finances.data.projection

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/** Hasta qué evento llegó cada proyector y con qué versión; `rebuilt_at` solo cambia en un replay. */
@Entity(tableName = "projection_state")
data class ProjectionStateEntity(
    @PrimaryKey @ColumnInfo(name = "projector_name") val projectorName: String,
    @ColumnInfo(name = "projector_version") val projectorVersion: Int,
    @ColumnInfo(name = "last_sequence") val lastSequence: Long,
    @ColumnInfo(name = "rebuilt_at") val rebuiltAt: Long?,
)

@Dao
interface ProjectionStateDao {
    @Upsert suspend fun upsert(state: ProjectionStateEntity)

    @Query("SELECT * FROM projection_state WHERE projector_name = :name")
    suspend fun state(name: String): ProjectionStateEntity?
}
