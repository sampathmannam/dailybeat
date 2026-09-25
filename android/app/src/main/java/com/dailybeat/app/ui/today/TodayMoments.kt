package com.dailybeat.app.ui.today

import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.Place
import com.dailybeat.app.domain.momentsForDisplay

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

/**
 * Joins generated moments to current local visit/place labels without rewriting saved history.
 *
 * Early builds persisted the visit's resolved address in [LocationVisit], then copied the same
 * record into the moments stream as the bare word "Transit". The timestamp and coordinates were
 * retained, so Today can safely join the two local records and show the place immediately. The
 * same join is needed for current travel and stay events when a visit is named after capture.
 * User-authored notes and non-template prose are never rewritten.
 */
internal fun todayMomentsForDisplay(
    events: List<Event>,
    visits: List<LocationVisit>,
    places: List<Place> = emptyList(),
): List<Event> = todayMomentsOrder(momentsForDisplay(events, visits, places))
