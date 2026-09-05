package com.claude.messages.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { ServiceLocator.notifyDataChanged() }

    private val defaultSmsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { ServiceLocator.notifyDataChanged() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestRuntimePermissions()

        val initialThreadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        val sharedText = readSharedText(intent)
        val sharedAddress = readAddress(intent)

        setContent {
            MessagesTheme {
                val navController = rememberNavController()

                LaunchedEffect(Unit) {
                    when {
                        initialThreadId > 0 -> navController.navigate("thread/$initialThreadId")
                        sharedAddress != null -> {
                            val encoded = Uri.encode(sharedAddress)
                            val body = Uri.encode(sharedText.orEmpty())
                            navController.navigate("compose/$encoded?body=$body")
                        }
                    }
                }

                NavHost(navController = navController, startDestination = "conversations") {

                    composable("conversations") {
                        val vm: ConversationsViewModel = viewModel()
                        ConversationsScreen(
                            viewModel = vm,
                            onOpenThread = { navController.navigate("thread/$it") },
                            onNewMessage = { navController.navigate("new") },
                            onOpenRules = { navController.navigate("rules") },
                            onOpenSettings = { navController.navigate("settings") },
                            onRequestDefaultSms = ::requestDefaultSms,
                        )
                    }

                    composable(
                        route = "thread/{threadId}",
                        arguments = listOf(navArgument("threadId") { type = NavType.LongType }),
                    ) { entry ->
                        val threadId = entry.arguments?.getLong("threadId") ?: -1L
                        val vm: ThreadViewModel = viewModel()
                        LaunchedEffect(threadId) { vm.load(threadId) }
                        ThreadScreen(viewModel = vm, onBack = { navController.popBackStack() })
                    }

                    composable(
                        route = "compose/{address}?body={body}",
                        arguments = listOf(
                            navArgument("address") { type = NavType.StringType },
                            navArgument("body") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                        ),
                    ) { entry ->
                        val address = entry.arguments?.getString("address").orEmpty()
                        val body = entry.arguments?.getString("body").orEmpty()
                        val vm: ThreadViewModel = viewModel()
                        LaunchedEffect(address) { vm.loadForAddress(address, body) }
                        ThreadScreen(viewModel = vm, onBack = { navController.popBackStack() })
                    }

                    composable("new") {
                        val vm: NewMessageViewModel = viewModel()
                        NewMessageScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                            onOpenThread = { threadId ->
                                navController.popBackStack()
                                navController.navigate("thread/$threadId")
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
                        // Share the RulesViewModel with the list so the editor
                        // state set by startNew()/startEdit() survives navigation.
                        val parent = remember(entry) {
                            navController.getBackStackEntry("rules")
                        }
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
    }

    private fun requestDefaultSms() {
        DefaultSmsHelper.requestIntent(this)?.let(defaultSmsLauncher::launch)
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    /** Text carried by an ACTION_SEND / sms: intent from another app. */
    private fun readSharedText(intent: Intent): String? = when (intent.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
        Intent.ACTION_SENDTO, Intent.ACTION_VIEW ->
            intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT)

        else -> null
    }

    private fun readAddress(intent: Intent): String? {
        val data = intent.data ?: return null
        return when (data.scheme) {
            "sms", "smsto", "mms", "mmsto" ->
                data.schemeSpecificPart?.substringBefore('?')?.trim()?.takeIf { it.isNotEmpty() }

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
