package com.mpesatracker.export

import android.content.Context
import com.mpesatracker.data.database.MpesaDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun createLocalBackup(): File = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(MpesaDatabase.DATABASE_NAME)
        val backupDir = File(context.getExternalFilesDir(null), "MpesaBackups").apply { mkdirs() }
        val backupFile = File(backupDir, "MpesaBackup_${System.currentTimeMillis()}.db")

        dbFile.inputStream().use { input ->
            backupFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        backupFile
    }

    suspend fun restoreLocalBackup(backupFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val dbFile = context.getDatabasePath(MpesaDatabase.DATABASE_NAME)
            backupFile.inputStream().use { input ->
                dbFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}