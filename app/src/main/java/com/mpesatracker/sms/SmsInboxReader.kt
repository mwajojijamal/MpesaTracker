package com.mpesatracker.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.mpesatracker.categorisation.CategoryEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class SmsSyncResult(
    val scannedCount: Int,
    val newlyImportedCount: Int
)

/**
 * Scans the device's existing SMS Inbox for historical M-Pesa confirmation messages
 * and imports any transactions not yet stored in the local Room database.
 */
@Singleton
class SmsInboxReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val categoryEngine: CategoryEngine
) {

    fun hasSmsPermissions(): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val receiveGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

        return readGranted && receiveGranted
    }

    suspend fun syncMpesaInbox(): SmsSyncResult = withContext(Dispatchers.IO) {
        if (!hasSmsPermissions()) {
            return@withContext SmsSyncResult(scannedCount = 0, newlyImportedCount = 0)
        }

        var scanned = 0
        var imported = 0

        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        // Filter at the SQLite ContentProvider level for messages from MPESA/M-PESA
        val selection = "${Telephony.Sms.ADDRESS} LIKE ? OR ${Telephony.Sms.ADDRESS} LIKE ?"
        val selectionArgs = arrayOf("%MPESA%", "%M-PESA%")
        val sortOrder = "${Telephony.Sms.DATE} ASC" // Oldest first so reversals find original codes

        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (cursor.moveToNext()) {
                val sender = cursor.getString(addressIdx)
                val body = cursor.getString(bodyIdx)
                val dateMillis = cursor.getLong(dateIdx)

                if (MessageValidator.isValidMpesaSender(sender) &&
                    MessageValidator.isValidMpesaMessageBody(body)
                ) {
                    scanned++
                    val parsed = MpesaParser.parse(body, fallbackTimestamp = dateMillis)
                    if (parsed != null) {
                        val wasInserted = categoryEngine.ingestParsedTransaction(parsed)
                        if (wasInserted) {
                            imported++
                        }
                    }
                }
            }
        }

        SmsSyncResult(scannedCount = scanned, newlyImportedCount = imported)
    }

    /**
     * Allows manual pasting of an M-Pesa SMS message (useful for emulators or Play Store builds).
     */
    suspend fun ingestRawSmsText(rawSms: String): Boolean = withContext(Dispatchers.IO) {
        val parsed = MpesaParser.parse(rawSms) ?: return@withContext false
        categoryEngine.ingestParsedTransaction(parsed)
    }
}