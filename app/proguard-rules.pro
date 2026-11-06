# Keep the methods the Junction Plus website calls (video mode, pull-to-refresh).
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
