package ru.radioinformator.efir.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.radioinformator.efir.model.EfirMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Журнал принятых передач — отдельный экран.
 *
 * Лента живёт до перезапуска, журнал остаётся: сообщение исчезает из воздуха,
 * а запись о нём — нет. Отсюда же он и чистится, поштучно или целиком.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    entries: List<EfirMessage>,
    channelTitle: (Int) -> String,
    profileResolver: (String) -> String?,
    onBack: () -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val stampFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("ru")) }
    val context = LocalContext.current

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
                            text = "ЖУРНАЛ",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp,
                            fontSize = 16.sp,
                        )
                    },
                    actions = {
                        if (entries.isNotEmpty()) {
                            TextButton(onClick = { confirmClear = true }) {
                                Text(
                                    "очистить",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = EfirRose,
                                )
                            }
                        }
                    },
                )
            },
        ) { padding ->
            if (entries.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(80.dp))
                    Text(
                        text = "ПОКА ПУСТО\n\nСюда попадает всё принятое из эфира —\n" +
                            "с датой, временем и каналом.\nЗаписи остаются после перезапуска.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Scaffold
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(items = entries, key = { it.id }) { entry ->
                    JournalRow(
                        entry = entry,
                        channelTitle = channelTitle,
                        stamp = stampFormat.format(Date(entry.receivedAtMillis)),
                        onOpenFeed = {
                            entry.profileCode?.let(profileResolver)?.let { url ->
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        canOpenFeed = entry.profileCode?.let(profileResolver) != null,
                        onDelete = { onDelete(entry.id) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }

    // Очистка необратима: восстановить записи неоткуда, в эфире их давно нет.
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Очистить журнал?", fontFamily = FontFamily.Monospace, fontSize = 15.sp) },
            text = {
                Text(
                    text = "Все ${entries.size} записей будут стёрты. Восстановить их " +
                        "неоткуда — в эфире этих сообщений уже нет.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    confirmClear = false
                }) {
                    Text("Стереть", fontFamily = FontFamily.Monospace, color = EfirRose)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Отмена", fontFamily = FontFamily.Monospace)
                }
            },
        )
    }
}

@Composable
private fun JournalRow(
    entry: EfirMessage,
    channelTitle: (Int) -> String,
    stamp: String,
    onOpenFeed: () -> Unit,
    canOpenFeed: Boolean,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.background)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            if (entry.isDirect) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = scheme.secondary,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "ЛИЧНОЕ СООБЩЕНИЕ",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = scheme.secondary,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Ник ведёт в ленту автора: код пришёл из эфира, другого пути нет.
                Text(
                    text = entry.nick.uppercase(Locale("ru")),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canOpenFeed) EfirSky else scheme.primary,
                    textDecoration = if (canOpenFeed) TextDecoration.Underline else null,
                    modifier = if (canOpenFeed) Modifier.clickable(onClick = onOpenFeed) else Modifier,
                )
            }

            Text(
                text = "к${entry.channel} · ${channelTitle(entry.channel)}",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = stamp,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = entry.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = scheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Удалить запись",
                modifier = Modifier.size(16.dp),
                tint = EfirRose,
            )
        }
    }
}
