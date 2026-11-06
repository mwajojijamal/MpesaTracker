package com.mpesatracker.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mpesatracker.data.MoneyUtils
import com.mpesatracker.data.entities.CategoryEntity
import com.mpesatracker.data.entities.TransactionEntity
import com.mpesatracker.data.entities.TransactionType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddOrEditTransactionDialog(
    initialTransaction: TransactionEntity? = null,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (
        existingId: Long,
        code: String?,
        amountKsh: String,
        chargesKsh: String,
        type: TransactionType,
        counterparty: String,
        categoryId: Long?,
        notes: String?,
        saveMerchantRule: Boolean
    ) -> Unit,
    onDelete: ((TransactionEntity) -> Unit)? = null
) {
    val isEditing = initialTransaction != null

    var code by remember { mutableStateOf(initialTransaction?.transactionCode ?: "") }
    var amountKsh by remember {
        mutableStateOf(
            initialTransaction?.let { MoneyUtils.centsToBigDecimal(it.amount).toPlainString() } ?: ""
        )
    }
    var chargesKsh by remember {
        mutableStateOf(
            initialTransaction?.let { MoneyUtils.centsToBigDecimal(it.charges).toPlainString() } ?: "0.00"
        )
    }
    var selectedType by remember {
        mutableStateOf(
            initialTransaction?.let {
                runCatching { TransactionType.valueOf(it.transactionType) }.getOrDefault(TransactionType.SEND)
            } ?: TransactionType.BUY_GOODS
        )
    }
    var counterparty by remember {
        mutableStateOf(initialTransaction?.recipient ?: initialTransaction?.sender ?: "")
    }
    var selectedCategoryId by remember {
        mutableStateOf(initialTransaction?.categoryId ?: categories.firstOrNull()?.id)
    }
    var notes by remember { mutableStateOf(initialTransaction?.notes ?: "") }
    var saveMerchantRule by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit / View Transaction" else "Add Manual Transaction",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Transaction Type", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TransactionType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.displayName) }
                        )
                    }
                }

                OutlinedTextField(
                    value = amountKsh,
                    onValueChange = { amountKsh = it },
                    label = { Text("Amount (Ksh)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = chargesKsh,
                    onValueChange = { chargesKsh = it },
                    label = { Text("Transaction Charges (Ksh)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = counterparty,
                    onValueChange = { counterparty = it },
                    label = {
                        Text(
                            if (selectedType == TransactionType.RECEIVED) "Sender Name"
                            else "Recipient / Merchant Name"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("M-Pesa Code (Optional, e.g. SLK45ABC12)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Category", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.name) }
                        )
                    }
                }

                if (counterparty.isNotBlank() && selectedCategoryId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = saveMerchantRule,
                            onCheckedChange = { saveMerchantRule = it }
                        )
                        Text(
                            text = "Always categorise \"$counterparty\" under this category",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (initialTransaction != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Original SMS / Source:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = initialTransaction.rawMessage,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (amountKsh.isNotBlank()) {
                        onSave(
                            initialTransaction?.id ?: 0L,
                            code,
                            amountKsh,
                            chargesKsh,
                            selectedType,
                            counterparty,
                            selectedCategoryId,
                            notes,
                            saveMerchantRule
                        )
                        onDismiss()
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (initialTransaction != null && onDelete != null) {
                    TextButton(
                        onClick = {
                            onDelete(initialTransaction)
                            onDismiss()
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}