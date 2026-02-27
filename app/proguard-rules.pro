# Keep sing-box library (gomobile bindings)
-keep class io.nekohasekai.** { *; }
-keep class libbox.** { *; }

# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class org.rigbyfoundation.nuggetvpn.**$$serializer { *; }
-keepclassmembers class org.rigbyfoundation.nuggetvpn.** { *** Companion; }
-keepclasseswithmembers class org.rigbyfoundation.nuggetvpn.** { kotlinx.serialization.KSerializer serializer(...); }
