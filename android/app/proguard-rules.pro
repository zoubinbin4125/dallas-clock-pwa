# Manifest components are kept via aapt rules. Keep the clock model names stable in stack traces.
-keep class com.binbin.dallasclock.ClockCore { *; }
-keep class com.binbin.dallasclock.ClockCore$Snapshot { *; }
-keep class com.binbin.dallasclock.ClockCore$Holiday { *; }
-keep class com.binbin.dallasclock.ClockCore$Hms { *; }
-keep class com.binbin.dallasclock.DallasClockCompactProvider { *; }
-keep class com.binbin.dallasclock.DallasClockWideProvider { *; }
-keep class com.binbin.dallasclock.ClockUpdateReceiver { *; }
