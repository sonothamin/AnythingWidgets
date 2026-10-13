# WorkManager's Room database is created via reflection (WorkDatabase_Impl.<init>).
# R8 strips the constructor otherwise, crashing androidx.startup at launch.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { <init>(); }

# Glance creates widget tap handlers by class name (ActionCallback.<init>() via reflection).
# R8 strips the constructors otherwise, and every tap on a widget silently does nothing.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
