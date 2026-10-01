# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.kvtral.ejercicios.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
