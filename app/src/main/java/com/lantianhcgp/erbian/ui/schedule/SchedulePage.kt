package com.lantianhcgp.erbian.ui.schedule

import android.Manifest
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantianhcgp.erbian.ui.AppViewModel
import com.lantianhcgp.erbian.ui.dockBottomInset
import com.lantianhcgp.erbian.ui.widgets.CardTitle
import com.lantianhcgp.erbian.ui.widgets.ErbianCard
import com.lantianhcgp.erbian.ui.widgets.PillButton
import model.Message
import model.MsgKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class CalEvent(val id: String, val day: String, val title: String, val time: String)

/** 页面统一页头（statusBars 避让 + 标题字重阶梯） */
@Composable
fun PageHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        // 2026-10-16 夜间实测：标题默认 content color 落在纯黑 rgb(0,0,0)（频道页在同一色板下却是白的），
        // 用户真机复现同样问题 → 显式绑定 onSurface，夜间随色板变白
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp),
    )
}

/**
 * 日程页 v2（spec §4）：**单一 LazyColumn**（根治双懒列嵌套溢出）、
 * 页头避让状态栏、内容垫过 dock；查询/权限逻辑不变（测试钩子文本不变）。
 */
@Composable
fun SchedulePage(vm: AppViewModel) {
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(false) }
    var events by remember { mutableStateOf<List<CalEvent>>(emptyList()) }
    var calNote by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
    }
    LaunchedEffect(Unit) {
        permitted = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (!permitted) launcher.launch(Manifest.permission.READ_CALENDAR)
    }
    LaunchedEffect(permitted) {
        if (!permitted) { calNote = "没有日历权限，下面只显示待确认项「耳边待办」"; return@LaunchedEffect }
        events = runCatching { queryErbianEvents(context) }.getOrElse {
            calNote = "「耳边待办」日历读取失败：${it.message}"; emptyList()
        }
        if (events.isEmpty() && calNote.isEmpty()) calNote = "日历里还没有「耳边待办」，跟我说点要做的事就会有"
    }

    val channel by vm.channel.collectAsState()
    val pendings = channel.filter {
        it.kind == MsgKind.TODO_CONFIRM && it.body !in channel.filter { c -> c.kind == MsgKind.TODO_RESULT }.map { r -> r.body.removePrefix("已加入日程：").removePrefix("确认：") }
    }.takeLast(10)

    val grouped = remember(events) { events.groupBy { it.day } }

    // B5：删除结果回来后重查日历（todoNote 变化即触发一次）
    LaunchedEffect(vm.todoNote) {
        if (vm.todoNote != null) {
            kotlinx.coroutines.delay(300)
            events = runCatching { queryErbianEvents(context) }.getOrDefault(events)
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("日程")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = dockBottomInset()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionLabel("耳边待办") }
            if (events.isEmpty()) {
                item {
                    Text(
                        calNote.ifEmpty { "日历里还没有「耳边待办」" },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            } else {
                grouped.forEach { (day, list) ->
                    item(key = "day-$day") {
                        Text(
                            day,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    items(list.size, key = { "$day-$it" }) { idx ->
                        val e = list[idx]
                        // B5（2026-10-16 用户）：待办要能删（画像不加删除）。点一次→确认态→再点执行。
                        var confirmDel by remember { mutableStateOf(false) }
                        ErbianCard {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    if (confirmDel) {
                                        Text(
                                            vm.todoNote ?: "删除这条待办？",
                                            fontSize = 11.sp,
                                            color = if (vm.todoNote != null) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }
                                Text(
                                    e.time,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (!confirmDel) {
                                    androidx.compose.material3.IconButton(
                                        onClick = { confirmDel = true },
                                    ) {
                                        androidx.compose.material3.Icon(
                                            androidx.compose.material.icons.Icons.Outlined.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(18.dp),
                )
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        PillButton("删除", onClick = {
                                            vm.deleteCalendarEvent(e.id)
                                            confirmDel = false
                                        })
                                        PillButton("取消", onClick = { confirmDel = false }, filled = false)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (pendings.isNotEmpty()) {
                item { SectionLabel("待确认") }
                items(pendings.size, key = { pendings[it].id }) { idx ->
                    val m: Message = pendings[idx]
                    ErbianCard {
                        Text(m.body, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton("确认", onClick = { vm.send("确认：${m.body}") })
                            PillButton("不用", onClick = { vm.send("不用了：${m.body}") }, filled = false)
                        }
                    }
                }
            }
        }
    }
}

private fun queryErbianEvents(context: android.content.Context): List<CalEvent> {
    val out = mutableListOf<CalEvent>()
    val dayFmt = SimpleDateFormat("M月d日 EEEE", Locale.CHINA)
    val timeFmt = SimpleDateFormat("HH:mm", Locale.CHINA)
    // 先取「耳边待办」日历 id（不能把 Calendars 列混进 Events 投影——没有 join 会抛异常）
    val calIds = mutableSetOf<String>()
    context.contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.NAME),
        null, null, null,
    )?.use { c ->
        val idIdx = c.getColumnIndex(CalendarContract.Calendars._ID)
        val nameIdx = c.getColumnIndex(CalendarContract.Calendars.NAME)
        while (c.moveToNext()) {
            if ((c.getString(nameIdx) ?: "").contains("耳边待办")) calIds.add(c.getString(idIdx))
        }
    }
    if (calIds.isEmpty()) return out
    val idList = calIds.joinToString(",")
    context.contentResolver.query(
        CalendarContract.Events.CONTENT_URI,
        arrayOf(CalendarContract.Events._ID, CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.ALL_DAY),
        "${CalendarContract.Events.CALENDAR_ID} IN ($idList) AND ${CalendarContract.Events.DELETED}!=1",
        null,
        "${CalendarContract.Events.DTSTART} ASC",
    )?.use { c ->
        val idIdx = c.getColumnIndex(CalendarContract.Events._ID)
        val titleIdx = c.getColumnIndex(CalendarContract.Events.TITLE)
        val startIdx = c.getColumnIndex(CalendarContract.Events.DTSTART)
        val allDayIdx = c.getColumnIndex(CalendarContract.Events.ALL_DAY)
        while (c.moveToNext()) {
            val start = c.getLong(startIdx)
            val allDay = c.getInt(allDayIdx) == 1
            out.add(
                CalEvent(
                    id = c.getString(idIdx) ?: "",
                    day = dayFmt.format(Date(start)),
                    title = c.getString(titleIdx) ?: "(无标题)",
                    time = if (allDay) "全天" else timeFmt.format(Date(start)),
                )
            )
        }
    }
    return out
}
