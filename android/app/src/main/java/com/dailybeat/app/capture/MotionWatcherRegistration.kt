package com.dailybeat.app.capture

/**
 * Owns asynchronous motion registration without allowing an old success to re-arm capture.
 * Tokens must identify distinct subscriptions: removing a stale token must not remove a new one.
 */
internal class MotionWatcherRegistration<Token : Any>(
    private val isAllowed: () -> Boolean,
    private val setArmed: (Boolean) -> Unit,
    private val createToken: () -> Token,
    private val register: (Token, () -> Unit, (Exception) -> Unit) -> Unit,
    private val remove: (Token) -> Unit,
    private val cancel: (Token) -> Unit,
    private val onFailure: (Exception) -> Unit,
) {
    private var generation = 0L
    private var active: Token? = null

    @Synchronized
    fun arm() {
        disarm()
        if (!allowedNow()) return
        val epoch = generation
        try {
            val token = createToken()
            active = token
            register(token, { registered(epoch, token) }, { failed(epoch, token, it) })
        } catch (error: Exception) {
            disarm()
            onFailure(error)
        }
    }

    @Synchronized
    fun disarm() {
        generation++
        val previous = active
        active = null
        setArmed(false)
        if (previous != null) release(previous)
    }

    @Synchronized
    private fun registered(epoch: Long, token: Token) {
        if (epoch != generation || active !== token) {
            release(token)
        } else if (!allowedNow()) {
            // Permission/consent may have changed while Play services was registering.
            disarm()
        } else {
            setArmed(true)
        }
    }

    @Synchronized
    private fun failed(epoch: Long, token: Token, error: Exception) {
        if (epoch != generation || active !== token) {
            release(token)
            return
        }
        disarm()
        onFailure(error)
    }

    private fun release(token: Token) {
        runCatching { remove(token) }
        // Invalidating the capability also blocks delivery if remote removal fails or is late.
        runCatching { cancel(token) }
    }

    private fun allowedNow(): Boolean = runCatching { isAllowed() }.getOrDefault(false)
}
