# Manifest components (activity, accessibility service) are kept automatically by AGP.

# Strip verbose/debug logging from release; Log.i transitions stay for `adb logcat -s ReelsDetector`.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
