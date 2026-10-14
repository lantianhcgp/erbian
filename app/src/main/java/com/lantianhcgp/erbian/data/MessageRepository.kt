package com.lantianhcgp.erbian.data

import android.content.Context
import androidx.room.Room
import json.Json
import json.JsonValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import model.Message
import model.MsgKind
import model.MsgStatus
import queue.InMemoryQueueStore
import queue.QueueStore

/**
 * 消息仓库：频道历史(Room) + 上行队列持久化(文件) 统一入口。
 * 频道 Flow 供 UI 观察；队列驱动在 UplinkSender。
 */
class MessageRepository(context: Context, scope: CoroutineScope) {

    private val db = Room.databaseBuilder(context, ErbianDb::class.java, "erbian.db").build()
    private val dao = db.msgDao()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 频道消息（含发送状态）。 */
    val channel: StateFlow<List<Message>> = MutableStateFlow(emptyList()).also { flow ->
        io.launch {
            dao.observeAll().collect { list ->
                (flow as MutableStateFlow).value = list.map { it.toModel() }
            }
        }
    }

    /** 幂等插入；返回 false = id 已存在。 */
    suspend fun add(msg: Message): Boolean = dao.insert(msg.toEntity()) != -1L

    suspend fun setStatus(id: String, status: MsgStatus) = dao.updateStatus(id, status.name.lowercase())

    suspend fun latestTs(): Long = dao.maxTs() ?: 0L

    /** 上行队列持久化（文件快照，量小）。 */
    val queueStore: QueueStore = FileQueueStore(context.filesDir.resolve("queue.json"))
}

internal fun MsgEntity.toModel(): Message = Message(
    id = id,
    kind = runCatching { MsgKind.fromWire(kind) }.getOrDefault(MsgKind.REPLY),
    body = body,
    ts = ts,
    status = runCatching { MsgStatus.valueOf(status.uppercase()) }.getOrDefault(MsgStatus.PENDING),
    origin = origin,
    meta = runCatching { Json.parse(metaJson).asObject() }.getOrDefault(emptyMap()),
)

internal fun Message.toEntity(): MsgEntity = MsgEntity(
    id = id,
    kind = kind.toWire(),
    body = body,
    ts = ts,
    status = status.name.lowercase(),
    origin = origin,
    metaJson = Json.write(JsonValue.Obj(meta)),
)
