package org.aionlink.projection

import org.junit.Assert.assertEquals
import org.junit.Test

class CarAppSelectionTest {
    @Test fun keepsTheOrderChosenAndSurvivesStorage() {
        var selection = CarAppSelection.toggle(emptyList(), "maps/.Main")
        selection = CarAppSelection.toggle(selection, "player/.Main")
        selection = CarAppSelection.toggle(selection, "gallery/.Main")
        assertEquals(listOf("maps/.Main", "player/.Main", "gallery/.Main"), selection)
        assertEquals(selection, CarAppSelection.decode(CarAppSelection.encode(selection)))
    }

    @Test fun togglingAgainRemovesAndEmptyStorageIsEmpty() {
        assertEquals(listOf("b"), CarAppSelection.toggle(listOf("a", "b"), "a"))
        assertEquals(emptyList<String>(), CarAppSelection.decode(""))
        assertEquals(listOf("a"), CarAppSelection.decode("a\n\na\n"))
    }
}
