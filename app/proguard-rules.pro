# ProGuard rules for TrueEnquiryDialer
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepclassmembers enum * { *; }

# Serialization models
-keepclassmembers class com.dialer.truedialer.data.model.** { *; }
-keep class com.dialer.truedialer.data.model.** { *; }

# MongoDB Atlas Sync
-keep class com.mongodb.** { *; }
-dontwarn com.mongodb.**
-keep class org.bson.** { *; }
-dontwarn org.bson.**

# BCrypt
-keep class org.mindrot.jbcrypt.** { *; }
-dontwarn org.mindrot.jbcrypt.**
