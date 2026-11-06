package com.mpesatracker.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categorisation_rules",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["matchingKeyword"], unique = true),
        Index(value = ["categoryId"])
    ]
)
data class CategorisationRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val matchingKeyword: String, // e.g., "NAIVAS", "KPLC", "UBER"
    val categoryId: Long,
    val priority: Int = 1,
    val isActive: Boolean = true
)