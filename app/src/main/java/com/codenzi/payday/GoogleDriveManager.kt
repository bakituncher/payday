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
import java.util.Collections

class GoogleDriveManager(private val context: Context) {

    private val driveService: Drive
    private val backupFileName = "payday_backup.json"

    init {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(DriveScopes.DRIVE_APPDATA)
        )
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null) {
            credential.selectedAccount = account.account
        }
        driveService = Drive.Builder(
            NetHttpTransport(), // HATA DÜZELTİLDİ: AndroidHttp yerine NetHttpTransport kullanıldı.
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName(context.getString(R.string.app_name)).build()
    }

    private suspend fun searchFile(fileName: String): File? = withContext(Dispatchers.IO) {
        try {
            // Sorgu güncellendi ve daha spesifik hale getirildi.
            val fileList = driveService.files().list()
                .setSpaces("appDataFolder")
                .setFields("files(id, name)")
                .setQ("name='$fileName' and trashed=false")
                .execute()
            return@withContext fileList.files.firstOrNull()
        } catch (e: Exception) {
            Log.e("GoogleDriveManager", "Dosya arama hatası", e)
            null
        }
    }

    // EKSİK FONKSİYON EKLENDİ
    suspend fun uploadFileContent(content: String) = withContext(Dispatchers.IO) {
        val fileMetadata = File().apply {
            name = backupFileName
            parents = Collections.singletonList("appDataFolder")
        }

        val mediaContent = ByteArrayContent.fromString("application/json", content)

        val existingFile = searchFile(backupFileName)

        if (existingFile == null) {
            // Dosya yok, yeni oluştur
            driveService.files().create(fileMetadata, mediaContent).execute()
        } else {
            // Dosya var, güncelle
            driveService.files().update(existingFile.id, fileMetadata, mediaContent).execute()
        }
    }

    // EKSİK FONKSİYON EKLENDİ
    suspend fun downloadFileContent(): String? = withContext(Dispatchers.IO) {
        val file = searchFile(backupFileName)
        if (file?.id != null) {
            try {
                val inputStream = driveService.files().get(file.id).executeMediaAsInputStream()
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    return@withContext reader.readText()
                }
            } catch (e: Exception) {
                Log.e("GoogleDriveManager", "Dosya indirme hatası", e)
                return@withContext null
            }
        } else {
            return@withContext null
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