-keep class dev.ad.soap.** { *; }
-dontobfuscate
-dontwarn androidx.annotation.**
# Only media3's still-image decoding uses it, and Soap TV plays video only.
-dontwarn androidx.exifinterface.**
