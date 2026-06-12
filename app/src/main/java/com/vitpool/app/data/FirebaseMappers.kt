package com.vitpool.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

internal fun DocumentSnapshot.toUserProfile(): UserProfile? {
    val uid = id
    val email = getString("email") ?: return null
    return UserProfile(
        uid = uid,
        name = getString("name").orEmpty(),
        email = email,
        branch = getString("branch").orEmpty(),
        division = getString("division").orEmpty(),
        campus = CampusOption.from(getString("campus")),
        batch = getLong("batch")?.toInt() ?: 1,
        year = getLong("year")?.toInt() ?: 1,
        gender = GenderOption.from(getString("gender")),
        genderPreference = GenderOption.from(getString("genderPreference")),
        stayHomeLabel = getString("stayHomeLabel").orEmpty(),
        plusCode = getString("plusCode").orEmpty(),
        latitude = getDouble("latitude") ?: 0.0,
        longitude = getDouble("longitude") ?: 0.0,
        notes = getString("notes").orEmpty(),
    )
}

@Suppress("UNCHECKED_CAST")
internal fun DocumentSnapshot.toMatchSuggestion(): MatchSuggestion {
    val candidates = (get("candidates") as? List<Map<String, Any?>>).orEmpty().mapNotNull { raw ->
        val userRaw = raw["user"] as? Map<String, Any?> ?: return@mapNotNull null
        MatchCandidate(
            user = RiderSummary(
                uid = userRaw["uid"] as? String ?: return@mapNotNull null,
                name = userRaw["name"] as? String ?: "Rider",
                branch = userRaw["branch"] as? String ?: "",
                division = userRaw["division"] as? String ?: "",
                campus = CampusOption.from(userRaw["campus"] as? String),
                stayHomeLabel = userRaw["stayHomeLabel"] as? String ?: "",
                notes = userRaw["notes"] as? String ?: "",
            ),
            departureGapMinutes = (raw["departureGapMinutes"] as? Number)?.toInt() ?: 0,
            proximityLabel = raw["proximityLabel"] as? String ?: "Nearby",
        )
    }
    return MatchSuggestion(
        candidateCount = getLong("candidateCount")?.toInt() ?: candidates.size,
        suggestedPoolUserIds = stringList("suggestedPoolUserIds"),
        candidates = candidates,
        summary = getString("summary") ?: "${candidates.size} compatible riders nearby",
    )
}

internal fun DocumentSnapshot.toRidePool(): RidePool? {
    if (!exists()) return null
    return RidePool(
        id = id,
        status = getString("status").orEmpty(),
        memberIds = stringList("memberIds"),
        confirmedMemberIds = stringList("confirmedMemberIds"),
        pendingMemberIds = stringList("pendingMemberIds"),
        departureMillis = timestampMillis("departureAt"),
    )
}

internal fun DocumentSnapshot.toChatMessage(): ChatMessage? {
    if (!exists()) return null
    return ChatMessage(
        id = id,
        senderId = getString("senderId").orEmpty(),
        senderName = getString("senderName") ?: "Rider",
        content = getString("content").orEmpty(),
        createdMillis = timestampMillis("createdAt"),
    )
}

@Suppress("UNCHECKED_CAST")
internal fun DocumentSnapshot.stringList(field: String): List<String> =
    (get(field) as? List<*>).orEmpty().mapNotNull { it as? String }

internal fun DocumentSnapshot.timestampMillis(field: String): Long {
    return when (val value = get(field)) {
        is Timestamp -> value.toDate().time
        is Number -> value.toLong()
        else -> 0L
    }
}

internal fun ProfileDraft.toCallablePayload(): Map<String, Any?> = mapOf(
    "name" to name.trim(),
    "branch" to branch.trim(),
    "division" to division.trim(),
    "campus" to campus.raw,
    "batch" to batch,
    "year" to year,
    "gender" to gender.raw,
    "genderPreference" to genderPreference.raw,
    "stayHomeLabel" to stayHomeLabel.trim(),
    "plusCode" to plusCode.trim(),
    "latitude" to latitude,
    "longitude" to longitude,
    "notes" to notes.trim(),
)
