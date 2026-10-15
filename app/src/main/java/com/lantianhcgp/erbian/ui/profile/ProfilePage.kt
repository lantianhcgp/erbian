package com.lantianhcgp.erbian.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantianhcgp.erbian.ui.AppViewModel
import com.lantianhcgp.erbian.ui.widgets.CardTitle
import com.lantianhcgp.erbian.ui.widgets.EmptyState
import com.lantianhcgp.erbian.ui.widgets.ErbianCard
import com.lantianhcgp.erbian.ui.widgets.PillButton
import json.Json
import json.JsonValue

private data class ProfileCard(val category: String, val title: String, val content: String, val confidence: Float, val evidence: List<String>)

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun ProfilePage(vm: AppViewModel) {
    val raw = vm.profileJson
    val cards = androidx.compose.runtime.remember(raw) { parseCards(raw) }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("画像", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
            PillButton("刷新", onClick = { vm.refresh() }, filled = false)
        }

        if (cards.isEmpty()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                EmptyState("画像还没有内容", "多和我说说话，我眼里的你就会慢慢成形")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = com.lantianhcgp.erbian.ui.dockBottomInset())) {
                items(cards, key = { it.title }) { c ->
                    ErbianCard {
                        Text(c.category, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        Text(c.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 2.dp))
                        Text(c.content, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("置信度 ${(c.confidence * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text("证据 ${c.evidence.size} 条", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        LinearProgressIndicator(
                            progress = { c.confidence.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                        if (c.evidence.isNotEmpty()) {
                            var expanded by remember { mutableStateOf(false) }
                            val shown = if (expanded) c.evidence else listOf(c.evidence.last())
                            shown.forEach { e ->
                                Text(
                                    "「$e」",
                                    fontSize = 11.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = if (!expanded && c.evidence.size > 1) Modifier
                                        .combinedClickable(onClick = {}, onLongClick = { expanded = true })
                                        .semantics { contentDescription = "证据展开" } else Modifier,
                                )
                            }
                            if (c.evidence.size > 1) {
                                Text(
                                    if (expanded) "收起" else "共${c.evidence.size}条证据，点按展开",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { expanded = !expanded },
                                )
                            }
                        }
                        Row(Modifier.padding(top = 6.dp)) {
                            PillButton("这条不对", onClick = { vm.send("画像这条不对：${c.title}") }, filled = false)
                        }
                    }
                }
            }
        }
    }
}

private fun parseCards(raw: String?): List<ProfileCard> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val root = Json.parse(raw)
        root.opt("cards")?.asArray().orEmpty().mapNotNull { item ->
            runCatching {
                ProfileCard(
                    category = item.optString("category", "其他"),
                    title = item.optString("title"),
                    content = item.optString("content"),
                    confidence = (item.opt("confidence") as? JsonValue.Num)?.v?.toFloat() ?: 0.5f,
                    evidence = item.opt("evidence")?.asArray()?.map { it.asString() } ?: emptyList(),
                ).takeIf { it.title.isNotEmpty() }
            }.getOrNull()
        }
    }.getOrDefault(emptyList())
}
