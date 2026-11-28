package com.enziidict.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enziidict.app.core.database.DictionaryPackUpgrader
import com.enziidict.app.core.database.FocusLockRepository
import com.enziidict.app.core.database.FocusLockStatus
import com.enziidict.app.core.model.SpeakingTopic
import com.enziidict.app.core.network.AppThemeMode
import com.enziidict.app.core.network.AppUpdateManager
import com.enziidict.app.core.network.SettingsDataStore
import com.enziidict.app.core.network.UpdateCheckOutcome
import com.enziidict.app.core.network.UserSettings
import com.enziidict.app.core.notification.EnziiNotificationHelper
import com.enziidict.app.feature.dictionary.DictionaryScreen
import com.enziidict.app.feature.focuslock.AppBlockerService
import com.enziidict.app.feature.focuslock.FocusLockScreen
import com.enziidict.app.feature.home.HomeScreen
import com.enziidict.app.feature.progress.LearningSummaryScreen
import com.enziidict.app.feature.reading.ReadingScreen
import com.enziidict.app.feature.settings.SettingsScreen
import com.enziidict.app.feature.shadowing.ShadowingItem
import com.enziidict.app.feature.shadowing.ShadowingPresets
import com.enziidict.app.feature.shadowing.ShadowingScreen
import com.enziidict.app.feature.speaking.SpeakingScreen
import com.enziidict.app.feature.srs.FlashcardScreen
import com.enziidict.app.feature.translate.TranslateScreen
import com.enziidict.app.ui.components.EnziiSplashOverlay
import com.enziidict.app.ui.theme.EnziiDictTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("Trang chủ", Icons.Default.Home),
    DICTIONARY("Từ điển", Icons.Default.Search),
    SRS("Sổ tay", Icons.Default.Book),
    TRANSLATE("Dịch", Icons.Default.Translate),
    SPEAKING("Nói AI", Icons.Default.Mic)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var appUpdateManager: AppUpdateManager

    @Inject
    lateinit var dictionaryPackUpgrader: DictionaryPackUpgrader

    @Inject
    lateinit var focusLockRepository: FocusLockRepository

    private val pendingNotificationIntent = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingNotificationIntent.value = intent

        setContent {
            val settings by settingsDataStore.settingsFlow.collectAsStateWithLifecycle(
                initialValue = UserSettings()
            )
            val systemDark = isSystemInDarkTheme()
            val isDark = when (settings.themeMode) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            val scope = rememberCoroutineScope()
            var hasAppUpdate by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                launch {
                    dictionaryPackUpgrader.upgradeIfNeeded()
                }
                launch {
                    focusLockRepository.resetEmergencyIfNewDay()
                    val eval = focusLockRepository.getCurrentEvaluation()
                    AppBlockerService.syncServiceState(
                        context = this@MainActivity,
                        shouldRun = eval.status != FocusLockStatus.DISABLED && eval.lockedPackages.isNotEmpty()
                    )
                }
                hasAppUpdate = runCatching {
                    appUpdateManager.checkLatestRelease() is UpdateCheckOutcome.UpdateAvailable
                }.getOrDefault(false)
            }

            EnziiDictTheme(darkTheme = isDark) {
                var showSplash by remember { mutableStateOf(true) }
                var currentTab by remember { mutableStateOf(MainTab.HOME) }
                var showSettingsScreen by remember { mutableStateOf(false) }
                var showFocusLockScreen by remember { mutableStateOf(false) }
                var showReadingScreen by remember { mutableStateOf(false) }
                var showShadowingScreen by remember { mutableStateOf(false) }
                var showLearningSummaryScreen by remember { mutableStateOf(false) }
                var pendingShadowingItem by remember { mutableStateOf<ShadowingItem?>(null) }
                var pendingSpeakingTopic by remember { mutableStateOf<SpeakingTopic?>(null) }
                var pendingDictionaryQuery by remember { mutableStateOf<String?>(null) }

                val incomingIntent by pendingNotificationIntent.collectAsStateWithLifecycle()
                LaunchedEffect(incomingIntent) {
                    val dest = incomingIntent?.getStringExtra(EnziiNotificationHelper.EXTRA_DEST)
                    if (!dest.isNullOrBlank()) {
                        showSplash = false
                        showSettingsScreen = false
                        showFocusLockScreen = false
                        showReadingScreen = false
                        showLearningSummaryScreen = false
                        when (dest) {
                            EnziiNotificationHelper.DEST_SRS -> {
                                showShadowingScreen = false
                                currentTab = MainTab.SRS
                            }
                            EnziiNotificationHelper.DEST_DICTIONARY_WORD -> {
                                showShadowingScreen = false
                                pendingDictionaryQuery = incomingIntent
                                    ?.getStringExtra(EnziiNotificationHelper.EXTRA_WORD)
                                    ?.takeIf { it.isNotBlank() }
                                currentTab = MainTab.DICTIONARY
                            }
                            EnziiNotificationHelper.DEST_SHADOWING -> {
                                val sid = incomingIntent?.getStringExtra(EnziiNotificationHelper.EXTRA_SHADOWING_ID)
                                pendingShadowingItem = ShadowingPresets.ALL.firstOrNull { it.id == sid }
                                showShadowingScreen = true
                            }
                        }
                        pendingNotificationIntent.value = null
                    }
                }

                when {
                    showSplash -> {
                        EnziiSplashOverlay(onFinished = { showSplash = false })
                    }
                    showFocusLockScreen -> {
                        FocusLockScreen(onBack = { showFocusLockScreen = false })
                    }
                    showSettingsScreen -> {
                        SettingsScreen(
                            onBack = { showSettingsScreen = false },
                            onOpenFocusLock = {
                                showSettingsScreen = false
                                showFocusLockScreen = true
                            }
                        )
                    }
                    showLearningSummaryScreen -> {
                        LearningSummaryScreen(onBack = { showLearningSummaryScreen = false })
                    }
                    showShadowingScreen -> {
                        ShadowingScreen(
                            initialItem = pendingShadowingItem,
                            onInitialItemConsumed = { pendingShadowingItem = null },
                            onBack = { showShadowingScreen = false }
                        )
                    }
                    showReadingScreen -> {
                        Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(pad)
                            ) {
                                ReadingScreen(
                                    onBackToHome = { showReadingScreen = false },
                                    onOpenShadowing = { item ->
                                        pendingShadowingItem = item
                                        showShadowingScreen = true
                                    }
                                )
                            }
                        }
                    }
                    else -> {
                        EnziiMainScaffold(
                            currentTab = currentTab,
                            isDark = isDark,
                            hasAppUpdate = hasAppUpdate,
                            onSelectTab = { currentTab = it },
                            onOpenSummary = { showLearningSummaryScreen = true },
                            onOpenReading = { showReadingScreen = true },
                            onToggleTheme = {
                                scope.launch {
                                    val next = if (isDark) AppThemeMode.LIGHT else AppThemeMode.DARK
                                    settingsDataStore.saveThemeMode(next)
                                }
                            },
                            onOpenSettings = { showSettingsScreen = true }
                        ) { paddingModifier ->
                            AnimatedContent(
                                targetState = currentTab,
                                modifier = paddingModifier,
                                transitionSpec = {
                                    (fadeIn(tween(220)) + scaleIn(initialScale = 0.985f, animationSpec = tween(220)))
                                        .togetherWith(fadeOut(tween(150)))
                                },
                                label = "main_tab_transition"
                            ) { tab ->
                                when (tab) {
                                    MainTab.HOME -> HomeScreen(
                                        onOpenDictionary = { currentTab = MainTab.DICTIONARY },
                                        onOpenSrs = { currentTab = MainTab.SRS },
                                        onOpenSpeakingWithTopic = { topic ->
                                            pendingSpeakingTopic = topic
                                            currentTab = MainTab.SPEAKING
                                        },
                                        onOpenTranslate = { currentTab = MainTab.TRANSLATE },
                                        onOpenReading = { showReadingScreen = true },
                                        onOpenShadowing = {
                                            pendingShadowingItem = null
                                            showShadowingScreen = true
                                        },
                                        onOpenLearningSummary = { showLearningSummaryScreen = true },
                                        onOpenFocusLock = { showFocusLockScreen = true }
                                    )
                                    MainTab.DICTIONARY -> DictionaryScreen(
                                        initialQuery = pendingDictionaryQuery,
                                        onInitialQueryConsumed = { pendingDictionaryQuery = null },
                                        onOpenShadowing = { item ->
                                            pendingShadowingItem = item
                                            showShadowingScreen = true
                                        }
                                    )
                                    MainTab.SRS -> FlashcardScreen(
                                        onOpenLearningSummary = { showLearningSummaryScreen = true }
                                    )
                                    MainTab.TRANSLATE -> TranslateScreen(
                                        onOpenSettings = { showSettingsScreen = true }
                                    )
                                    MainTab.SPEAKING -> SpeakingScreen(
                                        initialTopic = pendingSpeakingTopic,
                                        onInitialTopicConsumed = { pendingSpeakingTopic = null },
                                        onOpenSettings = { showSettingsScreen = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationIntent.value = intent
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnziiMainScaffold(
    currentTab: MainTab,
    isDark: Boolean,
    hasAppUpdate: Boolean,
    onSelectTab: (MainTab) -> Unit,
    onOpenSummary: () -> Unit,
    onOpenReading: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Enzii Dict",
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(onClick = onOpenSummary) {
                        Icon(Icons.Default.Insights, contentDescription = "Tổng kết học tập")
                    }
                    IconButton(onClick = onOpenReading) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Đọc song ngữ")
                    }
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Đổi giao diện Sáng/Tối"
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        BadgedBox(
                            badge = {
                                if (hasAppUpdate) {
                                    Badge()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Cài đặt")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { onSelectTab(tab) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, maxLines = 1) }
                    )
                }
            }
        }
    ) { innerPadding ->
        content(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
