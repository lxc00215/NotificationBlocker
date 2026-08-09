# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep the accessibility service
-keep class com.example.notificationblocker.NotificationBlockerService { *; }
-keep class com.example.notificationblocker.NotificationBlockerForegroundService { *; }
