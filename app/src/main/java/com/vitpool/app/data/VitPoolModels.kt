package com.vitpool.app.data

enum class TripDirection(val raw: String, val label: String) {
    CollegeToStay("college_to_stay", "College to stay"),
    StayToCollege("stay_to_college", "Stay to college");

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: CollegeToStay
    }
}

enum class GenderOption(val raw: String, val label: String) {
    Male("male", "Male"),
    Female("female", "Female"),
    Any("any", "Any");

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: Any
    }
}

enum class CampusOption(val raw: String, val label: String) {
    Bibwewadi("bibwewadi", "Bibwewadi"),
    Kondhwa("kondhwa", "Kondhwa");

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: Bibwewadi
    }
}

data class ProfileDraft(
    val name: String = "",
    val branch: String = "",
    val division: String = "",
    val campus: CampusOption = CampusOption.Bibwewadi,
    val batch: Int = 1,
    val year: Int = 1,
    val gender: GenderOption = GenderOption.Male,
    val genderPreference: GenderOption = GenderOption.Any,
    val stayHomeLabel: String = "",
    val plusCode: String = "",
    val latitude: Double = 18.4634,
    val longitude: Double = 73.8682,
    val notes: String = "",
)

data class UserProfile(
    val uid: String,
    val name: String,
    val email: String,
    val branch: String,
    val division: String,
    val campus: CampusOption,
    val batch: Int,
    val year: Int,
    val gender: GenderOption,
    val genderPreference: GenderOption,
    val stayHomeLabel: String,
    val plusCode: String,
    val latitude: Double,
    val longitude: Double,
    val notes: String,
)

internal fun UserProfile.toProfileDraft(): ProfileDraft = ProfileDraft(
    name = name,
    branch = branch,
    division = division,
    campus = campus,
    batch = batch.coerceIn(1, 3),
    year = year.coerceIn(1, 5),
    gender = gender,
    genderPreference = genderPreference,
    stayHomeLabel = stayHomeLabel,
    plusCode = plusCode,
    latitude = latitude,
    longitude = longitude,
    notes = notes,
)

data class RiderSummary(
    val uid: String,
    val name: String,
    val branch: String,
    val division: String,
    val campus: CampusOption,
    val stayHomeLabel: String,
    val notes: String,
)

data class RideRequest(
    val id: String,
    val tripDirection: TripDirection,
    val status: String,
    val predictedDepartureMillis: Long,
)

data class MatchCandidate(
    val user: RiderSummary,
    val departureGapMinutes: Int,
    val proximityLabel: String,
)

data class MatchSuggestion(
    val candidateCount: Int = 0,
    val suggestedPoolUserIds: List<String> = emptyList(),
    val candidates: List<MatchCandidate> = emptyList(),
    val summary: String = "No active ride request yet",
)

data class RidePool(
    val id: String,
    val status: String,
    val memberIds: List<String>,
    val confirmedMemberIds: List<String>,
    val pendingMemberIds: List<String>,
    val departureMillis: Long,
)

internal fun RidePool.memberDisplayNames(members: List<RiderSummary>): List<String> {
    val namesByUid = members.associate { it.uid to it.name.trim() }
    return memberIds.map { uid ->
        namesByUid[uid].takeUnless { it.isNullOrBlank() } ?: uid
    }
}

data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val createdMillis: Long,
)
