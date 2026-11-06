package com.mpesatracker.export

import android.content.Context
import com.mpesatracker.data.MoneyUtils
import com.mpesatracker.data.entities.CategoryEntity
import com.mpesatracker.data.entities.TransactionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun exportToCsv(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>
    ): File = withContext(Dispatchers.IO) {
        val categoryMap = categories.associateBy { it.id }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val fileName = "Mpesa_Transactions_${System.currentTimeMillis()}.csv"
        val file = File(context.filesDir, fileName)

        FileWriter(file).use { writer ->
            // CSV Header
            writer.append("Date,Code,Type,Counterparty,Amount (Ksh),Charges (Ksh),Category,Notes\n")

            for (tx in transactions) {
                val dateStr = dateFormat.format(Date(tx.transactionDate))
                val code = tx.transactionCode ?: ""
                val type = tx.transactionType
                val party = (tx.recipient ?: tx.sender ?: "").replace(",", " ")
                val amount = MoneyUtils.centsToBigDecimal(tx.amount).toPlainString()
                val charges = MoneyUtils.centsToBigDecimal(tx.charges).toPlainString()
                val category = (tx.categoryId?.let { categoryMap[it]?.name } ?: "Uncategorised").replace(",", " ")
                val notes = (tx.notes ?: "").replace(",", " ").replace("\n", " ")

                writer.append("\"$dateStr\",\"$code\",\"$type\",\"$party\",$amount,$charges,\"$category\",\"$notes\"\n")
            }
            writer.flush()
        }
        file
    }
}