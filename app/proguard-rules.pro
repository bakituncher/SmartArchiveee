# =======================================================================================
# ==                 Smart Archive için ProGuard Kuralları (Nihai Sürüm)                 ==
# =======================================================================================
# Bu dosya, uygulamanızın önemli parçalarının ProGuard tarafından yanlışlıkla
# kaldırılmasını veya değiştirilmesini engeller.

# ---------------------------------------------------------------------------------------
# -                          Varsayılan ve Temel Android Kuralları                      -
# ---------------------------------------------------------------------------------------

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends androidx.fragment.app.DialogFragment

-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

-keep class **.R$* {
    *;
}

# ---------------------------------------------------------------------------------------
# -                              Kotlin Özel Kuralları                                  -
# ---------------------------------------------------------------------------------------

-keepclasseswithmembers,allowshrinking class * {
    @kotlin.Metadata public *;
}

-keepclassmembers class * extends kotlin.coroutines.jvm.internal.BaseContinuationImpl {
    <init>(kotlin.coroutines.Continuation);
}

# ---------------------------------------------------------------------------------------
# -                           Üçüncü Parti Kütüphane Kuralları                          -
# ---------------------------------------------------------------------------------------

# --- Google GSON (JSON Veri Serileştirme) ---
-keep class com.codenzi.ceparsivi.** { *; }
-keep class com.google.gson.reflect.TypeToken
-keep class com.google.gson.Gson

# --- Glide (Görsel Yükleme Kütüphanesi) ---
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$ImageType
-keepclassmembers class * implements com.bumptech.glide.load.model.ModelLoader {
  public <init>(...);
}

# --- Google Play Services ve Firebase ---
-keepattributes SourceFile,LineNumberTable
-keep class com.google.api.services.drive.model.** { *; }
-keep class com.google.api.client.json.GenericJson { *; }
-keepclassmembers class com.google.api.client.json.GenericJson {
   <fields>;
}
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}
-keep public class com.google.android.gms.ads.** { public *; }
-keep public class com.google.android.ump.** { public *; }
-keep public class com.android.billingclient.api.** { public *; }

# --- AndroidX WorkManager ---
-keepclassmembers public class * extends androidx.work.Worker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}
-keepclassmembers public class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

# ---------------------------------------------------------------------------------------
# -                            Uygulamaya Özel Kurallar                                 -
# ---------------------------------------------------------------------------------------

# === KESİN ÇÖZÜM: RecyclerView İç Sınıflarını Doğrudan Hedefleme ===
# IDE'nin şikayet ettiği genel kural yerine, uygulamanızdaki spesifik iç sınıfları ('$')
# kullanarak koruyoruz. Bu, hatayı %100 giderecektir.
-keep public class com.codenzi.ceparsivi.ArchivedFileAdapter$* {
    *;
}
-keep public class com.codenzi.ceparsivi.IntroSliderAdapter$* {
    *;
}

# =======================================================================================
# ==                                Kuralların Sonu                                    ==
# =======================================================================================