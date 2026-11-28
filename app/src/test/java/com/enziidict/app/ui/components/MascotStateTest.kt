package com.enziidict.app.ui.components

import com.enziidict.app.core.model.SpeakingLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MascotStateTest {

    @Test
    fun `all MascotMood entries map to distinct valid drawable resources`() {
        val drawables = MascotMood.entries.map { it.drawableRes }
        assertEquals(5, drawables.size)
        assertEquals(5, drawables.toSet().size)
        drawables.forEach { resId ->
            assertNotEquals(0, resId)
        }
    }

    @Test
    fun `MascotLevel derives accurately from mastered word count and speaking level`() {
        assertEquals(MascotLevel.BASIC, MascotLevel.fromMasteredCount(0))
        assertEquals(MascotLevel.BASIC, MascotLevel.fromMasteredCount(9))
        assertEquals(MascotLevel.INTERMEDIATE, MascotLevel.fromMasteredCount(10))
        assertEquals(MascotLevel.INTERMEDIATE, MascotLevel.fromMasteredCount(29))
        assertEquals(MascotLevel.ADVANCED, MascotLevel.fromMasteredCount(30))
        assertEquals(MascotLevel.ADVANCED, MascotLevel.fromMasteredCount(120))

        assertEquals(MascotLevel.BASIC, MascotLevel.fromSpeakingLevel(SpeakingLevel.BEGINNER))
        assertEquals(MascotLevel.INTERMEDIATE, MascotLevel.fromSpeakingLevel(SpeakingLevel.INTERMEDIATE))
        assertEquals(MascotLevel.ADVANCED, MascotLevel.fromSpeakingLevel(SpeakingLevel.ADVANCED))
    }
}
