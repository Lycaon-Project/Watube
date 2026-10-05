# ----------------------------------------------------------------------------
# Watube ProGuard/R8 Rules - Ubdated
# ----------------------------------------------------------------------------

# Enable aggressive optimizations for smaller APK size
-optimizationpasses 5
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# Keep line numbers for crash reporting and debugging
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Rename source file attribute to hide original names
-renamesourcefileattribute SourceFile

# Preserve Kotlin metadata for reflection
-keepattributes Metadata

# Don't warn about missing dependencies (common in Android libraries)
-dontwarn java.beans.**
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# ----------------------------------------------------------------------------
# KOTLIN COROUTINES

# Keep coroutine dispatcher factories (loaded via ServiceLoader)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ----------------------------------------------------------------------------
# KOTLINX SERIALIZATION

# Keep serializable classes (required for JSON parsing)
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# ----------------------------------------------------------------------------
# APP-SPECIFIC DATA CLASSES

# Keep data classes used for JSON serialization and IPC
-keep class com.watube.yard.obj.** { *; }
-keep class com.watube.yard.api.obj.** { *; }
-keep class com.watube.yard.obj.update.** { *; }
-keep class com.watube.yard.parcelable.** { *; }

# Keep Parcelable CREATOR fields
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ----------------------------------------------------------------------------
# SETTINGS FRAGMENTS (loaded via reflection)

# Keep preference fragments loaded dynamically
-keep class com.watube.yard.ui.preferences.** { *; }

# ----------------------------------------------------------------------------
# CONSTRAINTLAYOUT MOTIONLAYOUT (Fix for miniplayer issue)

# Only keep MotionLayout classes (specific fix for miniplayer)
-keep class androidx.constraintlayout.motion.widget.MotionLayout { *; }
-keep class androidx.constraintlayout.motion.widget.TransitionAdapter { *; }

# ----------------------------------------------------------------------------
# NEWPIPE EXTRACTOR (No built-in ProGuard rules)

# Keep only the classes used via reflection
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }

# ✅ CORRECTION : Remplacement des règles Rhino par dontwarn
# Rhino JavaScript engine classes are handled by NewPipe Extractor internally
-dontwarn org.mozilla.javascript.**

# Suppress warnings for optional dependencies
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn com.google.re2j.**

# ----------------------------------------------------------------------------
# CRYPTOGRAPHY LIBRARIES

# Suppress warnings for optional crypto providers
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ----------------------------------------------------------------------------
# PROTOBUF

# Optimise protobuf classes
-shrinkunusedprotofields

# ----------------------------------------------------------------------------
# GOOGLE PLAY SERVICES

-dontwarn com.google.android.gms.**

# ----------------------------------------------------------------------------
# R8 OPTIMIZATION HINTS

# Allow more aggressive optimizations
-allowaccessmodification

# Merge classes when possible to reduce APK size
-repackageclasses ''

# Remove unused code aggressively
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ----------------------------------------------------------------------------
# LOGGING (release builds)

# Nothing is written to logcat in release: logcat is readable by anyone holding the
# device with USB debugging (or from a bug report), and lines in this codebase carry
# video ids, channel ids, request URLs and exception messages. User visible errors go
# through the in-app crash log (ExceptionHandler -> ErrorDialog) instead, and debug
# builds are unaffected (they are not minified). R8 only removes calls whose return
# value is unused, so existing call sites stay valid.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# same for the plain Java stack traces (they end up in logcat under "System.err")
-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
    public void printStackTrace(java.io.PrintStream);
    public void printStackTrace(java.io.PrintWriter);
}

# ----------------------------------------------------------------------------
# PRIVACY: Google telemetry removal (audited 2026-10-05: play-services-cast-framework 22.3.1,
# play-services-base 18.7.2, media3 1.9.2).
#
# Google's datatransport libraries (Firelog uploads) are not shipped at all, see the Cast
# dependency in build.gradle.kts. The rules below cut the entry points of the telemetry that
# lives inside the libraries we do ship, so R8 deletes all the code only they reach. Names made
# of "za"/"zz" are obfuscated and only valid for the versions above: after an update the release
# build fails (see the guard in build.gradle.kts) until they are verified again.

# 1. Cast analytics: Play Services answers CastContext with a flags bundle that starts the whole
#    analytics stack (session, application and feature usage analytics, usage reporting consent).
-assumenosideeffects class com.google.android.gms.cast.framework.CastContext {
    void zzf(android.os.Bundle);
}
#    feature usage counters, called by the Cast UI widgets
-assumenosideeffects class com.google.android.gms.internal.cast.zzr {
    static void zzb(com.google.android.gms.internal.cast.zzpm);
}

# 2. Play Services client telemetry: a collector attached to every Google API call (counts,
#    timings, results) and the client that reports the batches to Play Services.
-assumenosideeffects class com.google.android.gms.common.api.internal.GoogleApiManager {
    private void zaI(com.google.android.gms.tasks.TaskCompletionSource, int, com.google.android.gms.common.api.GoogleApi);
    private com.google.android.gms.common.internal.TelemetryLoggingClient zaL() return _NONNULL_;
}
-assumenosideeffects interface com.google.android.gms.common.internal.TelemetryLoggingClient {
    com.google.android.gms.tasks.Task log(com.google.android.gms.common.internal.TelemetryData);
}

# 3. Media3 platform diagnostics: even with setUsePlatformDiagnostics(false), every player opens
#    a MediaMetricsManager playback session whose id tags its codecs and audio track.
-assumenosideeffects class androidx.media3.exoplayer.ExoPlayerImpl$Api31 {
    static void registerMediaMetricsListener(android.content.Context, androidx.media3.exoplayer.ExoPlayerImpl, boolean, androidx.media3.exoplayer.analytics.PlayerId);
}
