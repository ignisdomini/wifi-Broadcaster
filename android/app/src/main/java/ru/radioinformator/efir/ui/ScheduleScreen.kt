package ru.radioinformator.efir.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.radioinformator.efir.model.EfirUiState
import ru.radioinformator.efir.model.ScheduleRule
import ru.radioinformator.efir.net.EfirPrefs
import java.text.SimpleDateFormat
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

    EfirBackdrop(alive = state.schedule.enabled, modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Box(Modifier.statusBarsPadding()) {
                    EfirScreenHeader(
                        title = "Расписание",
                        subtitle = if (state.schedule.rules.isEmpty()) {
                            "правил нет"
                        } else {
                            "правил: ${state.schedule.rules.size}"
                        },
                        onBack = onBack,
                        accent = EfirRose,
                    ) {
                        GlowIconButton(
                            icon = Icons.Filled.Add,
                            description = "Добавить правило",
                            tint = EfirAmber,
                            onClick = { showEditor = true },
                            size = 40.dp,
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    glow = if (state.schedule.enabled) EfirRose else null,
                    glowAlpha = 0.14f,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .halo(
                                    EfirRose,
                                    alpha = if (state.schedule.enabled) 0.30f else 0f,
                                    spread = 1.5f,
                                )
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    EfirRose.copy(alpha = if (state.schedule.enabled) 0.20f else 0.08f)
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = if (state.schedule.enabled) EfirRose else EfirRose.copy(alpha = 0.45f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Планировщик",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (state.schedule.enabled) {
                                    "сам меняет эфир по времени"
                                } else {
                                    "выключен"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        EfirSwitch(
                            checked = state.schedule.enabled,
                            onCheckedChange = onToggleEnabled,
                            tint = EfirRose,
                        )
                    }
                }

                Text(
                    text = "Пока планировщик включён, он сам ставит в эфир подходящее " +
                        "правило и снимает его, когда время вышло. Отправка сообщения " +
                        "вручную заменит то, что он поставил, до следующей проверки.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                )

                if (state.schedule.rules.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .halo(EfirRose, alpha = 0.18f, spread = 1.6f)
                                .glass(EfirCardShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = EfirRose,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = "Правил нет",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Нажмите «плюс» вверху, чтобы задать, что и когда уходит в эфир.",
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 20.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(items = state.schedule.rules, key = { it.id }) { rule ->
                            RuleRow(
                                rule = rule,
                                channelTitle = state.channelTitle(rule.channel),
                                isActive = rule.id == state.schedule.activeRuleId,
                                dateFormat = dateFormat,
                                onDelete = { onDelete(rule.id) },
                                modifier = Modifier.animateItem(),
                            )
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
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        glow = if (isActive) EfirGreen else null,
        glowAlpha = 0.16f,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rule.timeLabel,
                    fontFamily = EfirMono,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) EfirGreen else scheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                if (isActive) {
                    PulseDot(active = true, color = EfirGreen, size = 8.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "в эфире",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EfirGreen,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Удалить правило",
                        modifier = Modifier.size(16.dp),
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EfirTag(text = rule.daysLabel, color = EfirSky)
                EfirTag(text = "к${rule.channel} · $channelTitle", color = EfirAmber)
            }

            if (rule.fromDateMillis > 0 || rule.toDateMillis > 0) {
                val from = if (rule.fromDateMillis > 0) dateFormat.format(Date(rule.fromDateMillis)) else "…"
                val to = if (rule.toDateMillis > 0) dateFormat.format(Date(rule.toDateMillis)) else "…"
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "период $from — $to",
                    fontFamily = EfirMono,
                    fontSize = 11.sp,
                    color = scheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = rule.text,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = scheme.onSurface,
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
        title = { Text("Новое правило", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    label = { Text("Что вещать", fontSize = 12.sp) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = RoundedCornerShape(16.dp),
                )

                Spacer(Modifier.height(12.dp))
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

                Spacer(Modifier.height(12.dp))
                EfirSectionLabel("Дни недели")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    ScheduleRule.DAY_ORDER.forEach { day ->
                        val on = day in days
                        Text(
                            text = ScheduleRule.DAY_NAMES[day].orEmpty(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (on) EfirInk else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .then(
                                    if (on) {
                                        Modifier.halo(EfirGreen, alpha = 0.30f, spread = 1.5f)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (on) EfirGreen else Color.White.copy(alpha = 0.06f)
                                )
                                .clickable { days = if (on) days - day else days + day }
                                .padding(horizontal = 9.dp, vertical = 7.dp),
                        )
                    }
                }
                if (days.isEmpty()) {
                    Text(
                        text = "ничего не выбрано — значит каждый день",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
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
                Text("Добавить", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onMinus) {
            Icon(Icons.Filled.Remove, contentDescription = "Меньше", tint = EfirSky)
        }
        Text(
            text = value,
            fontFamily = EfirMono,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(140.dp),
        )
        IconButton(onClick = onPlus) {
            Icon(Icons.Filled.Add, contentDescription = "Больше", tint = EfirSky)
        }
    }
}
