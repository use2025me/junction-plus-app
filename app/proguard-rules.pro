# Keep the methods the Junction Plus website calls (video mode, pull-to-refresh).
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Shrink unused code but keep names readable (scrambled names can look suspicious to antivirus apps).
-dontobfuscate
