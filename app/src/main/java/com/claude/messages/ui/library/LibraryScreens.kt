package com.claude.messages.ui.library

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.claude.messages.R
import com.claude.messages.data.db.BlockedNumber
import com.claude.messages.data.db.StarredMessage
import com.claude.messages.data.repo.ContactsRepository
import com.claude.messages.di.ServiceLocator
import com.claude.messages.ui.compose.AppHeader
import com.claude.messages.ui.compose.Avatar
import com.claude.messages.ui.compose.EmptyState
import com.claude.messages.util.Formatting
import com.claude.messages.util.PhoneNumbers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ starred

data class StarredItem(val message: StarredMessage, val name: String)

class StarredViewModel(app: Application) : AndroidViewModel(app) {
    private val db = ServiceLocator.database(app)
    private val contacts = ServiceLocator.contactsRepository(app)

    val items: StateFlow<List<StarredItem>?> = db.starredDao().observeAll()
        .map { list ->
            list.map { m ->
                val name = if (m.outgoing) {
                    getApplication<Application>().getString(R.string.me)
                } else {
                    contacts.lookup(m.address)?.name?.takeIf { it.isNotBlank() }
                        ?: ContactsRepository.formatNumber(m.address)
                }
                StarredItem(m, name)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun unstar(id: Long) {
        viewModelScope.launch { db.starredDao().delete(id) }
    }
}

@Composable
fun StarredScreen(
    viewModel: StarredViewModel,
    onBack: () -> Unit,
    onOpenThread: (Long) -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AppHeader(title = stringResource(R.string.starred_messages), onBack = onBack) },
    ) { padding ->
        val list = items
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(
                icon = Icons.Outlined.StarOutline,
                title = stringResource(R.string.no_starred),
                subtitle = stringResource(R.string.no_starred_hint),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                items(list, key = { it.message.messageId }) { item ->
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onOpenThread(item.message.threadId) },
                    ) {
                        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(name = item.name, photoUri = null, size = 26.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    Formatting.listTimestamp(item.message.date),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                IconButton(
                                    onClick = { viewModel.unstar(item.message.messageId) },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        stringResource(R.string.unstar),
                                        Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Text(
                                item.message.body,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp, end = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ blocked

class BlockedViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = ServiceLocator.database(app).blockedDao()

    val numbers: StateFlow<List<BlockedNumber>?> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun block(raw: String) {
        val clean = raw.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            dao.upsert(BlockedNumber(key = PhoneNumbers.blockKey(clean), display = clean))
            ServiceLocator.notifyDataChanged()
        }
    }

    fun unblock(key: String) {
        viewModelScope.launch {
            dao.delete(key)
            ServiceLocator.notifyDataChanged()
        }
    }
}

@Composable
fun BlockedScreen(viewModel: BlockedViewModel, onBack: () -> Unit) {
    val numbers by viewModel.numbers.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    Scaffold(
        topBar = { AppHeader(title = stringResource(R.string.blocked_numbers), onBack = onBack) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                stringResource(R.string.blocked_numbers_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(stringResource(R.string.block_add_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = input.isNotBlank(),
                    onClick = {
                        viewModel.block(input)
                        input = ""
                    },
                ) { Text(stringResource(R.string.block_number)) }
            }

            val list = numbers
            if (list != null && list.isEmpty()) {
                EmptyState(icon = Icons.Default.Block, title = stringResource(R.string.no_blocked))
            } else if (list != null) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.key }) { number ->
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(name = number.display, photoUri = null, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    ContactsRepository.formatNumber(number.display),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    Formatting.fullTimestamp(number.addedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { viewModel.unblock(number.key) }) {
                                Text(stringResource(R.string.unblock_number))
                            }
                        }
                    }
                }
            }
        }
    }
}
