package com.mpesatracker.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["transactionCode"], unique = true),
        Index(value = ["categoryId"]),
        Index(value = ["transactionDate"]),
        Index(value = ["transactionType"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transactionCode: String?,      // Unique M-Pesa code e.g. "ABC123XYZ", nullable for manual entries
    val amount: Long,                  // Stored in minor units (cents): Ksh 500.00 = 50000L
    val transactionType: String,       // Matches TransactionType.name (RECEIVED, SEND, BUY_GOODS, etc.)
    val sender: String?,               // Populated when money is received
    val recipient: String?,            // Populated when money is sent, paid, or withdrawn
    val charges: Long = 0L,            // M-Pesa fee in minor units (cents): Ksh 12.00 = 1200L
    val categoryId: Long? = null,      // Links to CategoryEntity.id
    val accountReference: String? = null, // PayBill account number where applicable
    val transactionDate: Long,         // Epoch milliseconds (UTC)
    val rawMessage: String,            // Original SMS text (or "Manual Entry")
    val status: String = TransactionStatus.COMPLETED.name,
    val isVerified: Boolean = true,
    val notes: String? = null
)