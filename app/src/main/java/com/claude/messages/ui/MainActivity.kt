package com.claude.messages.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import androidx.compose.runtime.collectAsState
import com.claude.messages.data.prefs.FeaturePrefs
import com.claude.messages.ui.library.BlockedScreen
import com.claude.messages.ui.library.BlockedViewModel
import com.claude.messages.ui.library.InsightsScreen
import com.claude.messages.ui.library.InsightsViewModel
import com.claude.messages.ui.library.StarredScreen
import com.claude.messages.ui.library.StarredViewModel
import com.claude.messages.ui.lock.AppLock
import com.claude.messages.ui.lock.LockScreen
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.claude.messages.di.ServiceLocator
import com.claude.messages.ui.conversations.ConversationsScreen
import com.claude.messages.ui.conversations.ConversationsViewModel
import com.claude.messages.ui.home.HomeNavigationBar
import com.claude.messages.ui.home.HomeTab
import com.claude.messages.ui.money.MoneyScreen
import com.claude.messages.ui.money.MoneyViewModel
import com.claude.messages.ui.newmessage.NewMessageScreen
import com.claude.messages.ui.newmessage.NewMessageViewModel
import com.claude.messages.ui.rules.RuleEditorScreen
import com.claude.messages.ui.rules.RulesListScreen
import com.claude.messages.ui.rules.RulesViewModel
import com.claude.messages.ui.settings.SettingsScreen
import com.claude.messages.ui.theme.MessagesTheme
import com.claude.messages.ui.thread.ThreadScreen
import com.claude.messages.ui.thread.ThreadViewModel
import com.claude.messages.util.DefaultSmsHelper
import kotlinx.coroutines.flow.MutableSharedFlow

/** Where an incoming intent wants the app to open. */
private sealed interface Destination {
    data class Thread(val threadId: Long) : Destination
    data class Compose(val address: String, val body: String) : Destination
}

class MainActivity : FragmentActivity() {

    /**
     * Deep links arriving after the activity is created. The activity is
     * singleTask, so a notification tap while the app is already open reaches
     * onNewIntent rather than re-running setContent.
     */
    private val deepLinks = MutableSharedFlow<Destination>(extraBufferCapacity = 4)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { ServiceLocator.notifyDataChanged() }

    private val defaultSmsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { ServiceLocator.notifyDataChanged() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestMissingPermissions()

        val initial = destinationOf(intent)

        setContent {
            MessagesTheme {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentTab = HomeTab.ofRoute(backStackEntry?.destination?.route)

                fun go(destination: Destination) = when (destination) {
                    is Destination.Thread ->
                        navController.navigate("thread/${destination.threadId}")

                    is Destination.Compose -> navController.navigate(
                        "compose?address=${Uri.encode(destination.address)}" +
                            "&body=${Uri.encode(destination.body)}"
                    )
                }

                LaunchedEffect(Unit) {
                    initial?.let(::go)
                    deepLinks.collect(::go)
                }

                Scaffold(
                    bottomBar = {
                        // Slides away on full-screen destinations so a chat or
                        // the rule editor gets the whole display.
                        AnimatedVisibility(
                            visible = currentTab != null && !AppLock.locked.collectAsState().value,
                            enter = slideInVertically { it },
                            exit = slideOutVertically { it },
                        ) {
                            HomeNavigationBar(
                                current = currentTab,
                                onSelect = { tab -> navController.switchTab(tab) },
                            )
                        }
                    },
                ) { scaffoldPadding ->
                    val locked by AppLock.locked.collectAsState()
                    // Ask straight away whenever the lock engages.
                    LaunchedEffect(locked) { if (locked) AppLock.prompt(this@MainActivity) }
                    // The lock is drawn over the app rather than in place of it, so
                    // navigation (and a notification's deep link) keeps working
                    // underneath while the user authenticates.
                    Box(Modifier.fillMaxSize()) {
                        AppNavHost(
                            navController = navController,
                            // Only the bottom inset is consumed here; each screen
                            // still draws its own top bar under the status bar.
                            contentPadding = scaffoldPadding,
                            onRequestDefaultSms = ::requestDefaultSms,
                            onGrantPermissions = ::requestMissingPermissions,
                            onOpenAppSettings = ::openAppSettings,
                            onOpenDefaultAppsSettings = ::openDefaultAppsSettings,
                            onOpenSystemNotifications = ::openSystemNotificationSettings,
                        )
                        if (locked) LockScreen(onUnlock = { AppLock.prompt(this@MainActivity) })
                    }
                }
            }
        }
    }

    @Composable
    private fun AppNavHost(
        navController: NavHostController,
        contentPadding: PaddingValues,
        onRequestDefaultSms: () -> Unit,
        onGrantPermissions: () -> Unit,
        onOpenAppSettings: () -> Unit,
        onOpenDefaultAppsSettings: () -> Unit,
        onOpenSystemNotifications: () -> Unit,
    ) {
        val layoutDirection = LocalLayoutDirection.current
        val tabPadding = remember(contentPadding, layoutDirection) {
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding(),
            )
        }

        Box(Modifier.fillMaxSize()) {
            NavHost(navController = navController, startDestination = HomeTab.CHATS.route) {

                composable(HomeTab.CHATS.route) {
                    val vm: ConversationsViewModel = viewModel()
                    ConversationsScreen(
                        viewModel = vm,
                        contentPadding = tabPadding,
                        onOpenThread = { navController.navigate("thread/$it") },
                        onNewMessage = { navController.navigate("new") },
                        onRequestDefaultSms = onRequestDefaultSms,
                        onGrantPermissions = onGrantPermissions,
                        onOpenAppSettings = onOpenAppSettings,
                        onOpenDefaultAppsSettings = onOpenDefaultAppsSettings,
                        onOpenStarred = { navController.navigate("starred") },
                        onOpenInsights = { navController.navigate("insights") },
                        onOpenBlocked = { navController.navigate("blocked") },
                    )
                }

                composable(HomeTab.MONEY.route) {
                    val vm: MoneyViewModel = viewModel()
                    MoneyScreen(
                        viewModel = vm,
                        contentPadding = tabPadding,
                        onOpenThread = { navController.navigate("thread/$it") },
                        onOpenRules = { navController.switchTab(HomeTab.RULES) },
                    )
                }

                composable(HomeTab.RULES.route) {
                    val vm: RulesViewModel = viewModel()
                    RulesListScreen(
                        viewModel = vm,
                        contentPadding = tabPadding,
                        onAddRule = {
                            vm.startNew()
                            navController.navigate("ruleEditor")
                        },
                        onEditRule = { id ->
                            vm.startEdit(id)
                            navController.navigate("ruleEditor")
                        },
                    )
                }

                composable(HomeTab.SETTINGS.route) {
                    SettingsScreen(
                        contentPadding = tabPadding,
                        onOpenRules = { navController.switchTab(HomeTab.RULES) },
                        onRequestDefaultSms = onRequestDefaultSms,
                        onOpenSystemNotifications = onOpenSystemNotifications,
                        onOpenStarred = { navController.navigate("starred") },
                        onOpenInsights = { navController.navigate("insights") },
                        onOpenBlocked = { navController.navigate("blocked") },
                    )
                }

                composable("starred") {
                    val vm: StarredViewModel = viewModel()
                    StarredScreen(
                        viewModel = vm,
                        onBack = { navController.popBackStack() },
                        onOpenThread = { navController.navigate("thread/$it") },
                    )
                }

                composable("insights") {
                    val vm: InsightsViewModel = viewModel()
                    InsightsScreen(
                        viewModel = vm,
                        onBack = { navController.popBackStack() },
                        onOpenThread = { navController.navigate("thread/$it") },
                    )
                }

                composable("blocked") {
                    val vm: BlockedViewModel = viewModel()
                    BlockedScreen(viewModel = vm, onBack = { navController.popBackStack() })
                }

                composable(
                    route = "thread/{threadId}",
                    arguments = listOf(navArgument("threadId") { type = NavType.LongType }),
                ) { entry ->
                    val threadId = entry.arguments?.getLong("threadId") ?: -1L
                    val vm: ThreadViewModel = viewModel()
                    LaunchedEffect(threadId) { vm.open(threadId) }
                    ThreadScreen(
                        viewModel = vm,
                        onBack = { navController.popBackStack() },
                        onForward = { body -> navController.navigate("new?body=${Uri.encode(body)}") },
                    )
                }

                composable(
                    route = "compose?address={address}&body={body}",
                    arguments = listOf(
                        navArgument("address") { type = NavType.StringType; defaultValue = "" },
                        navArgument("body") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    val address = entry.arguments?.getString("address").orEmpty()
                    val body = entry.arguments?.getString("body").orEmpty()
                    val vm: ThreadViewModel = viewModel()
                    // Passing the address is what lets a brand-new conversation send.
                    LaunchedEffect(address) { vm.open(-1L, address, body) }
                    ThreadScreen(
                        viewModel = vm,
                        onBack = { navController.popBackStack() },
                        onForward = { body -> navController.navigate("new?body=${Uri.encode(body)}") },
                    )
                }

                composable(
                    route = "new?body={body}",
                    arguments = listOf(
                        navArgument("body") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    // Forwarding carries the text along to whoever is picked.
                    val body = entry.arguments?.getString("body").orEmpty()
                    val vm: NewMessageViewModel = viewModel()
                    NewMessageScreen(
                        viewModel = vm,
                        onBack = { navController.popBackStack() },
                        onCompose = { address ->
                            navController.popBackStack()
                            navController.navigate(
                                "compose?address=${Uri.encode(address)}&body=${Uri.encode(body)}"
                            )
                        },
                    )
                }

                composable("ruleEditor") { entry ->
                    // Shares the RulesViewModel with the list so the editor state
                    // set by startNew()/startEdit() survives navigation.
                    val parent = remember(entry) {
                        navController.getBackStackEntry(HomeTab.RULES.route)
                    }
                    val vm: RulesViewModel = viewModel(parent)
                    RuleEditorScreen(viewModel = vm, onDone = { navController.popBackStack() })
                }
            }
        }
    }

    /** Switches tabs without stacking duplicates of the top-level screens. */
    private fun NavHostController.switchTab(tab: HomeTab) {
        navigate(tab.route) {
            popUpTo(graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        destinationOf(intent)?.let(deepLinks::tryEmit)
    }

    override fun onStart() {
        super.onStart()
        val lockOn = FeaturePrefs.current(this).appLockEnabled
        AppLock.onStart(this, lockOn)
        // Keep the conversation list out of the recent-apps thumbnail when locked.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!lockOn)
        }
    }

    override fun onStop() {
        super.onStop()
        AppLock.onStop()
    }

    override fun onResume() {
        super.onResume()
        // The default-SMS role may have changed in Settings while we were away.
        ServiceLocator.notifyDataChanged()
    }

    private fun requestDefaultSms() {
        DefaultSmsHelper.requestIntent(this)?.let(defaultSmsLauncher::launch)
    }

    private fun openAppSettings() {
        runCatching { startActivity(DefaultSmsHelper.appSettingsIntent(this)) }
    }

    private fun openDefaultAppsSettings() {
        runCatching { startActivity(DefaultSmsHelper.defaultAppsSettingsIntent()) }
    }

    private fun openSystemNotificationSettings() {
        runCatching { startActivity(DefaultSmsHelper.notificationSettingsIntent(this)) }
    }

    private fun requestMissingPermissions() {
        val missing = DefaultSmsHelper.missingPermissions(this)
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    /** Reads where an intent wants us to go: a notification tap, or a share. */
    private fun destinationOf(intent: Intent): Destination? {
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        if (threadId > 0) return Destination.Thread(threadId)

        val address = intent.data
            ?.takeIf { it.scheme in setOf("sms", "smsto", "mms", "mmsto") }
            ?.schemeSpecificPart
            ?.substringBefore('?')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val body = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_SENDTO, Intent.ACTION_VIEW ->
                intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT)

            else -> null
        }.orEmpty()

        return when {
            address != null -> Destination.Compose(address, body)
            // A share with text but no recipient: let the user pick one.
            body.isNotEmpty() -> Destination.Compose("", body)
            else -> null
        }
    }

    companion object {
        const val EXTRA_THREAD_ID = "extra_thread_id"

        /** Intent that opens the app directly on [threadId] (used by notifications). */
        fun threadIntent(context: Context, threadId: Long): Intent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_THREAD_ID, threadId)
            }
    }
}
