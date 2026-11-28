package com.enziidict.app.core.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.enziidict.app.MainActivity
import com.enziidict.app.R
import com.enziidict.app.core.model.DictionaryEntry
import com.enziidict.app.feature.shadowing.ShadowingItem

data class ReminderNotificationContent(
    val title: String,
    val shortText: String,
    val bigText: String
)

data class WordNotificationContent(
    val title: String,
    val shortText: String,
    val bigText: String,
    val destination: String,
    val wordQuery: String? = null,
    val shadowingId: String? = null,
    val primaryActionLabel: String
)

object EnziiNotificationHelper {

    const val CHANNEL_DAILY_REMINDER = "enzii_daily_reminder"
    const val CHANNEL_DAILY_WORD = "enzii_daily_word"

    const val NOTIF_ID_REMINDER = 1001
    const val NOTIF_ID_WORD = 1002

    const val EXTRA_DEST = "enzii_notif_dest"
    const val EXTRA_WORD = "enzii_notif_word"
    const val EXTRA_SHADOWING_ID = "enzii_notif_shadowing_id"

    const val DEST_SRS = "srs"
    const val DEST_DICTIONARY_WORD = "dictionary_word"
    const val DEST_SHADOWING = "shadowing"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val reminderChannel = NotificationChannel(
            CHANNEL_DAILY_REMINDER,
            "Nhắc học hằng ngày",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Nhắc ôn tập SRS nhẹ nhàng và bảo vệ chuỗi Streak mỗi ngày."
        }

        val wordChannel = NotificationChannel(
            CHANNEL_DAILY_WORD,
            "Từ vựng & Mẫu câu mỗi ngày",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Gợi ý 1 từ vựng (kèm IPA & nghĩa) hoặc mẫu câu giao tiếp hằng ngày."
        }

        manager.createNotificationChannel(reminderChannel)
        manager.createNotificationChannel(wordChannel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun buildStudyReminderContent(
        studiedToday: Boolean,
        streakDays: Int,
        dueCount: Int,
        forcePreview: Boolean = false
    ): ReminderNotificationContent? {
        if (studiedToday && dueCount <= 0 && !forcePreview) {
            return null
        }

        val title = when {
            !studiedToday && streakDays > 0 -> "Giữ vững chuỗi $streakDays ngày học hôm nay!"
            dueCount > 0 -> "Bạn có $dueCount thẻ SRS đang chờ ôn tập"
            else -> "Dành 5 phút luyện tiếng Anh cùng Enzii Dict nhé!"
        }

        val shortText = when {
            dueCount > 0 -> "$dueCount từ vựng đến hạn ôn hôm nay — chạm để lật Flashcard ngay."
            else -> "Ôn nhanh vài từ mới hoặc đọc một đoạn song ngữ để duy trì phản xạ."
        }

        val bigText = buildString {
            if (!studiedToday && streakDays > 0) {
                append("Bạn đang có chuỗi $streakDays ngày học liên tiếp. Chỉ cần 1 lượt ôn tập ngắn hôm nay để không bị ngắt Streak!")
            } else if (!studiedToday) {
                append("Hôm nay bạn chưa mở phiên học nào trên Enzii Dict.")
            } else {
                append("Hôm nay bạn đã học rất tốt!")
            }
            if (dueCount > 0) {
                append("\n• Hiện có $dueCount thẻ Flashcard SM-2 đang đến hạn ôn.")
            } else {
                append("\n• Mở Sổ tay hoặc Từ điển để ôn lại những từ bạn quan tâm.")
            }
        }

        return ReminderNotificationContent(
            title = title,
            shortText = shortText,
            bigText = bigText
        )
    }

    fun buildWordNotificationContent(entry: DictionaryEntry): WordNotificationContent {
        val ipa = entry.ipaUs.ifBlank { entry.ipaUk }
        val ipaDisplay = if (ipa.isNotBlank()) " $ipa" else ""
        val title = "Từ hôm nay: ${entry.word}$ipaDisplay"
        val shortText = "(${entry.pos}) ${entry.meaningVi}"
        val exampleBlock = entry.examples.firstOrNull()?.let { ex ->
            val viLine = if (ex.vi.isNotBlank()) "\n→ ${ex.vi}" else ""
            "\n\nVí dụ: \"${ex.en}\"$viLine"
        }.orEmpty()

        val bigText = "${entry.word}$ipaDisplay (${entry.pos})\nNghĩa: ${entry.meaningVi}$exampleBlock"
        return WordNotificationContent(
            title = title,
            shortText = shortText,
            bigText = bigText,
            destination = DEST_DICTIONARY_WORD,
            wordQuery = entry.word,
            primaryActionLabel = "Xem chi tiết từ"
        )
    }

    fun buildSentenceNotificationContent(item: ShadowingItem): WordNotificationContent {
        val title = "Mẫu câu giao tiếp (${item.category.labelVi})"
        val shortText = item.sentenceEn
        val ipaLine = if (item.ipa.isNotBlank()) "\n${item.ipa}" else ""
        val bigText = "\"${item.sentenceEn}\"$ipaLine\n\nNghĩa: ${item.meaningVi}"
        return WordNotificationContent(
            title = title,
            shortText = shortText,
            bigText = bigText,
            destination = DEST_SHADOWING,
            shadowingId = item.id,
            primaryActionLabel = "Luyện Shadowing"
        )
    }

    @SuppressLint("MissingPermission")
    fun postStudyReminder(context: Context, content: ReminderNotificationContent): Boolean {
        if (!hasNotificationPermission(context)) return false
        ensureChannels(context)

        val openSrsPendingIntent = createMainActivityPendingIntent(
            context = context,
            requestCode = 2001,
            destination = DEST_SRS
        )
        val openDictPendingIntent = createMainActivityPendingIntent(
            context = context,
            requestCode = 2002,
            destination = DEST_DICTIONARY_WORD
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_REMINDER)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(content.title)
            .setContentText(content.shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openSrsPendingIntent)
            .addAction(0, "Ôn Flashcard ngay", openSrsPendingIntent)
            .addAction(0, "Mở Từ điển", openDictPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_REMINDER, notification)
        return true
    }

    @SuppressLint("MissingPermission")
    fun postDailyWordOrSentence(context: Context, content: WordNotificationContent): Boolean {
        if (!hasNotificationPermission(context)) return false
        ensureChannels(context)

        val primaryPendingIntent = createMainActivityPendingIntent(
            context = context,
            requestCode = 2101,
            destination = content.destination,
            wordQuery = content.wordQuery,
            shadowingId = content.shadowingId
        )
        val reviewSrsPendingIntent = createMainActivityPendingIntent(
            context = context,
            requestCode = 2102,
            destination = DEST_SRS
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_WORD)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(content.title)
            .setContentText(content.shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(primaryPendingIntent)
            .addAction(0, content.primaryActionLabel, primaryPendingIntent)
            .addAction(0, "Ôn SRS ngay", reviewSrsPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_WORD, notification)
        return true
    }

    private fun createMainActivityPendingIntent(
        context: Context,
        requestCode: Int,
        destination: String,
        wordQuery: String? = null,
        shadowingId: String? = null
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DEST, destination)
            if (!wordQuery.isNullOrBlank()) {
                putExtra(EXTRA_WORD, wordQuery)
            }
            if (!shadowingId.isNullOrBlank()) {
                putExtra(EXTRA_SHADOWING_ID, shadowingId)
            }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
