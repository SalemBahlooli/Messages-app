package com.claude.messages.ui.library

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.claude.messages.R
import com.claude.messages.data.repo.ContactsRepository
import com.claude.messages.di.ServiceLocator
import com.claude.messages.ui.compose.AppHeader
import com.claude.messages.ui.compose.EmptyState
import com.claude.messages.ui.compose.SectionHeader
import com.claude.messages.util.Formatting
import com.claude.messages.util.OtpDetector
import com.claude.messages.util.PhoneNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class TopContact(val name: String, val threadId: Long, val count: Int)

data class Insights(
    val sent: Int,
    val received: Int,
    val codes: Int,
    val top: List<TopContact>,
    /** Messages per hour of day, 0..23. */
    val byHour: List<Int>,
    /** Messages per day, oldest first, ending today. */
    val byDay: List<Pair<Long, Int>>,
) {
    val total: Int get() = sent + received
}

class InsightsViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<Insights?>(null)
    val state: StateFlow<Insights?> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.value = compute() }
    }

    private suspend fun compute(): Insights = withContext(Dispatchers.Default) {
        val app = getApplication<Application>()
        val since = System.currentTimeMillis() - PERIOD_DAYS * DAY_MS
        val messages = ServiceLocator.smsRepository(app).loadAllSince(since)
        val contacts = ServiceLocator.contactsRepository(app)

        val byHour = IntArray(24)
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val byDay = IntArray(14)
        val cal = Calendar.getInstance()

        messages.forEach { m ->
            cal.timeInMillis = m.date
            byHour[cal.get(Calendar.HOUR_OF_DAY)]++
            val dayIndex = 13 - ((today - startOfDay(m.date)) / DAY_MS).toInt()
            if (dayIndex in 0..13) byDay[dayIndex]++
        }

        // Group by number rather than thread so one person counts once.
        val top = messages
            .filter { it.address.isNotBlank() }
            .groupBy { PhoneNumbers.blockKey(it.address) }
            .map { (_, list) -> list }
            .sortedByDescending { it.size }
            .take(5)
            .map { list ->
                val sample = list.first()
                val name = contacts.lookup(sample.address)?.name?.takeIf { it.isNotBlank() }
                    ?: ContactsRepository.formatNumber(sample.address)
                TopContact(name, sample.threadId, list.size)
            }

        Insights(
            sent = messages.count { it.isOutgoing },
            received = messages.count { it.isIncoming },
            codes = messages.count { it.isIncoming && OtpDetector.find(it.body) != null },
            top = top,
            byHour = byHour.toList(),
            byDay = (0..13).map { i -> (today - (13 - i) * DAY_MS) to byDay[i] },
        )
    }

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        const val PERIOD_DAYS = 90L
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel,
    onBack: () -> Unit,
    onOpenThread: (Long) -> Unit,
) {
    val insights by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppHeader(
                title = stringResource(R.string.insights),
                subtitle = stringResource(R.string.insights_period),
                onBack = onBack,
            )
        },
    ) { padding ->
        val data = insights
        when {
            data == null -> Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            data.total == 0 -> EmptyState(
                icon = Icons.Default.Insights,
                title = stringResource(R.string.insights_empty),
                modifier = Modifier.padding(padding),
            )

            else -> Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                // Headline numbers are tiles, not a chart.
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatTile(stringResource(R.string.insights_received), data.received, Modifier.weight(1f))
                    StatTile(stringResource(R.string.insights_sent), data.sent, Modifier.weight(1f))
                    StatTile(stringResource(R.string.insights_codes), data.codes, Modifier.weight(1f))
                }
                Text(
                    stringResource(
                        R.string.insights_per_day,
                        String.format(Locale.getDefault(), "%.1f", data.total / InsightsViewModel.PERIOD_DAYS.toDouble()),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )

                if (data.top.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.insights_top))
                    TopContacts(data.top, onOpenThread)
                }

                SectionHeader(stringResource(R.string.insights_hours))
                HourChart(data.byHour)

                SectionHeader(stringResource(R.string.insights_days))
                DayChart(data.byDay)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Horizontal bars, labelled directly — the name and count are the point. */
@Composable
private fun TopContacts(top: List<TopContact>, onOpenThread: (Long) -> Unit) {
    val max = top.maxOf { it.count }.coerceAtLeast(1)
    Column(Modifier.padding(horizontal = 20.dp)) {
        top.forEach { contact ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = contact.threadId > 0) { onOpenThread(contact.threadId) }
                    .padding(vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        contact.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        contact.count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth(contact.count / max.toFloat())
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

/**
 * Vertical bars for a single series. Tapping a bar shows its value in the
 * caption, which is the touch equivalent of a hover tooltip.
 */
@Composable
private fun BarChart(
    values: List<Int>,
    caption: (index: Int) -> String,
    axisLabels: List<Pair<Int, String>>,
    defaultIndex: Int,
) {
    var selected by remember(values) { mutableIntStateOf(defaultIndex) }
    val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val bar = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)

    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(
            caption(selected),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().height(110.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { i, v ->
                // The hit target is the whole column, wider and taller than the bar.
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { selected = i }
                        .padding(horizontal = 1.dp), // 2px gap between bars
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight((v / max.toFloat()).coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (i == selected) bar else dim)
                    )
                }
            }
        }
        // Recessive baseline and sparse axis labels.
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                values.indices.forEach { i ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        axisLabels.firstOrNull { it.first == i }?.let { (_, label) ->
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HourChart(byHour: List<Int>) {
    val hourFormat = remember { SimpleDateFormat("h a", Locale.getDefault()) }
    fun hourLabel(h: Int): String = hourFormat.format(
        Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, 0) }.time
    )
    val busiest = byHour.indices.maxByOrNull { byHour[it] } ?: 0
    val busiestText = stringResource(R.string.insights_busiest, hourLabel(busiest))
    BarChart(
        values = byHour,
        caption = { i ->
            if (i == busiest) busiestText else "${hourLabel(i)} · ${byHour[i]}"
        },
        axisLabels = listOf(0, 6, 12, 18).map { it to hourLabel(it) },
        defaultIndex = busiest,
    )
}

@Composable
private fun DayChart(byDay: List<Pair<Long, Int>>) {
    BarChart(
        values = byDay.map { it.second },
        caption = { i -> "${Formatting.dayAndMonth(byDay[i].first)} · ${byDay[i].second}" },
        axisLabels = listOf(0, 7, 13).map { it to Formatting.dayAndMonth(byDay[it].first) },
        defaultIndex = byDay.lastIndex,
    )
}
