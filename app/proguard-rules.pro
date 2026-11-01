-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}

# Re-test release builds once these exist: kotlinx.serialization models (R8 full mode),
# Room entities and converters, Hilt-injected classes created reflectively, Navigation type-safe routes.
