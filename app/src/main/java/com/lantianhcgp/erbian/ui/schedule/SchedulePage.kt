package com.lantianhcgp.erbian.ui.schedule

import android.Manifest
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantianhcgp.erbian.ui.AppViewModel
import com.lantianhcgp.erbian.ui.widgets.CardTitle
import com.lantianhcgp.erbian.ui.widgets.EmptyState
import com.lantianhcgp.erbian.ui.widgets.ErbianCard
import com.lantianhcgp.erbian.ui.widgets.PillButton
import model.MsgKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class CalEvent(val day: String, val title: String, val time: String)

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

    val confirms by vm.channel.collectAsState()
    val pendings = confirms.filter {
        it.kind == MsgKind.TODO_CONFIRM && it.body !in confirms.filter { c -> c.kind == MsgKind.TODO_RESULT }.map { r -> r.body.removePrefix("已加入日程：").removePrefix("确认：") }
    }.takeLast(10)

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Text("日程", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))

        CardTitle("耳边待办")
        if (events.isEmpty()) {
            Text(calNote.ifEmpty { "日历里还没有「耳边待办」" }, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            LazyColumn(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val grouped = events.groupBy { it.day }
                for ((day, list) in grouped) {
                    item {
                        Text(day, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                    }
                    items(list) { e ->
                        ErbianCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(e.title, fontSize = 14.sp)
                                Text(e.time, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        if (pendings.isNotEmpty()) {
            Text("待确认", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(pendings, key = { it.id }) { m ->
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
        arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.ALL_DAY),
        "${CalendarContract.Events.CALENDAR_ID} IN ($idList) AND ${CalendarContract.Events.DELETED}!=1",
        null,
        "${CalendarContract.Events.DTSTART} ASC",
    )?.use { c ->
        val titleIdx = c.getColumnIndex(CalendarContract.Events.TITLE)
        val startIdx = c.getColumnIndex(CalendarContract.Events.DTSTART)
        val allDayIdx = c.getColumnIndex(CalendarContract.Events.ALL_DAY)
        while (c.moveToNext()) {
            val start = c.getLong(startIdx)
            val allDay = c.getInt(allDayIdx) == 1
            out.add(
                CalEvent(
                    day = dayFmt.format(Date(start)),
                    title = c.getString(titleIdx) ?: "(无标题)",
                    time = if (allDay) "全天" else timeFmt.format(Date(start)),
                )
            )
        }
    }
    return out
}
