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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontwarn org.xsocket.**
-keep class org.xsocket.** { *; }
-keep class javax.ws.rs.** { *; }
-keep class com.mushanyux.mushanim.MSIM {*;}
-keep class com.mushanyux.mushanim.message.type.MSMsgContentType { *; }
-keep class com.mushanyux.mushanim.message.type.MSSendMsgResult { *; }
-keep class com.mushanyux.mushanim.message.type.MSConnectStatus { *; }
-keep class com.mushanyux.mushanim.message.type.MSConnectReason { *; }

-keep class com.mushanyux.mushanim.entity.* { *; }
-keep class com.mushanyux.mushanim.interfaces.** { *; }
-keep class com.mushanyux.mushanim.msgmodel.** { *; }
-keep class com.mushanyux.mushanim.manager.** { *; }
-keepclassmembers class com.mushanyux.mushanim.db.MSDBHelper$DatabaseHelper {
   public *;
}

#--------- 混淆dh curve25519-------
-keep class org.whispersystems.curve25519.**{*;}
-keep class org.whispersystems.** { *; }
-keep class org.thoughtcrime.securesms.** { *; }

# sqlcipher
-keep,includedescriptorclasses class net.sqlcipher.** { *; }
-keep,includedescriptorclasses interface net.sqlcipher.** { *; }

-flattenpackagehierarchy 'msim'