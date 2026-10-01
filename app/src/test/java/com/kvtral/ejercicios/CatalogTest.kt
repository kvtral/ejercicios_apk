package com.kvtral.ejercicios

import com.kvtral.ejercicios.data.Equipment
import com.kvtral.ejercicios.data.parseCatalog
import com.kvtral.ejercicios.data.requiredEquipment
import com.kvtral.ejercicios.ui.anim.Body
import com.kvtral.ejercicios.ui.anim.animationBounds
import com.kvtral.ejercicios.ui.anim.buildSkeleton
import com.kvtral.ejercicios.ui.anim.poseAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class CatalogTest {

    private val catalog = parseCatalog(
        checkNotNull(javaClass.classLoader!!.getResource("exercises.json")).readText(),
    )

    @Test
    fun everyEquipmentHasExercisesAndRoutines() {
        val byId = catalog.exercises.associateBy { it.id }
        assertEquals("ids duplicados", catalog.exercises.size, byId.size)
        for (eq in Equipment.entries) {
            assertTrue("sin ejercicios de $eq", catalog.exercises.count { it.equipment == eq } >= 5)
            assertTrue("sin rutina de $eq", catalog.routines.any { eq in it.requiredEquipment(byId) })
        }
        catalog.routines.forEach { r -> r.items.forEach { assertTrue(it.exerciseId in byId) } }
    }

    @Test
    fun animationsKeepBoneLengthsAndStayOnGround() {
        for (ex in catalog.exercises) {
            val spec = ex.animation
            assertTrue(ex.id, spec.frames.isNotEmpty() && spec.periodMs > 0)
            for (t in 0L until spec.periodMs step 50L) {
                val s = buildSkeleton(poseAt(spec, t), spec.view == "front", spec.anchor)
                assertEquals(ex.id, Body.TORSO, hypot(s.shoulder.x - s.hip.x, s.shoulder.y - s.hip.y), 0.01f)
                assertEquals(ex.id, Body.SHIN, hypot(s.footF.x - s.kneeF.x, s.footF.y - s.kneeF.y), 0.01f)
                // Ninguna articulación atraviesa el suelo (y = 0).
                assertTrue(ex.id, s.points.all { it.y <= 0.001f })
            }
            val b = animationBounds(spec)
            assertTrue(ex.id, b.maxX > b.minX && b.maxY > b.minY)
        }
    }
}
