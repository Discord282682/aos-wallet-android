# Keep BouncyCastle Ed25519 classes (reflection-free, but keep names for crash readability).
-keep class org.bouncycastle.crypto.** { *; }
-dontwarn org.bouncycastle.**
