package com.firestarterpro.templartv.data.profile

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A local profile. Every history, shelf, favourite, row and shortcut belongs to exactly one profile
 * so histories never mix. Profiles are archived, never deleted.
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0,
)
