# ----------------------------------------------------------------------------
# Watube ProGuard/R8 Rules
# ----------------------------------------------------------------------------

# region Build
# R8 (full mode, see gradle.properties) ignores the ProGuard-only -optimizationpasses and
# -optimizations: they were removed, R8 runs its own passes.

# Keep line numbers: a stack trace from a user is retraced with the mapping of the release
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

# Kotlin coroutines and kotlinx.serialization ship their own R8 rules (META-INF/com.android.tools/r8),
# the copies that were here duplicated them.

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

# The timeago patterns (one class per language) are reached through PatternMap, not by
# reflection: R8 shrinks and renames them like any other class.

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

# and for Kotlin's println (System.out in logcat), from the app or a library
-assumenosideeffects class kotlin.io.ConsoleKt {
    public static void println(...);
    public static void print(...);
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
