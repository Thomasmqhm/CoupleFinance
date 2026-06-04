# CoupleFinance ProGuard Rules

# Conserver les numéros de ligne pour le débogage des stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Modèles de données ─────────────────────────────────────────────
# Les modèles sérialisés vers/depuis Firestore doivent garder leurs noms
-keep class com.couplefinance.models.** { *; }
-keepclassmembers class com.couplefinance.models.** { *; }

# ── Firebase ───────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ── MLKit / OCR ────────────────────────────────────────────────────
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ── Tesseract ─────────────────────────────────────────────────────
-keep class cz.adaptech.tesseract4android.** { *; }
-dontwarn cz.adaptech.**

# ── PDFBox ────────────────────────────────────────────────────────
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.apache.**

# ── BouncyCastle ──────────────────────────────────────────────────
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ── Widgets Android ───────────────────────────────────────────────
-keep class com.couplefinance.widget.** { *; }

# ── BroadcastReceivers ───────────────────────────────────────────
-keep class com.couplefinance.data.BankAutoSyncManager$BankSyncReceiver { *; }
-keep class com.couplefinance.utils.NotificationScheduler$ChargeAlarmReceiver { *; }

# ── Sérialisation JSON (org.json) ─────────────────────────────────
-keep class org.json.** { *; }

# ── Androidx ─────────────────────────────────────────────────────
-keep class androidx.** { *; }
-dontwarn androidx.**

# ── Règles générales Android ─────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
