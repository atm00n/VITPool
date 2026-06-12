package com.vitpool.app.data

import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_KM = 6371.0
private const val TIME_WINDOW_MINUTES = 15
private const val MAX_DISTANCE_KM = 1.0

data class PendingRide(
    val uid: String,
    val tripDirection: TripDirection,
    val predictedDepartureMillis: Long,
    val user: UserProfile,
)

fun findCompatibleCandidates(base: PendingRide, pending: List<PendingRide>): List<MatchCandidate> {
    return pending
        .asSequence()
        .filter { it.uid != base.uid }
        .filter { it.tripDirection == base.tripDirection }
        .filter { it.user.campus == base.user.campus }
        .filter { genderCompatible(base.user, it.user) }
        .map { candidate ->
            val gap = kotlin.math.abs((candidate.predictedDepartureMillis - base.predictedDepartureMillis) / 60_000).toInt()
            val distanceKm = haversineKm(
                base.user.latitude,
                base.user.longitude,
                candidate.user.latitude,
                candidate.user.longitude,
            )
            candidate to (gap to distanceKm)
        }
        .filter { (_, pair) -> pair.first <= TIME_WINDOW_MINUTES && pair.second <= MAX_DISTANCE_KM }
        .sortedWith(compareBy({ it.second.second * 1.7 + it.second.first.toDouble() / TIME_WINDOW_MINUTES }, { it.first.uid }))
        .map { (candidate, pair) ->
            MatchCandidate(
                user = candidate.user.toRiderSummary(),
                departureGapMinutes = pair.first,
                proximityLabel = "Within ${roundUpMetres(pair.second)} metres",
            )
        }
        .toList()
}

fun buildSuggestedPool(baseUid: String, candidates: List<MatchCandidate>): List<String> {
    if (candidates.isEmpty()) return emptyList()
    return listOf(baseUid) + candidates.take(3).map { it.user.uid }
}

fun compatibleGroup(base: PendingRide, selected: List<PendingRide>): Boolean {
    if (selected.size !in 2..4) return false
    if (selected.none { it.uid == base.uid }) return false
    for (request in selected) {
        if (request.tripDirection != base.tripDirection) return false
        if (request.user.campus != base.user.campus) return false
        if (kotlin.math.abs(request.predictedDepartureMillis - base.predictedDepartureMillis) > TIME_WINDOW_MINUTES * 60_000) return false
    }
    for (i in selected.indices) {
        for (j in i + 1 until selected.size) {
            if (!genderCompatible(selected[i].user, selected[j].user)) return false
            val distance = haversineKm(
                selected[i].user.latitude,
                selected[i].user.longitude,
                selected[j].user.latitude,
                selected[j].user.longitude,
            )
            if (distance > MAX_DISTANCE_KM * 1.35) return false
        }
    }
    return true
}

fun UserProfile.toRiderSummary(): RiderSummary = RiderSummary(
    uid = uid,
    name = name,
    branch = branch,
    division = division,
    campus = campus,
    stayHomeLabel = stayHomeLabel,
    notes = notes,
)

private fun genderCompatible(first: UserProfile, second: UserProfile): Boolean {
    val firstAccepts = first.genderPreference == GenderOption.Any || first.genderPreference == second.gender
    val secondAccepts = second.genderPreference == GenderOption.Any || second.genderPreference == first.gender
    return firstAccepts && secondAccepts
}

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)
    val a = sin(dLat / 2).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
}

private fun roundUpMetres(distanceKm: Double): Int {
    val metres = maxOf(50, ceil(distanceKm * 1000).toInt())
    val step = when {
        metres <= 500 -> 50
        metres <= 1000 -> 100
        else -> 250
    }
    return ceil(metres.toDouble() / step).toInt() * step
}
