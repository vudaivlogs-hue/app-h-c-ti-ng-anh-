package com.enziidict.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object EnziiDatabaseMigrations {

    /**
     * Schema v1 -> v2:
     * - Introduces `word_folders` table with preset folders ("Mặc định", "Oxford 3000", "Từ lưu từ hội thoại")
     *   plus any custom `folder_name` already used by existing rows in `saved_words`.
     * - Rebuilds `saved_words` to add `folder_id` with FK -> `word_folders(id)` while preserving
     *   all existing SM-2 progress, timestamps, and folder assignments.
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `word_folders` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `icon_name` TEXT NOT NULL,
                    `color_hex` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS `idx_word_folders_name_unique`
                ON `word_folders` (`name`)
                """.trimIndent()
            )

            // Seed preset folders
            db.execSQL(
                "INSERT OR IGNORE INTO `word_folders` (`name`, `icon_name`, `color_hex`, `created_at`) VALUES ('Mặc định', 'folder', '#1D4ED8', ?)",
                arrayOf(now)
            )
            db.execSQL(
                "INSERT OR IGNORE INTO `word_folders` (`name`, `icon_name`, `color_hex`, `created_at`) VALUES ('Oxford 3000', 'school', '#0F766E', ?)",
                arrayOf(now + 1)
            )
            db.execSQL(
                "INSERT OR IGNORE INTO `word_folders` (`name`, `icon_name`, `color_hex`, `created_at`) VALUES ('Từ lưu từ hội thoại', 'chat', '#7C3AED', ?)",
                arrayOf(now + 2)
            )

            // Preserve any custom folders already referenced by v1 saved_words
            db.execSQL(
                """
                INSERT OR IGNORE INTO `word_folders` (`name`, `icon_name`, `color_hex`, `created_at`)
                SELECT DISTINCT TRIM(`folder_name`), 'label', '#EA580C', ?
                FROM `saved_words`
                WHERE `folder_name` IS NOT NULL AND TRIM(`folder_name`) != ''
                """.trimIndent(),
                arrayOf(now + 3)
            )

            // Rebuild saved_words with folder_id FK -> word_folders(id)
            db.execSQL(
                """
                CREATE TABLE `saved_words_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `entry_id` INTEGER NOT NULL,
                    `folder_id` INTEGER NOT NULL,
                    `folder_name` TEXT NOT NULL,
                    `ease_factor` REAL NOT NULL,
                    `interval_days` INTEGER NOT NULL,
                    `repetitions` INTEGER NOT NULL,
                    `lapses` INTEGER NOT NULL,
                    `state` TEXT NOT NULL,
                    `next_review_at` INTEGER NOT NULL,
                    `last_reviewed_at` INTEGER,
                    `created_at` INTEGER NOT NULL,
                    FOREIGN KEY(`entry_id`) REFERENCES `dictionary_entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`folder_id`) REFERENCES `word_folders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT OR IGNORE INTO `saved_words_new` (
                    `id`, `entry_id`, `folder_id`, `folder_name`,
                    `ease_factor`, `interval_days`, `repetitions`, `lapses`,
                    `state`, `next_review_at`, `last_reviewed_at`, `created_at`
                )
                SELECT
                    sw.`id`,
                    sw.`entry_id`,
                    COALESCE(
                        (SELECT wf.`id` FROM `word_folders` wf WHERE wf.`name` = TRIM(sw.`folder_name`) LIMIT 1),
                        (SELECT wf.`id` FROM `word_folders` wf WHERE wf.`name` = 'Mặc định' LIMIT 1)
                    ),
                    COALESCE(
                        (SELECT wf.`name` FROM `word_folders` wf WHERE wf.`name` = TRIM(sw.`folder_name`) LIMIT 1),
                        'Mặc định'
                    ),
                    sw.`ease_factor`,
                    sw.`interval_days`,
                    sw.`repetitions`,
                    sw.`lapses`,
                    sw.`state`,
                    sw.`next_review_at`,
                    sw.`last_reviewed_at`,
                    sw.`created_at`
                FROM `saved_words` sw
                """.trimIndent()
            )

            db.execSQL("DROP TABLE `saved_words`")
            db.execSQL("ALTER TABLE `saved_words_new` RENAME TO `saved_words`")

            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS `idx_saved_words_unique_entry_folder_id`
                ON `saved_words` (`folder_id`, `entry_id`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `idx_saved_words_due_queue`
                ON `saved_words` (`folder_id`, `next_review_at`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `idx_saved_words_entry_id`
                ON `saved_words` (`entry_id`)
                """.trimIndent()
            )
        }
    }
}
