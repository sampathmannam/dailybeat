package com.dailybeat.app.ui.today

import com.dailybeat.app.data.model.Event

/**
 * The order Today lists the day's moments in: newest first.
 *
 * Today is the screen the officer just recorded something on, so the thing they added belongs at
 * the top where they are already looking. Diary keeps the opposite order on purpose — it reads as
 * the day's narrative, start to finish.
 *
 * Sorted rather than simply reversed: the DAO happens to return ascending timestamps today, but
 * this list's order is a product decision and should not silently invert if that query is ever
 * changed. Ties keep their relative order, so two moments saved in the same millisecond stay in
 * the order they were written.
 */
internal fun todayMomentsOrder(events: List<Event>): List<Event> =
    events.sortedByDescending { it.timestamp }
