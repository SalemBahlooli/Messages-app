package com.claude.messages.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    entities = [
        NotificationRule::class,
        ThreadMeta::class,
        ScheduledMessage::class,
        StarredMessage::class,
        BlockedNumber::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ruleDao(): NotificationRuleDao
    abstract fun threadMetaDao(): ThreadMetaDao
    abstract fun scheduledDao(): ScheduledMessageDao
    abstract fun starredDao(): StarredMessageDao
    abstract fun blockedDao(): BlockedNumberDao

    companion object {
        /**
         * Version 2 adds scheduled, starred and blocked tables. A real migration
         * rather than the destructive fallback: wiping the database would throw
         * away every rule, pin and draft the user has made.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scheduled_messages` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`threadId` INTEGER NOT NULL, `recipients` TEXT NOT NULL, " +
                        "`body` TEXT NOT NULL, `sendAt` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `starred_messages` (" +
                        "`messageId` INTEGER NOT NULL, `threadId` INTEGER NOT NULL, " +
                        "`address` TEXT NOT NULL, `body` TEXT NOT NULL, " +
                        "`date` INTEGER NOT NULL, `outgoing` INTEGER NOT NULL, " +
                        "`starredAt` INTEGER NOT NULL, PRIMARY KEY(`messageId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `blocked_numbers` (" +
                        "`key` TEXT NOT NULL, `display` TEXT NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))"
                )
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "messages.db",
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
