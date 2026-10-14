package queue

import json.Json
import json.JsonValue
import model.Message

interface QueueStore {
    fun saveAll(msgs: List<Message>)
    fun loadAll(): List<Message>
}

/**
 * 内存实现（测试用）。App 层用 Room/文件实现同一接口。
 */
class InMemoryQueueStore : QueueStore {
    var snapshots: List<List<Message>> = emptyList()
    private var data: List<Message> = emptyList()
    override fun saveAll(msgs: List<Message>) { data = msgs; snapshots = snapshots + listOf(msgs) }
    override fun loadAll(): List<Message> = data
}
