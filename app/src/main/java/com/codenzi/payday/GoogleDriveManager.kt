// Konum: app/src/main/java/com/codenzi/payday/GoogleDriveManager.kt

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
import java.io.InputStreamReader

class GoogleDriveManager(private val context: Context) {

    private val backupFileName = "payday_backup.json"
    private var cachedFileId: String? = null // Dosya ID'sini önbelleğe alarak tekrar tekrar aramayı önler

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
        // Önbellekte varsa, doğrudan onu kullan
        if (cachedFileId != null) {
            Log.d("GoogleDriveManager", "Önbellekten dosya ID'si kullanıldı: $cachedFileId")
            return cachedFileId
        }

        // Önbellekte yoksa, Google Drive'da ara
        Log.d("GoogleDriveManager", "Dosya ID'si için Google Drive'da arama yapılıyor...")
        val fileList = drive.files().list()
            .setSpaces("appDataFolder")
            .setFields("files(id)")
            .setQ("name='$backupFileName' and trashed=false")
            .execute()

        // Bulunan ilk dosyanın ID'sini al ve önbelleğe kaydet
        return fileList.files.firstOrNull()?.id?.also {
            cachedFileId = it
            Log.d("GoogleDriveManager", "Dosya bulundu ve ID'si önbelleğe alındı: $it")
        }
    }

    suspend fun uploadFileContent(content: String) = withContext(Dispatchers.IO) {
        val drive = getDriveService() ?: throw IllegalStateException("Kullanıcı giriş yapmamış.")
        val fileId = getBackupFileId(drive)
        val mediaContent = ByteArrayContent.fromString("application/json", content)

        if (fileId == null) {
            // Dosya hiç yoksa oluştur
            Log.d("GoogleDriveManager", "Yedek dosyası bulunamadı, yeni bir tane oluşturuluyor.")
            val fileMetadata = File().apply {
                name = backupFileName
                parents = listOf("appDataFolder")
            }
            val createdFile = drive.files().create(fileMetadata, mediaContent).setFields("id").execute()
            cachedFileId = createdFile.id // Yeni ID'yi önbelleğe al
            Log.d("GoogleDriveManager", "Yeni yedek dosyası oluşturuldu. ID: $cachedFileId")
        } else {
            // Dosya varsa güncelle
            Log.d("GoogleDriveManager", "Mevcut yedek dosyası güncelleniyor. ID: $fileId")
            drive.files().update(fileId, null, mediaContent).execute()
        }
    }

    suspend fun downloadFileContent(): String? = withContext(Dispatchers.IO) {
        val drive = getDriveService() ?: throw IllegalStateException("Kullanıcı giriş yapmamış.")
        val fileId = getBackupFileId(drive)
            ?: run {
                Log.w("GoogleDriveManager", "Geri yüklenecek yedek dosyası bulunamadı.")
                return@withContext null // Dosya ID'si bulunamazsa yedek yoktur.
            }

        Log.d("GoogleDriveManager", "Yedek dosyası indiriliyor. ID: $fileId")
        try {
            val inputStream = drive.files().get(fileId).executeMediaAsInputStream()
            return@withContext BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
        } catch (e: Exception) {
            Log.e("GoogleDriveManager", "Dosya indirme hatası", e)
            throw e
        }
    }

    companion object {
        fun getSignInIntent(context: Context): Intent {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
                .build()
            val client = GoogleSignIn.getClient(context, gso)
            client.signOut() // Her seferinde temiz bir başlangıç için oturumu kapat
            return client.signInIntent
        }
    }
}