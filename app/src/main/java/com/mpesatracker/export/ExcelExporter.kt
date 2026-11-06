package com.mpesatracker.export

import android.content.Context
import com.mpesatracker.data.MoneyUtils
import com.mpesatracker.data.entities.CategoryEntity
import com.mpesatracker.data.entities.TransactionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExcelExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun exportToExcel(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>
    ): File = withContext(Dispatchers.IO) {
        val categoryMap = categories.associateBy { it.id }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("M-Pesa Transactions")

        // Header Row
        val headerRow = sheet.createRow(0)
        val headers = listOf("Date", "Code", "Type", "Counterparty", "Amount (Ksh)", "Charges (Ksh)", "Category", "Notes")
        headers.forEachIndexed { index, title ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(title)
        }

        // Data Rows
        transactions.forEachIndexed { index, tx ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(dateFormat.format(Date(tx.transactionDate)))
            row.createCell(1).setCellValue(tx.transactionCode ?: "")
            row.createCell(2).setCellValue(tx.transactionType)
            row.createCell(3).setCellValue(tx.recipient ?: tx.sender ?: "")
            row.createCell(4).setCellValue(MoneyUtils.centsToBigDecimal(tx.amount).toDouble())
            row.createCell(5).setCellValue(MoneyUtils.centsToBigDecimal(tx.charges).toDouble())
            row.createCell(6).setCellValue(tx.categoryId?.let { categoryMap[it]?.name } ?: "Uncategorised")
            row.createCell(7).setCellValue(tx.notes ?: "")
        }

        val fileName = "Mpesa_Report_${System.currentTimeMillis()}.xlsx"
        val file = File(context.filesDir, fileName)
        FileOutputStream(file).use { fos ->
            workbook.write(fos)
        }
        workbook.close()
        file
    }
}