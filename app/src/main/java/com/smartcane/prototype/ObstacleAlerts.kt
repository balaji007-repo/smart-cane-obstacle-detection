package com.smartcane.prototype

internal enum class Zone {
    FAR_LEFT, LEFT, CENTER, RIGHT, FAR_RIGHT, TOP, BOTTOM
}

internal enum class ObstacleGrid(val zones: List<Zone>) {
    COLUMNS(listOf(Zone.FAR_LEFT, Zone.LEFT, Zone.CENTER, Zone.RIGHT, Zone.FAR_RIGHT)),
    ROWS(listOf(Zone.TOP, Zone.CENTER, Zone.BOTTOM));

    fun zoneAt(x: Float, y: Float, width: Int, height: Int): Zone {
        val fraction = if (this == COLUMNS) x / width else y / height
        return zones[(fraction * zones.size).toInt().coerceIn(zones.indices)]
    }
}

internal class ObstacleAlerts {
    private val announced = mutableMapOf<Zone, Long?>()

    fun update(occupied: Set<Zone>, now: Long, speak: (Zone) -> Boolean) {
        val entries = announced.entries.iterator()
        while (entries.hasNext()) {
            val entry = entries.next()
            val clearSince = entry.value
            when {
                clearSince != null && now - clearSince >= CLEAR_MS -> entries.remove()
                entry.key in occupied -> entry.setValue(null)
                clearSince == null -> entry.setValue(now)
            }
        }

        val next = PRIORITY.firstOrNull { it in occupied && it !in announced } ?: return
        if (speak(next)) announced[next] = null
    }

    private companion object {
        const val CLEAR_MS = 800L
        val PRIORITY = listOf(Zone.CENTER, Zone.LEFT, Zone.RIGHT, Zone.FAR_LEFT, Zone.FAR_RIGHT, Zone.TOP, Zone.BOTTOM)
    }
}
