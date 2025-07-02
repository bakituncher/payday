package com.codenzi.payday

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

@Suppress("DEPRECATION")
class GoogleDriveManager(private val context: Context) {

    private val backupFileName = "payday_backup.json"
    private var cachedFileId: String? = null
    private val TAG = "GoogleDriveManager"

    private fun getDriveService(): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_APPDATA)
        ).apply {
            selectedAccount = account.account
        }
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName(context.getString(R.string.app_name)).build()
    }

    private suspend fun getBackupFileId(drive: Drive): String? {
        if (cachedFileId != null) return cachedFileId
        return try {
            drive.files().list()
                .setSpaces("appDataFolder")
                .setFields("files(id)")
                .setQ("name='$backupFileName' and trashed=false")
                .execute()
                .files.firstOrNull()?.id?.also { cachedFileId = it }
        } catch (e: IOException) {
            Log.e(TAG, "Dosya ID'si alınırken ağ hatası oluştu.", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Dosya ID'si alınırken genel bir hata oluştu.", e)
            null
        }
    }

    suspend fun isBackupAvailable(): Boolean = withContext(Dispatchers.IO) {
        val drive = getDriveService() ?: return@withContext false
        try {
            getBackupFileId(drive) != null
        } catch (e: Exception) {
            Log.e(TAG, "Yedek kontrolü sırasında hata", e)
            false
        }
    }

    suspend fun uploadFileContent(content: String) = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService() ?: throw IllegalStateException("Kullanıcı giriş yapmamış.")
            val fileId = getBackupFileId(drive)
            val mediaContent = ByteArrayContent.fromString("application/json", content)
            if (fileId == null) {
                val fileMetadata = File().apply {
                    name = backupFileName
                    parents = listOf("appDataFolder")
                }
                val createdFile = drive.files().create(fileMetadata, mediaContent).setFields("id").execute()
                cachedFileId = createdFile.id
            } else {
                drive.files().update(fileId, null, mediaContent).execute()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Dosya yüklenirken ağ hatası oluştu.", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Dosya yüklenirken genel bir hata oluştu.", e)
            throw e
        }
    }

    suspend fun downloadFileContent(): String? = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService() ?: throw IllegalStateException("Kullanıcı giriş yapmamış.")
            val fileId = getBackupFileId(drive) ?: return@withContext null
            val inputStream = drive.files().get(fileId).executeMediaAsInputStream()
            BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
        } catch (e: IOException) {
            Log.e(TAG, "Dosya indirilirken ağ hatası.", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Dosya indirme sırasında genel bir hata.", e)
            null
        }
    }

    suspend fun deleteBackupFile(): Boolean = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService() ?: return@withContext false
            val fileId = getBackupFileId(drive)
            if (fileId != null) {
                drive.files().delete(fileId).execute()
                cachedFileId = null
                Log.d(TAG, "Google Drive'daki yedek dosyası başarıyla silindi.")
            }
            true // Return true even if file didn't exist, the state is still "no backup file"
        } catch (e: IOException) {
            Log.e(TAG, "Google Drive yedeği silinirken ağ hatası.", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Google Drive yedek dosyası silinirken genel bir hata oluştu.", e)
            false
        }
    }


    companion object {
        fun getSignInIntent(context: Context): Intent {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
                .build()
            val client = GoogleSignIn.getClient(context, gso)
            return client.signInIntent
        }
    }
}