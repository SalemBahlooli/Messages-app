package com.claude.messages.ui.newmessage

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.claude.messages.data.model.Contact
import com.claude.messages.data.repo.ContactsRepository
import com.claude.messages.di.ServiceLocator
import com.claude.messages.ui.compose.Avatar
import com.claude.messages.ui.compose.EmptyState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewMessageUiState(
    val query: String = "",
    val contacts: List<Contact> = emptyList(),
    val filtered: List<Contact> = emptyList(),
)

class NewMessageViewModel(app: Application) : AndroidViewModel(app) {

    private val contactsRepo = ServiceLocator.contactsRepository(app)
    private val smsRepo = ServiceLocator.smsRepository(app)

    private val _state = MutableStateFlow(NewMessageUiState())
    val state: StateFlow<NewMessageUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val all = contactsRepo.allContacts()
            _state.update { it.copy(contacts = all, filtered = all) }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { current ->
            val needle = query.trim().lowercase()
            current.copy(
                query = query,
                filtered = if (needle.isEmpty()) current.contacts else current.contacts.filter {
                    it.name.lowercase().contains(needle) ||
                        it.number.filter(Char::isDigit).contains(needle.filter(Char::isDigit))
                },
            )
        }
    }

    /** Resolves the thread for the typed number or picked contact. */
    fun openThreadFor(number: String, onReady: (Long) -> Unit) {
        viewModelScope.launch {
            val threadId = smsRepo.threadIdFor(setOf(number))
            onReady(threadId)
        }
    }

    /** True when the query looks like a phone number the user can text directly. */
    fun queryIsDialable(): Boolean {
        val digits = _state.value.query.filter { it.isDigit() }
        return digits.length >= 3
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMessageScreen(
    viewModel: NewMessageViewModel,
    onBack: () -> Unit,
    onOpenThread: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New message") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("To") },
                placeholder = { Text("Name or phone number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )

            if (viewModel.queryIsDialable() &&
                state.filtered.none { it.number == state.query }
            ) {
                TextButton(
                    onClick = { viewModel.openThreadFor(state.query.trim(), onOpenThread) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text("Send to ${state.query.trim()}")
                }
            }

            if (state.filtered.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.PersonSearch,
                    title = "No contacts found",
                    subtitle = "Type a phone number to start a new conversation.",
                )
                return@Column
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(state.filtered, key = { it.id.toString() + it.number }) { contact ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.openThreadFor(contact.number, onOpenThread)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(name = contact.name, photoUri = contact.photoUri, size = 44.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(contact.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                ContactsRepository.formatNumber(contact.number),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
