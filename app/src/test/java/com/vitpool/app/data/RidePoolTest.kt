package com.vitpool.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RidePoolTest {
    @Test
    fun memberDisplayNames_usesNamesInPoolOrder() {
        val pool = RidePool(
            id = "pool-1",
            status = "pending_confirmation",
            memberIds = listOf("user-2", "user-1"),
            confirmedMemberIds = listOf("user-1"),
            pendingMemberIds = listOf("user-2"),
            departureMillis = 0L,
        )
        val members = listOf(
            RiderSummary(
                uid = "user-1",
                name = "Aarav",
                branch = "CS",
                division = "A",
                campus = CampusOption.Bibwewadi,
                stayHomeLabel = "Katraj",
                notes = "",
            ),
            RiderSummary(
                uid = "user-2",
                name = "Diya",
                branch = "IT",
                division = "B",
                campus = CampusOption.Bibwewadi,
                stayHomeLabel = "Kondhwa",
                notes = "",
            ),
        )

        assertEquals(listOf("Diya", "Aarav"), pool.memberDisplayNames(members))
    }

    @Test
    fun memberDisplayNames_fallsBackToUidWhenNameIsMissing() {
        val pool = RidePool(
            id = "pool-1",
            status = "pending_confirmation",
            memberIds = listOf("user-1", "user-2"),
            confirmedMemberIds = listOf("user-1"),
            pendingMemberIds = listOf("user-2"),
            departureMillis = 0L,
        )
        val members = listOf(
            RiderSummary(
                uid = "user-1",
                name = "   ",
                branch = "CS",
                division = "A",
                campus = CampusOption.Bibwewadi,
                stayHomeLabel = "Katraj",
                notes = "",
            ),
        )

        assertEquals(listOf("user-1", "user-2"), pool.memberDisplayNames(members))
    }
}
