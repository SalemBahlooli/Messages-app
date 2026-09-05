package com.claude.messages.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.claude.messages.di.ServiceLocator
import com.claude.messages.ui.conversations.ConversationsScreen
import com.claude.messages.ui.conversations.ConversationsViewModel
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

class MainActivity : ComponentActivity() {

    /**
     * Deep links arriving after the activity is created. The activity is
     * singleTask, so a notification tap while the app is already open reaches
     * onNewIntent rather than re-running setContent — without this the tap
     * would do nothing.
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

                NavHost(navController = navController, startDestination = "conversations") {

                    composable("conversations") {
                        val vm: ConversationsViewModel = viewModel()
                        // Pick up a role or permission granted while we were away.
                        LaunchedEffect(Unit) { vm.refresh() }
                        ConversationsScreen(
                            viewModel = vm,
                            onOpenThread = { navController.navigate("thread/$it") },
                            onNewMessage = { navController.navigate("new") },
                            onOpenRules = { navController.navigate("rules") },
                            onOpenSettings = { navController.navigate("settings") },
                            onRequestDefaultSms = ::requestDefaultSms,
                            onGrantPermissions = ::requestMissingPermissions,
                            onOpenAppSettings = ::openAppSettings,
                            onOpenDefaultAppsSettings = ::openDefaultAppsSettings,
                        )
                    }

                    composable(
                        route = "thread/{threadId}",
                        arguments = listOf(navArgument("threadId") { type = NavType.LongType }),
                    ) { entry ->
                        val threadId = entry.arguments?.getLong("threadId") ?: -1L
                        val vm: ThreadViewModel = viewModel()
                        LaunchedEffect(threadId) { vm.open(threadId) }
                        ThreadScreen(viewModel = vm, onBack = { navController.popBackStack() })
                    }

                    composable(
                        route = "compose?address={address}&body={body}",
                        arguments = listOf(
                            navArgument("address") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("body") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                        ),
                    ) { entry ->
                        val address = entry.arguments?.getString("address").orEmpty()
                        val body = entry.arguments?.getString("body").orEmpty()
                        val vm: ThreadViewModel = viewModel()
                        // Passing the address is what lets a brand-new conversation send.
                        LaunchedEffect(address) { vm.open(-1L, address, body) }
                        ThreadScreen(viewModel = vm, onBack = { navController.popBackStack() })
                    }

                    composable("new") {
                        val vm: NewMessageViewModel = viewModel()
                        NewMessageScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                            onCompose = { address ->
                                navController.popBackStack()
                                navController.navigate("compose?address=${Uri.encode(address)}&body=")
                            },
                        )
                    }

                    composable("rules") {
                        val vm: RulesViewModel = viewModel()
                        RulesListScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
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

                    composable("ruleEditor") { entry ->
                        // Share the RulesViewModel with the list so the editor state
                        // set by startNew()/startEdit() survives navigation.
                        val parent = remember(entry) { navController.getBackStackEntry("rules") }
                        val vm: RulesViewModel = viewModel(parent)
                        RuleEditorScreen(
                            viewModel = vm,
                            onDone = { navController.popBackStack() },
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenRules = { navController.navigate("rules") },
                            onRequestDefaultSms = ::requestDefaultSms,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        destinationOf(intent)?.let(deepLinks::tryEmit)
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
