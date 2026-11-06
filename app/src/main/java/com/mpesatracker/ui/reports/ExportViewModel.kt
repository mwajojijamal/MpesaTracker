package com.mpesatracker.ui.reports

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mpesatracker.data.repository.MpesaRepository
import com.mpesatracker.export.CsvExporter
import com.mpesatracker.export.ExcelExporter
import com.mpesatracker.export.PdfExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ExportUiState(
    val transactionCount: Int = 0,
    val isExporting: Boolean = false,
    val exportStatusMessage: String? = null
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val repository: MpesaRepository,
    private val csvExporter: CsvExporter,
    private val excelExporter: ExcelExporter,
    private val pdfExporter: PdfExporter,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val isExportingState = MutableStateFlow(false)
    private val statusMessageState = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ExportUiState> = combine(
        repository.observeAllTransactions(),
        isExportingState,
        statusMessageState
    ) { transactions, isExporting, statusMsg ->
        ExportUiState(
            transactionCount = transactions.size,
            isExporting = isExporting,
            exportStatusMessage = statusMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ExportUiState()
    )

    fun exportCsv(contextActivity: Context) {
        viewModelScope.launch {
            isExportingState.value = true
            val txs = repository.observeAllTransactions()
            // We take a snapshot of current transactions and categories
            val categories = repository.getAllCategories()
            // Wait, collect first emission or query
            // Let's use repository.getAllCategories() and query transactions directly if needed,
            // but since transactions is a Flow, let's fetch them via a suspending query or collect.
        }
    }

    fun generateExportFile(fileType: String, contextActivity: Context) {
        viewModelScope.launch {
            isExportingState.value = true
            statusMessageState.value = null
            try {
                // Fetch transactions and categories directly
                val txs = repository.observeAllTransactions()
                // For simplicity, let's fetch a list of transactions via repository or Dao.
                // Since MpesaRepository doesn't have a direct suspending getAllTransactions yet,
                // let's add it or collect 1 emission:
                val allTxs = mutableListOf<com.mpesatracker.data.entities.TransactionEntity>()
                repository.observeAllTransactions().collect { list ->
                    allTxs.clear()
                    allTxs.addAll(list)
                    throw kotlin.coroutines.cancellation.CancellationException("Collected")
                }
            } catch (e: Exception) {
                // handled via collection
            }
        }
    }

    fun exportFormat(format: String, contextActivity: Context) {
        viewModelScope.launch {
            isExportingState.value = true
            try {
                // To get list synchronously:
                val categories = repository.getAllCategories()
                // Let's fetch transactions using a flow first emission
                var txs = emptyList<com.mpesatracker.data.entities.TransactionEntity>()
                repository.observeAllTransactions().collect {
                    txs = it
                    throw kotlin.coroutines.cancellation.CancellationException("Fetched")
                }

                val file: File = when (format.uppercase()) {
                    "CSV" -> csvExporter.exportToCsv(txs, categories)
                    "EXCEL" -> excelExporter.exportToExcel(txs, categories)
                    "PDF" -> pdfExporter.exportToPdf(txs, categories)
                    else -> throw IllegalArgumentException("Unknown format")
                }

                isExportingState.value = false
                statusMessageState.value = "Successfully generated ${file.name}"
                shareFile(file, contextActivity)
            } catch (e: Exception) {
                isExportingState.value = false
                statusMessageState.value = "Export error: ${e.localizedMessage}"
            }
        }
    }

    private fun shareFile(file: File, contextActivity: Context) {
        val uri = FileProvider.getUriForFile(
            contextActivity,
            "${contextActivity.packageName}.fileprovider",
            file
        )
        val mimeType = when {
            file.name.endsWith(".csv") -> "text/csv"
            file.name.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            file.name.endsWith(".pdf") -> "application/pdf"
            else -> "*/*"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        contextActivity.startActivity(Intent.createChooser(intent, "Share M-Pesa Report"))
    }

    fun dismissStatus() {
        statusMessageState.value = null
    }
}