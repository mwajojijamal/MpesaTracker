package com.mpesatracker.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.mpesatracker.data.MoneyUtils
import com.mpesatracker.data.entities.CategoryEntity
import com.mpesatracker.data.entities.TransactionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun exportToPdf(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>
    ): File = withContext(Dispatchers.IO) {
        val categoryMap = categories.associateBy { it.id }
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        var page = pdfDocument.startPage(pageInfo)
        var canvas: Canvas = page.canvas

        val titlePaint = Paint().apply {
            textSize = 18f
            isFakeBoldText = true
            color = android.graphics.Color.rgb(0, 91, 79) // Deep Teal
        }
        val headerPaint = Paint().apply {
            textSize = 10f
            isFakeBoldText = true
            color = android.graphics.Color.DKGRAY
        }
        val textPaint = Paint().apply {
            textSize = 9f
            color = android.graphics.Color.BLACK
        }

        var yPosition = 40f
        canvas.drawText("M-Pesa Personal Finance Statement", 40f, yPosition, titlePaint)
        yPosition += 20f
        canvas.drawText("Generated locally on: ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date())}", 40f, yPosition, headerPaint)
        yPosition += 30f

        // Table headers
        canvas.drawText("Date", 40f, yPosition, headerPaint)
        canvas.drawText("Type", 130f, yPosition, headerPaint)
        canvas.drawText("Counterparty", 220f, yPosition, headerPaint)
        canvas.drawText("Amount (Ksh)", 430f, yPosition, headerPaint)
        yPosition += 15f

        for (tx in transactions) {
            if (yPosition > 780f) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                yPosition = 40f
            }

            val dateStr = dateFormat.format(Date(tx.transactionDate))
            val type = tx.transactionType
            val party = (tx.recipient ?: tx.sender ?: "N/A").take(22)
            val amount = MoneyUtils.formatCentsToKsh(tx.amount)

            canvas.drawText(dateStr, 40f, yPosition, textPaint)
            canvas.drawText(type, 130f, yPosition, textPaint)
            canvas.drawText(party, 220f, yPosition, textPaint)
            canvas.drawText(amount, 430f, yPosition, textPaint)

            yPosition += 18f
        }

        pdfDocument.finishPage(page)

        val fileName = "Mpesa_Statement_${System.currentTimeMillis()}.pdf"
        val file = File(context.filesDir, fileName)
        FileOutputStream(file).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()
        file
    }
}