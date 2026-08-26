package ru.radioinformator.efir.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    val stampFormat = remember { SimpleDateFormat("dd.MM.yyyy · HH:mm:ss", Locale("ru")) }
    val context = LocalContext.current

    EfirBackdrop(alive = false, modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Box(Modifier.statusBarsPadding()) {
                    EfirScreenHeader(
                        title = "Журнал",
                        subtitle = if (entries.isEmpty()) {
                            "записей пока нет"
                        } else {
                            "записей: ${entries.size}"
                        },
                        onBack = onBack,
                        accent = EfirSky,
                    ) {
                        if (entries.isNotEmpty()) {
                            GlowIconButton(
                                icon = Icons.Filled.DeleteSweep,
                                description = "Очистить журнал",
                                tint = EfirRose,
                                onClick = { confirmClear = true },
                                size = 40.dp,
                            )
                        }
                    }
                }
            },
        ) { padding ->
            if (entries.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(74.dp)
                            .halo(EfirSky, alpha = 0.20f, spread = 1.6f)
                            .glass(EfirCardShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            tint = EfirSky,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "Пока пусто",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Сюда попадает всё принятое из эфира — с датой, временем " +
                            "и каналом. Записи остаются после перезапуска.",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    // Очистка необратима: восстановить записи неоткуда, в эфире их давно нет.
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text("Очистить журнал?", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "Все ${entries.size} записей будут стёрты. Восстановить их " +
                        "неоткуда — в эфире этих сообщений уже нет.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    confirmClear = false
                }) {
                    Text("Стереть", color = EfirRose, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Отмена") }
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
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = if (entry.isDirect) EfirAmber else EfirSky

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        glow = if (entry.isDirect) EfirAmber else null,
        glowAlpha = 0.10f,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HandleAvatar(handle = entry.nick, color = accent, size = 32.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    // Позывной ведёт в ленту автора: код пришёл из эфира,
                    // другого пути к чужой ленте в сети нет.
                    Text(
                        text = entry.nick.uppercase(Locale("ru")),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (canOpenFeed) {
                            Modifier.clickable(onClick = onOpenFeed)
                        } else {
                            Modifier
                        },
                    )
                    Text(
                        text = stamp,
                        fontFamily = EfirMono,
                        fontSize = 11.sp,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Удалить запись",
                        modifier = Modifier.size(16.dp),
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EfirTag(
                    text = "к${entry.channel} · ${channelTitle(entry.channel)}",
                    color = EfirGreen,
                )
                if (entry.isDirect) {
                    EfirTag(
                        text = "личное",
                        color = EfirAmber,
                        icon = Icons.Filled.Lock,
                        solid = true,
                    )
                }
                if (canOpenFeed) {
                    EfirTag(text = "лента", color = EfirSky, icon = Icons.Filled.Badge)
                }
            }

            if (entry.text.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = entry.text,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = scheme.onSurface,
                )
            }
        }
    }
}
