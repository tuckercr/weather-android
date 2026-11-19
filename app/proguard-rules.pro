# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Crashlytics: keep file names and line numbers so deobfuscated release stack traces point at the
# right line, and keep custom exception class names readable in crash reports.
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
# Room (used internally by WorkManager) creates its generated *_Impl database classes by
# reflection through the no-arg constructor. Without this rule R8 strips that constructor and the
# app crashes at startup with NoSuchMethodException: WorkDatabase_Impl.<init>.
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
# Play review-ktx 2.0.2 marks a lambda with an annotation from a newer play-services-basement than
# the one Firebase and location bring in. It is only an annotation, so nothing is missing at run time.
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite
