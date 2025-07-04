# Gson (JSON kütüphanesi) için gerekli kurallar.
# Veri modellerinizin (örn: BackupData) adlarının değiştirilmesini önler.
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keepattributes Signature

# Kendi veri sınıflarınızın (Data-Model) korunması için.
# Bu kural, 'com.codenzi.payday' paketi altındaki tüm sınıfları ve üyelerini korur.
-keep class com.codenzi.payday.** { *; }

# Google Drive API ve Google Sign-In için gerekli kurallar.
# Bu API'lerin kullandığı sınıfların ve alanların korunmasını sağlar.
-keep class com.google.api.services.drive.** { *; }
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}

# Firebase Crashlytics için gerekli kurallar.
# Hata raporlarının doğru şekilde oluşturulabilmesi için önemlidir.
-keep class com.google.firebase.crashlytics.** { *; }
-keep public class * extends com.google.firebase.crashlytics.CustomKeysAndValues
-keep class * implements com.google.firebase.crashlytics.internal.persistence.CrashlyticsReportPersistence

# Room (Veritabanı) Entity sınıfları
# Veritabanı tablolarınızla eşleşen sınıfların adlarının değiştirilmesini engeller.
-keep class * implements androidx.room.Entity

# MPAndroidChart (Grafik kütüphanesi) için kurallar
-keep class com.github.mikephil.charting.** { *; }