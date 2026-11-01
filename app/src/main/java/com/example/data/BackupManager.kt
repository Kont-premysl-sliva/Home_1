package com.example.data

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupManager(private val context: Context) {

    private val backupDir: File = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ContainerBackups").apply {
        if (!exists()) {
            mkdirs()
        }
    }

    fun getBackupDir(): File {
        if (!backupDir.exists()) backupDir.mkdirs()
        return backupDir
    }

    fun createDataBackup(onProgress: ((Int) -> Unit)? = null): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val backupFile = File(getBackupDir(), "DataBackup_$timestamp.zip")

        try {
            val dbFile = context.getDatabasePath("container-db")
            val dbShmFile = context.getDatabasePath("container-db-shm")
            val dbWalFile = context.getDatabasePath("container-db-wal")

            val databaseFiles = listOfNotNull(
                if (dbFile.exists()) dbFile else null,
                if (dbShmFile.exists()) dbShmFile else null,
                if (dbWalFile.exists()) dbWalFile else null
            )

            val filesDir = context.filesDir
            val imageFiles = filesDir.listFiles { _, name -> name.endsWith(".jpg") || name.endsWith(".jpeg") }?.toList() ?: emptyList()

            val totalFiles = databaseFiles.size + imageFiles.size
            var currentFile = 0

            ZipOutputStream(FileOutputStream(backupFile)).use { zos ->
                for (file in databaseFiles) {
                    addFileToZip(file, "database/${file.name}", zos)
                    currentFile++
                    onProgress?.invoke((currentFile * 100) / totalFiles.coerceAtLeast(1))
                }

                for (file in imageFiles) {
                    addFileToZip(file, "images/${file.name}", zos)
                    currentFile++
                    onProgress?.invoke((currentFile * 100) / totalFiles.coerceAtLeast(1))
                }
            }
            return backupFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            if (backupFile.exists()) backupFile.delete()
            throw e
        }
    }

    fun restoreDataBackup(backupFile: File, onProgress: ((Int) -> Unit)? = null) {
        try {
            var totalEntries = 0
            try {
                java.util.zip.ZipFile(backupFile).use { zip ->
                    totalEntries = zip.size()
                }
            } catch (e: Exception) {
                // Ignore if we can't count
            }
            
            var currentEntry = 0
            ZipInputStream(FileInputStream(backupFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (entry.name.startsWith("database/")) {
                        // Obnovení souborů databáze
                        val fileName = File(entry.name).name
                        val targetDbFile = context.getDatabasePath(fileName)
                        targetDbFile.parentFile?.mkdirs()
                        FileOutputStream(targetDbFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    } else if (entry.name.startsWith("images/")) {
                        // Obnovení obrázků do filesDir
                        val fileName = File(entry.name).name
                        val targetImageFile = File(context.filesDir, fileName)
                        FileOutputStream(targetImageFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    currentEntry++
                    if (totalEntries > 0) {
                        onProgress?.invoke((currentEntry * 100) / totalEntries)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    fun getAppApkBackup(): File {
        val apkFile = File(context.applicationInfo.sourceDir)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val backupApkFile = File(getBackupDir(), "AppBackup_$timestamp.apk")
        
        apkFile.copyTo(backupApkFile, overwrite = true)
        return backupApkFile
    }

    private fun addFileToZip(file: File, entryName: String, zos: ZipOutputStream) {
        val buffer = ByteArray(1024)
        FileInputStream(file).use { fis ->
            zos.putNextEntry(ZipEntry(entryName))
            var length: Int
            while (fis.read(buffer).also { length = it } > 0) {
                zos.write(buffer, 0, length)
            }
            zos.closeEntry()
        }
    }
}
