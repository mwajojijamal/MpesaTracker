package com.mpesatracker.ui.transactions

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mpesatracker.data.MoneyUtils
import com.mpesatracker.data.entities.CategoryEntity
import com.mpesatracker.data.entities.FinancialClass
import com.mpesatracker.data.entities.TransactionEntity
import com.mpesatracker.data.entities.TransactionType
import com.mpesatracker.ui.theme.ExpenseRed
import com.mpesatracker.ui.theme.IncomeGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hasSmsPermission by viewModel.hasSmsPermission.collectAsStateWithLifecycle()

    var showFilters by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showPasteSmsDialog by remember { mutableStateOf(false) }
    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.READ_SMS] == true &&
            permissions[Manifest.permission.RECEIVE_SMS] == true
        viewModel.onPermissionResult(granted)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions (${state.transactions.size})", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { showPasteSmsDialog = true }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste M-Pesa SMS")
                    }
                    IconButton(
                        onClick = {
                            if (hasSmsPermission) {
                                viewModel.syncSmsInbox()
                            } else {
                                showPermissionRationaleDialog = true
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync M-Pesa SMS Inbox")
                    }
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Toggle Filters")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (state.isSyncingSms) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Sync Status Banner
            state.syncStatusMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::dismissSyncMessage) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            // Offline Privacy & Permission Explanation Card (Shown when SMS permission is not granted)
            if (!hasSmsPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Enable Offline M-Pesa Auto-Detection",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "This app has zero internet access. Granting SMS permission allows it to read messages from MPESA locally on your phone and convert them into financial records.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(onClick = { showPermissionRationaleDialog = true }) {
                                Text("Grant SMS Access")
                            }
                            TextButton(onClick = { showPasteSmsDialog = true }) {
                                Text("Or Paste SMS Manually")
                            }
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = state.filter.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                placeholder = { Text("Search name, M-Pesa code, or note...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.filter.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Expandable Filter Panel
            AnimatedVisibility(visible = showFilters) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Filter Transactions", fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = viewModel::clearFilters) {
                                Text("Reset All")
                            }
                        }

                        Text("Date Period", style = MaterialTheme.typography.labelSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DateFilterOption.entries.forEach { option ->
                                FilterChip(
                                    selected = state.filter.dateFilter == option,
                                    onClick = { viewModel.updateDateFilter(option) },
                                    label = { Text(option.label) }
                                )
                            }
                        }

                        Text("Transaction Type", style = MaterialTheme.typography.labelSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = state.filter.selectedType == null,
                                onClick = { viewModel.updateTypeFilter(null) },
                                label = { Text("All Types") }
                            )
                            TransactionType.entries.forEach { type ->
                                FilterChip(
                                    selected = state.filter.selectedType == type.name,
                                    onClick = { viewModel.updateTypeFilter(type.name) },
                                    label = { Text(type.displayName) }
                                )
                            }
                        }

                        Text("Category", style = MaterialTheme.typography.labelSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = state.filter.selectedCategoryId == null,
                                onClick = { viewModel.updateCategoryFilter(null) },
                                label = { Text("All Categories") }
                            )
                            state.categories.forEach { cat ->
                                FilterChip(
                                    selected = state.filter.selectedCategoryId == cat.id,
                                    onClick = { viewModel.updateCategoryFilter(cat.id) },
                                    label = { Text(cat.name) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = state.filter.minAmountKsh,
                                onValueChange = { viewModel.updateAmountRange(it, state.filter.maxAmountKsh) },
                                label = { Text("Min Ksh") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = state.filter.maxAmountKsh,
                                onValueChange = { viewModel.updateAmountRange(state.filter.minAmountKsh, it) },
                                label = { Text("Max Ksh") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (state.transactions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No transactions found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sync your M-Pesa SMS inbox, paste an SMS, or tap + to add one manually.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(state.transactions, key = { it.id }) { tx ->
                        TransactionCard(
                            transaction = tx,
                            category = tx.categoryId?.let { state.categoryMap[it] },
                            onClick = { selectedTransaction = tx }
                        )
                    }
                }
            }
        }
    }

    // Privacy Rationale Dialog before launching Android OS permission prompt
    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionRationaleDialog = false },
            title = { Text("100% Offline SMS Access", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "M-Pesa Finance Tracker needs READ_SMS and RECEIVE_SMS permissions strictly to:\n\n" +
                        "1. Detect incoming confirmation messages from MPESA.\n" +
                        "2. Scan your existing MPESA messages to build your local history.\n\n" +
                        "This app does NOT have internet permission. Your messages and financial records never leave your phone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationaleDialog = false
                        smsPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_SMS,
                                Manifest.permission.RECEIVE_SMS
                            )
                        )
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationaleDialog = false }) {
                    Text("Not Now")
                }
            }
        )
    }

    // Paste Raw M-Pesa SMS Dialog (for testing or manual SMS import)
    if (showPasteSmsDialog) {
        var rawSmsText by remember {
            mutableStateOf(
                "ABC123XYZ Confirmed. Ksh500.00 sent to NAIVAS SUPERMARKET on 31/10/2026 at 10:30 AM. Transaction cost Ksh0.00."
            )
        }
        AlertDialog(
            onDismissRequest = { showPasteSmsDialog = false },
            title = { Text("Paste M-Pesa SMS", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste any M-Pesa confirmation SMS below to parse and categorise it automatically:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = rawSmsText,
                        onValueChange = { rawSmsText = it },
                        label = { Text("M-Pesa SMS Message") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importPastedSms(rawSmsText)
                        showPasteSmsDialog = false
                    }
                ) {
                    Text("Parse & Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteSmsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddDialog) {
        AddOrEditTransactionDialog(
            initialTransaction = null,
            categories = state.categories,
            onDismiss = { showAddDialog = false },
            onSave = { id, code, amount, charges, type, counterparty, catId, notes, saveRule ->
                viewModel.saveTransaction(
                    existingId = id,
                    transactionCode = code,
                    amountKsh = amount,
                    chargesKsh = charges,
                    transactionType = type,
                    counterparty = counterparty,
                    categoryId = catId,
                    notes = notes,
                    saveRuleForCounterparty = saveRule
                )
            }
        )
    }

    selectedTransaction?.let { tx ->
        AddOrEditTransactionDialog(
            initialTransaction = tx,
            categories = state.categories,
            onDismiss = { selectedTransaction = null },
            onSave = { id, code, amount, charges, type, counterparty, catId, notes, saveRule ->
                viewModel.saveTransaction(
                    existingId = id,
                    transactionCode = code,
                    amountKsh = amount,
                    chargesKsh = charges,
                    transactionType = type,
                    counterparty = counterparty,
                    categoryId = catId,
                    notes = notes,
                    transactionDate = tx.transactionDate,
                    rawMessage = tx.rawMessage,
                    saveRuleForCounterparty = saveRule
                )
            },
            onDelete = { viewModel.deleteTransaction(it) }
        )
    }
}

@Composable
fun TransactionCard(
    transaction: TransactionEntity,
    category: CategoryEntity?,
    onClick: () -> Unit
) {
    val txType = remember(transaction.transactionType) {
        runCatching { TransactionType.valueOf(transaction.transactionType) }
            .getOrDefault(TransactionType.OTHER)
    }
    val isIncome = txType.financialClass == FinancialClass.INCOME
    val amountColor = if (isIncome) IncomeGreen else ExpenseRed
    val signPrefix = if (isIncome) "+" else "-"
    val counterparty = transaction.recipient ?: transaction.sender ?: txType.displayName
    val formattedDate = remember(transaction.transactionDate) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(transaction.transactionDate))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = counterparty,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = category?.name ?: txType.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                if (!transaction.transactionCode.isNullOrBlank()) {
                    Text(
                        text = "Ref: ${transaction.transactionCode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$signPrefix ${MoneyUtils.formatCentsToKsh(transaction.amount)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                if (transaction.charges > 0L) {
                    Text(
                        text = "Fee: ${MoneyUtils.formatCentsToKsh(transaction.charges)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}