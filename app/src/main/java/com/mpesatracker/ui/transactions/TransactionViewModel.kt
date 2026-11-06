package com.mpesatracker.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mpesatracker.data.MoneyUtils
import com.mpesatracker.data.entities.CategoryEntity
import com.mpesatracker.data.entities.TransactionEntity
import com.mpesatracker.data.entities.TransactionStatus
import com.mpesatracker.data.entities.TransactionType
import com.mpesatracker.data.repository.MpesaRepository
import com.mpesatracker.sms.SmsInboxReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class DateFilterOption(val label: String) {
    ALL("All Time"),
    TODAY("Today"),
    LAST_7_DAYS("Last 7 Days"),
    THIS_MONTH("This Month")
}

data class TransactionFilterState(
    val searchQuery: String = "",
    val selectedType: String? = null,
    val selectedCategoryId: Long? = null,
    val dateFilter: DateFilterOption = DateFilterOption.ALL,
    val minAmountKsh: String = "",
    val maxAmountKsh: String = ""
)

data class TransactionsUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val categoryMap: Map<Long, CategoryEntity> = emptyMap(),
    val filter: TransactionFilterState = TransactionFilterState(),
    val isSyncingSms: Boolean = false,
    val syncStatusMessage: String? = null
)

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val repository: MpesaRepository,
    private val smsInboxReader: SmsInboxReader
) : ViewModel() {

    private val filterState = MutableStateFlow(TransactionFilterState())
    private val isSyncingState = MutableStateFlow(false)
    private val syncMessageState = MutableStateFlow<String?>(null)

    private val _hasSmsPermission = MutableStateFlow(smsInboxReader.hasSmsPermissions())
    val hasSmsPermission: StateFlow<Boolean> = _hasSmsPermission.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultCategoriesSeeded()
            if (_hasSmsPermission.value) {
                syncSmsInbox()
            }
        }
    }

    val uiState: StateFlow<TransactionsUiState> = combine(
        repository.observeAllTransactions(),
        repository.observeAllCategories(),
        filterState,
        isSyncingState,
        syncMessageState
    ) { transactions, categories, filter, isSyncing, syncMsg ->
        val categoryMap = categories.associateBy { it.id }
        val minCents = if (filter.minAmountKsh.isNotBlank()) MoneyUtils.parseKshToCents(filter.minAmountKsh) else null
        val maxCents = if (filter.maxAmountKsh.isNotBlank()) MoneyUtils.parseKshToCents(filter.maxAmountKsh) else null
        val cutoffTimestamp = getStartTimestampForFilter(filter.dateFilter)

        val filtered = transactions.filter { tx ->
            val matchesQuery = if (filter.searchQuery.isBlank()) {
                true
            } else {
                val q = filter.searchQuery.trim()
                (tx.recipient?.contains(q, ignoreCase = true) == true) ||
                    (tx.sender?.contains(q, ignoreCase = true) == true) ||
                    (tx.transactionCode?.contains(q, ignoreCase = true) == true) ||
                    (tx.notes?.contains(q, ignoreCase = true) == true)
            }

            val matchesType = filter.selectedType == null || tx.transactionType == filter.selectedType
            val matchesCategory = filter.selectedCategoryId == null || tx.categoryId == filter.selectedCategoryId
            val matchesDate = cutoffTimestamp == null || tx.transactionDate >= cutoffTimestamp
            val matchesMinAmount = minCents == null || tx.amount >= minCents
            val matchesMaxAmount = maxCents == null || tx.amount <= maxCents

            matchesQuery && matchesType && matchesCategory && matchesDate && matchesMinAmount && matchesMaxAmount
        }

        TransactionsUiState(
            transactions = filtered,
            categories = categories,
            categoryMap = categoryMap,
            filter = filter,
            isSyncingSms = isSyncing,
            syncStatusMessage = syncMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionsUiState()
    )

    fun onPermissionResult(granted: Boolean) {
        _hasSmsPermission.value = granted || smsInboxReader.hasSmsPermissions()
        if (_hasSmsPermission.value) {
            syncSmsInbox()
        }
    }

    fun syncSmsInbox() {
        viewModelScope.launch {
            isSyncingState.value = true
            val result = smsInboxReader.syncMpesaInbox()
            isSyncingState.value = false
            syncMessageState.value = if (result.newlyImportedCount > 0) {
                "Imported ${result.newlyImportedCount} new M-Pesa transactions (scanned ${result.scannedCount})."
            } else {
                "Inbox up to date (${result.scannedCount} M-Pesa SMS scanned)."
            }
        }
    }

    fun importPastedSms(rawSms: String) {
        viewModelScope.launch {
            val success = smsInboxReader.ingestRawSmsText(rawSms)
            syncMessageState.value = if (success) {
                "M-Pesa SMS parsed and added successfully!"
            } else {
                "Could not parse SMS or transaction code already exists."
            }
        }
    }

    fun dismissSyncMessage() {
        syncMessageState.value = null
    }

    fun updateSearchQuery(query: String) {
        filterState.value = filterState.value.copy(searchQuery = query)
    }

    fun updateTypeFilter(typeName: String?) {
        filterState.value = filterState.value.copy(selectedType = typeName)
    }

    fun updateCategoryFilter(categoryId: Long?) {
        filterState.value = filterState.value.copy(selectedCategoryId = categoryId)
    }

    fun updateDateFilter(option: DateFilterOption) {
        filterState.value = filterState.value.copy(dateFilter = option)
    }

    fun updateAmountRange(minKsh: String, maxKsh: String) {
        filterState.value = filterState.value.copy(minAmountKsh = minKsh, maxAmountKsh = maxKsh)
    }

    fun clearFilters() {
        filterState.value = TransactionFilterState()
    }

    fun saveTransaction(
        existingId: Long = 0L,
        transactionCode: String?,
        amountKsh: String,
        chargesKsh: String,
        transactionType: TransactionType,
        counterparty: String,
        categoryId: Long?,
        notes: String?,
        transactionDate: Long = System.currentTimeMillis(),
        rawMessage: String = "Manual Transaction Entry",
        saveRuleForCounterparty: Boolean = false
    ) {
        viewModelScope.launch {
            val amountCents = MoneyUtils.parseKshToCents(amountKsh)
            if (amountCents <= 0L) return@launch
            val chargesCents = MoneyUtils.parseKshToCents(chargesKsh)

            val isIncoming = transactionType == TransactionType.RECEIVED
            val entity = TransactionEntity(
                id = existingId,
                transactionCode = transactionCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
                amount = amountCents,
                transactionType = transactionType.name,
                sender = if (isIncoming) counterparty.trim().ifEmpty { null } else null,
                recipient = if (!isIncoming) counterparty.trim().ifEmpty { null } else null,
                charges = chargesCents,
                categoryId = categoryId,
                accountReference = null,
                transactionDate = transactionDate,
                rawMessage = rawMessage,
                status = TransactionStatus.COMPLETED.name,
                isVerified = true,
                notes = notes?.trim()?.ifEmpty { null }
            )

            if (existingId == 0L) {
                repository.insertTransaction(entity)
            } else {
                repository.updateTransaction(entity)
            }

            if (saveRuleForCounterparty && categoryId != null && counterparty.isNotBlank()) {
                repository.saveMerchantRule(counterparty, categoryId)
            }
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    private fun getStartTimestampForFilter(option: DateFilterOption): Long? {
        val cal = Calendar.getInstance()
        return when (option) {
            DateFilterOption.ALL -> null
            DateFilterOption.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            DateFilterOption.LAST_7_DAYS -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.timeInMillis
            }
            DateFilterOption.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
        }
    }
}