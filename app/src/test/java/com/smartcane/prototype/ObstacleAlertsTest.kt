package com.smartcane.prototype

import org.junit.Assert.assertEquals
import org.junit.Test

class ObstacleAlertsTest {
    @Test
    fun usesFiveEqualColumnsWithBoundariesAssignedToTheRight() {
        val positions = listOf(0f, 19.9f, 20f, 40f, 60f, 80f, 100f)
        assertEquals(
            listOf(Zone.FAR_LEFT, Zone.FAR_LEFT, Zone.LEFT, Zone.CENTER, Zone.RIGHT, Zone.FAR_RIGHT, Zone.FAR_RIGHT),
            positions.map { ObstacleGrid.COLUMNS.zoneAt(it, 5f, 100, 50) },
        )
    }

    @Test
    fun usesThreeEqualRowsWithBoundariesAssignedToTheLowerRow() {
        val positions = listOf(0f, 99.9f, 100f, 199.9f, 200f, 300f)
        assertEquals(
            listOf(Zone.TOP, Zone.TOP, Zone.CENTER, Zone.CENTER, Zone.BOTTOM, Zone.BOTTOM),
            positions.map { ObstacleGrid.ROWS.zoneAt(48f, it, 50, 300) },
        )
    }

    @Test
    fun persistentZoneSpeaksImmediatelyAndRearmsAfterClearing() {
        val alerts = ObstacleAlerts()
        val spoken = mutableListOf<Zone>()
        val center = setOf(Zone.CENTER)

        alerts.update(center, 0, spoken::add)
        assertEquals(listOf(Zone.CENTER), spoken)

        alerts.update(center, 3000, spoken::add)
        alerts.update(emptySet(), 3100, spoken::add)
        alerts.update(center, 3400, spoken::add)
        assertEquals(listOf(Zone.CENTER), spoken)

        alerts.update(emptySet(), 4000, spoken::add)
        alerts.update(center, 4800, spoken::add)
        assertEquals(listOf(Zone.CENTER, Zone.CENTER), spoken)
    }

    @Test
    fun nextOccupiedZoneSpeaksAsSoonAsSpeechIsAvailable() {
        val alerts = ObstacleAlerts()
        val spoken = mutableListOf<Zone>()
        val occupied = setOf(Zone.TOP, Zone.CENTER, Zone.BOTTOM)

        alerts.update(occupied, 0, spoken::add)
        alerts.update(occupied, 100) { false }
        assertEquals(listOf(Zone.CENTER), spoken)

        alerts.update(occupied - Zone.TOP, 700, spoken::add)
        assertEquals(listOf(Zone.CENTER, Zone.BOTTOM), spoken)
    }
}
