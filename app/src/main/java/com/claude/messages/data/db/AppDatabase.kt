package com.claude.messages.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun senderMatchToString(v: SenderMatch): String = v.name
    @TypeConverter fun stringToSenderMatch(v: String): SenderMatch =
        runCatching { SenderMatch.valueOf(v) }.getOrDefault(SenderMatch.ANY)

    @TypeConverter fun contentMatchToString(v: ContentMatch): String = v.name
    @TypeConverter fun stringToContentMatch(v: String): ContentMatch =
        runCatching { ContentMatch.valueOf(v) }.getOrDefault(ContentMatch.ANY)

    @TypeConverter fun vibrationToString(v: VibrationPattern): String = v.name
    @TypeConverter fun stringToVibration(v: String): VibrationPattern = VibrationPattern.from(v)

    @TypeConverter fun importanceToString(v: RuleImportance): String = v.name
    @TypeConverter fun stringToImportance(v: String): RuleImportance =
        runCatching { RuleImportance.valueOf(v) }.getOrDefault(RuleImportance.NORMAL)
}

@Database(
    entities = [NotificationRule::class, ThreadMeta::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ruleDao(): NotificationRuleDao
    abstract fun threadMetaDao(): ThreadMetaDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "messages.db",
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
