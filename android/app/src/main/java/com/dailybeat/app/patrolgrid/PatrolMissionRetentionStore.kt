package com.dailybeat.app.patrolgrid

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dailybeat.app.data.model.PatrolMission
import org.json.JSONObject

/**
 * Durable, encrypted copy of the server-owned mission retention clock.
 *
 * Event timestamps are deliberately absent. Every local evidence type for a mission is
 * governed by the one `retention_until` value calculated by PostgreSQL when that mission
 * first becomes terminal.
 */
internal class PatrolMissionRetentionStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    @Synchronized
    fun recordAuthoritativeMissions(missions: List<PatrolMission>) {
        val deadlines = read().toMutableMap()
        missions.forEach { mission ->
            val deadline = mission.retentionUntilEpochMs
            if (deadline == null) {
                // Never let a delayed pre-closure response erase a deadline already
                // learned from a newer response. Terminal missions are not reopenable.
                if (mission.id !in deadlines) deadlines[mission.id] = null
            } else {
                require(deadline > 0L) { "The server returned an invalid mission retention clock." }
                val existing = deadlines[mission.id]
                check(existing == null || existing == deadline) {
                    "The server changed an established mission retention clock."
                }
                deadlines[mission.id] = deadline
            }
        }
        check(write(deadlines)) { "Unable to save the mission retention clocks securely." }
    }

    @Synchronized
    fun dueMissionIds(nowMs: Long): Set<String> {
        require(nowMs > 0L)
        return read().filterValues { deadline -> deadline != null && deadline <= nowMs }.keys
    }

    @Synchronized
    fun knownMissionIds(): Set<String> = read().keys

    @Synchronized
    fun missionIdsWithoutDeadline(): Set<String> =
        read().filterValues { deadline -> deadline == null }.keys

    @Synchronized
    fun removeMissionIds(missionIds: Set<String>) {
        if (missionIds.isEmpty()) return
        val deadlines = read().toMutableMap()
        missionIds.forEach(deadlines::remove)
        check(write(deadlines)) { "Unable to finalize mission retention cleanup." }
    }

    private fun read(): Map<String, Long?> {
        val encoded = prefs.getString(KEY_DEADLINES, null) ?: return emptyMap()
        return runCatching {
            val root = JSONObject(encoded)
            root.keys().asSequence().associateWith { missionId ->
                require(missionId.isNotBlank())
                if (root.isNull(missionId)) null else root.getLong(missionId).also { require(it > 0L) }
            }
        }.getOrElse {
            // Unknown clocks must never silently turn into indefinite retention. Keeping
            // the damaged value makes each startup fail closed until secure cleanup works.
            throw IllegalStateException("The secure mission retention clock store is unreadable.", it)
        }
    }

    private fun write(deadlines: Map<String, Long?>): Boolean {
        val root = JSONObject()
        deadlines.toSortedMap().forEach { (missionId, deadline) ->
            root.put(missionId, deadline ?: JSONObject.NULL)
        }
        return prefs.edit().putString(KEY_DEADLINES, root.toString()).commit()
    }

    private companion object {
        const val FILE_NAME = "patrolgrid_mission_retention"
        const val KEY_DEADLINES = "authoritative_deadlines"
    }
}
