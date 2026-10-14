package com.lantianhcgp.erbian.data

import json.Json
import json.JsonValue
import model.Message
import queue.QueueStore
import java.io.File

/**
 * 上行队列文件快照存储（设计文档 §三：离线积压持久化）。
 * 每次 saveAll 全量覆盖 queue.json（量小，原子写：临时文件 + rename）。
 */
class FileQueueStore(private val file: File) : QueueStore {

    override fun saveAll(msgs: List<Message>) {
        val arr = JsonValue.Arr(msgs.map { it.toJson() })
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(Json.write(arr))
        if (!tmp.renameTo(file)) {
            file.writeText(Json.write(arr))
            tmp.delete()
        }
    }

    override fun loadAll(): List<Message> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val v = Json.parse(file.readText())
            v.asArray().mapNotNull { item ->
                runCatching { Message.fromJson(item) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }
}
