# ----------------------------------------------------------------------------
# Watube ProGuard/R8 Rules - Ubdated
# ----------------------------------------------------------------------------

# region Build
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
# endregion

# region Build
# ----------------------------------------------------------------------------
# KOTLIN COROUTINES

# Keep coroutine dispatcher factories (loaded via ServiceLoader)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
# endregion

# region Build
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
# endregion

# region Build
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
# endregion

# region Settings
# ----------------------------------------------------------------------------
# SETTINGS FRAGMENTS (loaded via reflection)

# Keep preference fragments loaded dynamically
-keep class com.watube.yard.ui.preferences.** { *; }
# endregion

# region Mini Player
# ----------------------------------------------------------------------------
# CONSTRAINTLAYOUT MOTIONLAYOUT (Fix for miniplayer issue)

# Only keep MotionLayout classes (specific fix for miniplayer)
-keep class androidx.constraintlayout.motion.widget.MotionLayout { *; }
-keep class androidx.constraintlayout.motion.widget.TransitionAdapter { *; }
# endregion

# region Local Extraction
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
# endregion

# region Build
# ----------------------------------------------------------------------------
# CRYPTOGRAPHY LIBRARIES

# Suppress warnings for optional crypto providers
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# endregion

# region Build
# ----------------------------------------------------------------------------
# PROTOBUF

# Optimise protobuf classes
-shrinkunusedprotofields
# endregion

# region Build
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
# endregion

# region Privacy
# ----------------------------------------------------------------------------
# LOGGING (release builds)

# Nothing is written to logcat in release: logcat is readable by anyone holding the
# device with USB debugging (or from a bug report), and lines in this codebase carry
# video ids, channel ids, request URLs and exception messages. Crash traces are kept for
# the developer in debug builds only (ExceptionHandler -> crash log in the maintenance
# settings), and debug builds are unaffected (they are not minified). R8 only removes calls
# whose return value is unused, so existing call sites stay valid.
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
# endregion

# region Privacy
# ----------------------------------------------------------------------------
# PRIVACY: telemetry removal (audited 2026-10-09: media3 1.11.1). No proprietary Google library
# is shipped at all (build.gradle.kts refuses them); this cuts what lives in the open source ones.

# Media3 platform diagnostics: even with setUsePlatformDiagnostics(false), every player opens
# a MediaMetricsManager playback session whose id tags its codecs and audio track.
-assumenosideeffects class androidx.media3.exoplayer.ExoPlayerImpl$Api31 {
    static void registerMediaMetricsListener(android.content.Context, androidx.media3.exoplayer.ExoPlayerImpl, boolean, androidx.media3.exoplayer.analytics.PlayerId);
}
# endregion
