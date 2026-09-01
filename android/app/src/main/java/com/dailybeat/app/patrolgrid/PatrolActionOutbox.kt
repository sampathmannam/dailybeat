package com.dailybeat.app.patrolgrid

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

class PatrolActionOutbox(
    context: Context,
    private val remote: PatrolGridRemote,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    @Synchronized
    fun enqueueVisit(missionId: String, priorityLocationId: String): String {
        val id = UUID.randomUUID().toString()
        append(
            PendingAction(
                id = id,
                type = TYPE_VISIT,
                missionId = missionId,
                priorityLocationId = priorityLocationId,
                category = null,
                detail = null,
                occurredAtMs = clock(),
            ),
        )
        return id
    }

    @Synchronized
    fun enqueueUpdate(missionId: String, category: String, detail: String): String {
        require(category in setOf("observation", "operational_deviation", "safety_event"))
        require(detail.isNotBlank())
        val id = UUID.randomUUID().toString()
        append(
            PendingAction(
                id = id,
                type = TYPE_UPDATE,
                missionId = missionId,
                priorityLocationId = null,
                category = category,
                detail = detail.take(4_000),
                occurredAtMs = clock(),
            ),
        )
        return id
    }

    @Synchronized
    fun pendingCount(): Int = read().size

    suspend fun syncPending(): Result<Int> = try {
        var synced = 0
        read().forEach { action ->
            when (action.type) {
                TYPE_VISIT -> remote.markPriorityVisited(
                    missionId = action.missionId,
                    priorityLocationId = requireNotNull(action.priorityLocationId),
                    clientVisitId = action.id,
                    visitedAtMs = action.occurredAtMs,
                )
                TYPE_UPDATE -> remote.addFieldUpdate(
                    missionId = action.missionId,
                    category = requireNotNull(action.category),
                    detail = requireNotNull(action.detail),
                    clientUpdateId = action.id,
                    occurredAtMs = action.occurredAtMs,
                )
                else -> error("Unsupported PatrolGrid outbox action")
            }.getOrThrow()
            remove(action.id)
            synced += 1
        }
        Result.success(synced)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_ACTIONS).commit()
    }

    @Synchronized
    private fun append(action: PendingAction) {
        val actions = read().toMutableList().apply { add(action) }
        check(write(actions)) { "Unable to save the patrol action securely." }
    }

    @Synchronized
    private fun remove(id: String) {
        check(write(read().filterNot { it.id == id })) { "Unable to update the patrol action queue." }
    }

    private fun read(): List<PendingAction> = runCatching {
        val rows = JSONArray(prefs.getString(KEY_ACTIONS, "[]"))
        (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            PendingAction(
                id = row.getString("id"),
                type = row.getString("type"),
                missionId = row.getString("missionId"),
                priorityLocationId = row.optString("priorityLocationId").takeIf(String::isNotBlank),
                category = row.optString("category").takeIf(String::isNotBlank),
                detail = row.optString("detail").takeIf(String::isNotBlank),
                occurredAtMs = row.getLong("occurredAtMs"),
            )
        }
    }.getOrElse {
        // A damaged encrypted queue must fail closed instead of being overwritten.
        throw IllegalStateException("The secure patrol action queue could not be read.", it)
    }

    private fun write(actions: List<PendingAction>): Boolean {
        val rows = JSONArray()
        actions.forEach { action ->
            rows.put(
                JSONObject()
                    .put("id", action.id)
                    .put("type", action.type)
                    .put("missionId", action.missionId)
                    .put("priorityLocationId", action.priorityLocationId ?: "")
                    .put("category", action.category ?: "")
                    .put("detail", action.detail ?: "")
                    .put("occurredAtMs", action.occurredAtMs),
            )
        }
        return prefs.edit().putString(KEY_ACTIONS, rows.toString()).commit()
    }

    private data class PendingAction(
        val id: String,
        val type: String,
        val missionId: String,
        val priorityLocationId: String?,
        val category: String?,
        val detail: String?,
        val occurredAtMs: Long,
    )

    private companion object {
        const val FILE_NAME = "patrolgrid_action_outbox"
        const val KEY_ACTIONS = "pending_actions"
        const val TYPE_VISIT = "visit"
        const val TYPE_UPDATE = "update"
    }
}
