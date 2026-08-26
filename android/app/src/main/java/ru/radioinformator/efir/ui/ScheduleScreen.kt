package ru.radioinformator.efir.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.radioinformator.efir.model.EfirUiState
import ru.radioinformator.efir.model.ScheduleRule
import ru.radioinformator.efir.net.EfirPrefs
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Планировщик вещания.
 *
 * Для тех, кто вещает по расписанию, а не по случаю: завтрак до одиннадцати,
 * обед до трёх, рынок по выходным, распродажа на три дня. Держать телефон в
 * руках и менять текст вручную такие люди не будут.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    state: EfirUiState,
    onBack: () -> Unit,
    onAdd: (ScheduleRule) -> Unit,
    onDelete: (String) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    var showEditor by rememberSaveable { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale("ru")) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад",
                                tint = EfirGreen,
                            )
                        }
                    },
                    title = {
                        Text(
                            text = "РАСПИСАНИЕ",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp,
                            fontSize = 15.sp,
                        )
                    },
                    actions = {
                        IconButton(onClick = { showEditor = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Добавить", tint = EfirAmber)
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(start = 14.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "ПЛАНИРОВЩИК",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = if (state.schedule.enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Switch(
                        checked = state.schedule.enabled,
                        onCheckedChange = onToggleEnabled,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (state.schedule.enabled) "сам меняет эфир" else "выключен",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = "Пока планировщик включён, он сам ставит в эфир подходящее " +
                        "правило и снимает его, когда время вышло. Отправка сообщения " +
                        "вручную заменит то, что он поставил, до следующей проверки.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )

                if (state.schedule.rules.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "ПРАВИЛ НЕТ\n\nНажмите «плюс» вверху, чтобы задать,\n" +
                                "что и когда уходит в эфир.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn {
                        items(items = state.schedule.rules, key = { it.id }) { rule ->
                            RuleRow(
                                rule = rule,
                                channelTitle = state.channelTitle(rule.channel),
                                isActive = rule.id == state.schedule.activeRuleId,
                                dateFormat = dateFormat,
                                onDelete = { onDelete(rule.id) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        RuleEditor(
            state = state,
            onDismiss = { showEditor = false },
            onSave = {
                onAdd(it)
                showEditor = false
            },
        )
    }
}

@Composable
private fun RuleRow(
    rule: ScheduleRule,
    channelTitle: String,
    isActive: Boolean,
    dateFormat: SimpleDateFormat,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rule.timeLabel,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) scheme.primary else scheme.onSurface,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = rule.daysLabel,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = scheme.onSurfaceVariant,
                )
                if (isActive) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "В ЭФИРЕ",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary,
                    )
                }
            }

            Text(
                text = "к${rule.channel} · $channelTitle",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = scheme.secondary,
                modifier = Modifier.padding(top = 2.dp),
            )

            if (rule.fromDateMillis > 0 || rule.toDateMillis > 0) {
                val from = if (rule.fromDateMillis > 0) dateFormat.format(Date(rule.fromDateMillis)) else "…"
                val to = if (rule.toDateMillis > 0) dateFormat.format(Date(rule.toDateMillis)) else "…"
                Text(
                    text = "период $from — $to",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = scheme.onSurfaceVariant,
                )
            }

            Text(
                text = rule.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = scheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Удалить правило",
                modifier = Modifier.size(16.dp),
                tint = EfirRose,
            )
        }
    }
}

/** Редактор правила: текст, канал, время, дни недели и необязательный период. */
@Composable
private fun RuleEditor(
    state: EfirUiState,
    onDismiss: () -> Unit,
    onSave: (ScheduleRule) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var channel by rememberSaveable { mutableStateOf(state.transmitChannel) }
    var fromMinutes by rememberSaveable { mutableStateOf(9 * 60) }
    var toMinutes by rememberSaveable { mutableStateOf(18 * 60) }
    var days by rememberSaveable { mutableStateOf(setOf<Int>()) }

    val canSave = text.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Новое правило", fontFamily = FontFamily.Monospace, fontSize = 15.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    label = { Text("Что вещать", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    shape = RoundedCornerShape(8.dp),
                )

                Spacer(Modifier.height(10.dp))
                Stepper(
                    label = "Канал",
                    value = "к$channel · ${state.channelTitle(channel)}",
                    onMinus = { if (channel > EfirPrefs.CHANNEL_MIN) channel-- },
                    onPlus = { if (channel < EfirPrefs.CHANNEL_MAX) channel++ },
                )

                Stepper(
                    label = "С",
                    value = ScheduleRule.formatMinutes(fromMinutes),
                    onMinus = { fromMinutes = (fromMinutes - 30 + 1440) % 1440 },
                    onPlus = { fromMinutes = (fromMinutes + 30) % 1440 },
                )
                Stepper(
                    label = "До",
                    value = ScheduleRule.formatMinutes(toMinutes),
                    onMinus = { toMinutes = (toMinutes - 30 + 1440) % 1440 },
                    onPlus = { toMinutes = (toMinutes + 30) % 1440 },
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Дни недели",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ScheduleRule.DAY_ORDER.forEach { day ->
                        val on = day in days
                        Text(
                            text = ScheduleRule.DAY_NAMES[day].orEmpty(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = if (on) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .background(
                                    color = if (on) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                )
                                .clickable {
                                    days = if (on) days - day else days + day
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                        )
                    }
                }
                Text(
                    text = if (days.isEmpty()) "ничего не выбрано — значит каждый день" else "",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        ScheduleRule(
                            id = ScheduleRule.newId(),
                            text = text.trim(),
                            channel = channel,
                            fromMinutes = fromMinutes,
                            toMinutes = toMinutes,
                            weekDays = days,
                        )
                    )
                },
                enabled = canSave,
            ) {
                Text("Добавить", fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", fontFamily = FontFamily.Monospace)
            }
        },
    )
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onMinus) {
            Icon(Icons.Filled.Remove, contentDescription = "Меньше")
        }
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(140.dp),
        )
        IconButton(onClick = onPlus) {
            Icon(Icons.Filled.Add, contentDescription = "Больше")
        }
    }
}
