# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/smithdave/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# This allows proguard to strip isLoggable() blocks containing only <=INFO log
# code from release builds.
-assumenosideeffects class android.util.Log {
  static *** i(...);
  static *** d(...);
  static *** v(...);
  static *** isLoggable(...);
}

-keep class io.netty.** { *; }
-keep class org.jctools.** { *; }
-keep class org.conscrypt.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class org.openjsse.** { *; }
-keep class com.aayushatharva.** { *; }
-keep class com.github.luben.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class com.jcraft.** { *; }
-keep class com.ning.** { *; }
-keep class net.jpountz.** { *; }
-keep class org.apache.log4j.** { *; }
-keep class org.jboss.log4j.** { *; }
-keep class org.slf4j.** { *; }
-keep class sun.security.** { *; }
-keep class com.oracle.svm.** { *; }
-keep class java.lang.management.** { *; }

-dontwarn io.netty.**
-dontwarn com.aayushatharva.**
-dontwarn com.github.luben.**
-dontwarn com.google.protobuf.**
-dontwarn com.jcraft.**
-dontwarn com.ning.**
-dontwarn org.slf4j.**
-dontwarn com.oracle.svm.**
-dontwarn java.lang.management.**
