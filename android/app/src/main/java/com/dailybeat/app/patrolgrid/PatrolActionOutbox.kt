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
    fun enqueueVisit(missionId: String, sessionId: String, priorityLocationId: String): String {
        require(sessionId.isNotBlank())
        val id = UUID.randomUUID().toString()
        append(
            PendingAction(
                id = id,
                type = TYPE_VISIT,
                missionId = missionId,
                sessionId = sessionId,
                priorityLocationId = priorityLocationId,
                category = null,
                detail = null,
                reviewId = null,
                occurredAtMs = clock(),
            ),
        )
        return id
    }

    @Synchronized
    fun enqueueUpdate(
        missionId: String,
        sessionId: String?,
        category: String,
        detail: String,
        reviewId: String? = null,
    ): String {
        require(category in setOf("observation", "operational_deviation", "safety_event", "review_context"))
        require(detail.isNotBlank())
        require((category == "review_context") == !reviewId.isNullOrBlank()) {
            "A review id is required only for review context responses."
        }
        require((category == "review_context") == sessionId.isNullOrBlank()) {
            "Operational evidence requires a session; review context requires a review link."
        }
        val id = UUID.randomUUID().toString()
        append(
            PendingAction(
                id = id,
                type = TYPE_UPDATE,
                missionId = missionId,
                sessionId = sessionId,
                priorityLocationId = null,
                category = category,
                detail = detail.take(4_000),
                reviewId = reviewId,
                occurredAtMs = clock(),
            ),
        )
        return id
    }

    @Synchronized
    fun pendingCount(): Int = read().size

    @Synchronized
    internal fun pendingMissionIds(): Set<String> = read().mapTo(mutableSetOf()) { it.missionId }

    @Synchronized
    internal fun inspectMissionIdsForRetention(): Set<String> = runCatching {
        val rows = JSONArray(prefs.getString(KEY_ACTIONS, "[]"))
        (0 until rows.length()).mapNotNullTo(mutableSetOf()) { index ->
            rows.optJSONObject(index)?.optString("missionId")?.takeIf(String::isNotBlank)
        }
    }.getOrDefault(emptySet())

    /** Plans a sweep without mutating the encrypted queue, so intent can be journaled first. */
    @Synchronized
    internal fun inspectRetention(dueMissionIds: Set<String>): PatrolActionRetentionResult =
        retentionResult(dueMissionIds, apply = false)

    /**
     * Removes every action for a mission whose server-owned deadline is due. The action's
     * own event timestamp is never used as a retention clock. Unreadable actions are also
     * removed because they can neither be synchronized nor safely retained indefinitely.
     */
    @Synchronized
    internal fun purgeForMissionDeadlines(
        dueMissionIds: Set<String>,
    ): PatrolActionRetentionResult = retentionResult(dueMissionIds, apply = true)

    private fun retentionResult(
        dueMissionIds: Set<String>,
        apply: Boolean,
    ): PatrolActionRetentionResult {
        val rows = try {
            JSONArray(prefs.getString(KEY_ACTIONS, "[]"))
        } catch (_: Exception) {
            if (apply) {
                check(prefs.edit().remove(KEY_ACTIONS).commit()) {
                    "Unable to remove the unreadable patrol action queue."
                }
            }
            return PatrolActionRetentionResult(unreadableContainerCount = 1)
        }

        val retained = mutableListOf<PendingAction>()
        val affectedMissionIds = mutableSetOf<String>()
        var expiredCount = 0
        var malformedCount = 0
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index)
            val action = row?.let(::parseActionOrNull)
            when {
                action == null -> {
                    row?.optString("missionId")
                        ?.takeIf(String::isNotBlank)
                        ?.let(affectedMissionIds::add)
                    malformedCount += 1
                }
                action.missionId in dueMissionIds -> {
                    affectedMissionIds += action.missionId
                    expiredCount += 1
                }
                else -> retained += action
            }
        }
        if (apply) {
            // This also upgrades readable legacy rows (createdAtMs) to occurredAtMs.
            check(write(retained)) { "Unable to apply the patrol action retention policy." }
        }
        return PatrolActionRetentionResult(
            expiredCount = expiredCount,
            malformedCount = malformedCount,
            affectedMissionIds = affectedMissionIds,
        )
    }

    suspend fun syncPending(): Result<Int> = try {
        var synced = 0
        read().forEach { action ->
            try {
                when (action.type) {
                    TYPE_VISIT -> remote.markPriorityVisited(
                        sessionId = requireNotNull(action.sessionId),
                        priorityLocationId = requireNotNull(action.priorityLocationId),
                        clientVisitId = action.id,
                        visitedAtMs = action.occurredAtMs,
                    )
                    TYPE_UPDATE -> remote.addFieldUpdate(
                        sessionId = action.sessionId,
                        category = requireNotNull(action.category),
                        detail = requireNotNull(action.detail),
                        clientUpdateId = action.id,
                        occurredAtMs = action.occurredAtMs,
                        reviewId = action.reviewId,
                    )
                    else -> error("Unsupported PatrolGrid outbox action")
                }.getOrThrow()
            } catch (error: PatrolGridEvidenceUnavailableException) {
                throw PatrolEvidenceDestinationUnavailableException(action.missionId, error)
            }
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
        check(prefs.edit().remove(KEY_ACTIONS).commit()) {
            "Unable to clear the secure patrol action queue."
        }
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
            requireNotNull(parseActionOrNull(rows.getJSONObject(index)))
        }
    }.getOrElse {
        // A damaged encrypted queue must fail closed instead of being overwritten.
        throw IllegalStateException("The secure patrol action queue could not be read.", it)
    }

    private fun parseActionOrNull(row: JSONObject): PendingAction? = runCatching {
        val type = row.getString("type")
        val sessionId = row.optString("sessionId").takeIf(String::isNotBlank)
        val priorityLocationId = row.optString("priorityLocationId").takeIf(String::isNotBlank)
        val category = row.optString("category").takeIf(String::isNotBlank)
        val detail = row.optString("detail").takeIf(String::isNotBlank)
        val reviewId = row.optString("reviewId").takeIf(String::isNotBlank)
        val occurredAtMs = when {
            row.has("occurredAtMs") -> row.getLong("occurredAtMs")
            row.has("createdAtMs") -> row.getLong("createdAtMs")
            else -> error("The action has no event timestamp.")
        }
        val action = PendingAction(
            id = row.getString("id"),
            type = type,
            missionId = row.getString("missionId"),
            sessionId = sessionId,
            priorityLocationId = priorityLocationId,
            category = category,
            detail = detail,
            reviewId = reviewId,
            occurredAtMs = occurredAtMs,
        )
        require(action.id.isNotBlank() && action.missionId.isNotBlank() && occurredAtMs > 0L)
        when (type) {
            TYPE_VISIT -> require(
                !sessionId.isNullOrBlank() &&
                    !priorityLocationId.isNullOrBlank() &&
                    category == null && detail == null && reviewId == null,
            )
            TYPE_UPDATE -> {
                require(category in UPDATE_CATEGORIES && !detail.isNullOrBlank())
                require((category == "review_context") == !reviewId.isNullOrBlank())
                require((category == "review_context") == sessionId.isNullOrBlank())
            }
            else -> error("Unsupported PatrolGrid outbox action")
        }
        action
    }.getOrNull()

    private fun write(actions: List<PendingAction>): Boolean {
        val rows = JSONArray()
        actions.forEach { action ->
            rows.put(
                JSONObject()
                    .put("id", action.id)
                    .put("type", action.type)
                    .put("missionId", action.missionId)
                    .put("sessionId", action.sessionId ?: "")
                    .put("priorityLocationId", action.priorityLocationId ?: "")
                    .put("category", action.category ?: "")
                    .put("detail", action.detail ?: "")
                    .put("reviewId", action.reviewId ?: "")
                    .put("occurredAtMs", action.occurredAtMs),
            )
        }
        return prefs.edit().putString(KEY_ACTIONS, rows.toString()).commit()
    }

    private data class PendingAction(
        val id: String,
        val type: String,
        val missionId: String,
        val sessionId: String?,
        val priorityLocationId: String?,
        val category: String?,
        val detail: String?,
        val reviewId: String?,
        val occurredAtMs: Long,
    )

    private companion object {
        const val FILE_NAME = "patrolgrid_action_outbox"
        const val KEY_ACTIONS = "pending_actions"
        const val TYPE_VISIT = "visit"
        const val TYPE_UPDATE = "update"
        val UPDATE_CATEGORIES = setOf(
            "observation",
            "operational_deviation",
            "safety_event",
            "review_context",
        )
    }
}

internal data class PatrolActionRetentionResult(
    val expiredCount: Int = 0,
    val malformedCount: Int = 0,
    val unreadableContainerCount: Int = 0,
    val affectedMissionIds: Set<String> = emptySet(),
) {
    val discardedCount: Int
        get() = expiredCount + malformedCount + unreadableContainerCount
}
