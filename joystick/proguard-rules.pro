# WebRTC usa JNI: las clases Java se buscan por nombre desde código nativo.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
