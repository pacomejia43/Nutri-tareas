# R8/ProGuard is disabled for the release build (see app/build.gradle.kts) so this file is not
# exercised today. Kept so shrinking can be turned on later without starting from a blank file.

# Anthropic Java SDK + Jackson use reflection for JSON (de)serialization.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn com.fasterxml.jackson.**

# PdfBox-Android ships its own font/resource loading via reflection.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.**

# kotlinx.serialization keeps its own consumer rules; this app's @Serializable models.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.nutritareas.app.**$$serializer { *; }
-keepclassmembers class com.nutritareas.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.nutritareas.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
