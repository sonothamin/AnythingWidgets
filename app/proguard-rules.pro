# WorkManager's Room database is created via reflection (WorkDatabase_Impl.<init>).
# R8 strips the constructor otherwise, crashing androidx.startup at launch.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { <init>(); }
