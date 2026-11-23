# ----------------------------------------------------------------------------
# Watube ProGuard/R8 Rules
# ----------------------------------------------------------------------------
# R8 runs in full mode (see gradle.properties). Kotlin coroutines and kotlinx.serialization
# ship their own R8 rules (META-INF/com.android.tools/r8): they are not repeated here.

# region Attributes
# Keep line numbers: a stack trace from a user is retraced with the mapping of the release
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
# Kotlin metadata, read by reflection
-keepattributes Metadata

# the original source file names are not shipped
-renamesourcefileattribute SourceFile
# endregion

# region Missing optional dependencies
-dontwarn java.beans.**
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Rhino (JavaScript engine of the extractor) and its optional tools
-dontwarn org.mozilla.javascript.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn com.google.re2j.**

# optional TLS / crypto providers
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# endregion

# region App data classes (JSON serialization and IPC)
-keep class com.watube.yard.obj.** { *; }
-keep class com.watube.yard.api.obj.** { *; }
-keep class com.watube.yard.parcelable.** { *; }

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
# endregion

# region Settings (preference fragments loaded by reflection)
-keep class com.watube.yard.ui.preferences.** { *; }
# endregion

# region Mini player (MotionLayout)
-keep class androidx.constraintlayout.motion.widget.MotionLayout { *; }
-keep class androidx.constraintlayout.motion.widget.TransitionAdapter { *; }
# endregion

# region Extractor
# The timeago patterns (one class per language) are reached through PatternMap, not by
# reflection: R8 shrinks and renames them like any other class.
# endregion

# region Protobuf
-shrinkunusedprotofields
# endregion

# region R8 optimization
-allowaccessmodification
-repackageclasses ''

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
# endregion

# region Privacy: no logging in release builds
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

# Kotlin's print/println are @InlineOnly: compiled into each caller as System.out calls, never
# called on kotlin.io.ConsoleKt (whose copies are private), so no rule can match them. The app
# itself has no print, println or System.out call.
# endregion

# region Privacy: telemetry removal (audited 2026-10-09, still matching media3 1.11.1 on 2026-11-23)
# No proprietary Google library is shipped at all (build.gradle.kts refuses them); this cuts what
# lives in the open source ones.

# Media3 platform diagnostics: even with setUsePlatformDiagnostics(false), every player opens
# a MediaMetricsManager playback session whose id tags its codecs and audio track.
-assumenosideeffects class androidx.media3.exoplayer.ExoPlayerImpl$Api31 {
    public static void registerMediaMetricsListener(android.content.Context, androidx.media3.exoplayer.ExoPlayerImpl, boolean, androidx.media3.exoplayer.analytics.PlayerId);
}
# endregion
