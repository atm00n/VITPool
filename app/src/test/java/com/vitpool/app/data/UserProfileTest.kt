package com.vitpool.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UserProfileTest {
    @Test
    fun toProfileDraft_preservesEditableProfileFields() {
        val profile = UserProfile(
            uid = "user-1",
            name = "Aarav",
            email = "aarav@vit.edu",
            branch = "CS",
            division = "A",
            campus = CampusOption.Kondhwa,
            batch = 2,
            year = 3,
            gender = GenderOption.Male,
            genderPreference = GenderOption.Any,
            stayHomeLabel = "Katraj",
            plusCode = "7JQ7+V5",
            latitude = 18.4521,
            longitude = 73.8652,
            notes = "Evening rides",
        )

        val draft = profile.toProfileDraft()

        assertEquals("Aarav", draft.name)
        assertEquals("CS", draft.branch)
        assertEquals("A", draft.division)
        assertEquals(CampusOption.Kondhwa, draft.campus)
        assertEquals(2, draft.batch)
        assertEquals(3, draft.year)
        assertEquals(GenderOption.Male, draft.gender)
        assertEquals(GenderOption.Any, draft.genderPreference)
        assertEquals("Katraj", draft.stayHomeLabel)
        assertEquals("7JQ7+V5", draft.plusCode)
        assertEquals(18.4521, draft.latitude, 0.0)
        assertEquals(73.8652, draft.longitude, 0.0)
        assertEquals("Evening rides", draft.notes)
    }

    @Test
    fun toProfileDraft_clampsLegacyBatchAndYearValues() {
        val profile = UserProfile(
            uid = "user-1",
            name = "",
            email = "student@vit.edu",
            branch = "",
            division = "",
            campus = CampusOption.Bibwewadi,
            batch = 9,
            year = 8,
            gender = GenderOption.Female,
            genderPreference = GenderOption.Female,
            stayHomeLabel = "",
            plusCode = "",
            latitude = 0.0,
            longitude = 0.0,
            notes = "",
        )

        val draft = profile.toProfileDraft()

        assertEquals(3, draft.batch)
        assertEquals(5, draft.year)
    }
}
