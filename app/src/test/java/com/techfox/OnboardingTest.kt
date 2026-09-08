package com.techfox

import android.content.Context
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.pill
import androidx.graphics.shapes.star
import androidx.test.core.app.ApplicationProvider
import com.techfox.data.PreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnboardingTest {

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        prefs = PreferencesManager(context)
        prefs.setOnboardingCompleted(false)
        prefs.setApiKey("")
    }

    @Test
    fun testOnboardingState_defaultsToFalse() {
        assertFalse(prefs.isOnboardingCompleted())
    }

    @Test
    fun testOnboardingState_updatesWhenCompleted() {
        prefs.setOnboardingCompleted(true)
        assertTrue(prefs.isOnboardingCompleted())
    }

    @Test
    fun testOnboardingStrings_arePresent() {
        assertEquals("Welcome to Goo-goo ga-ga!", context.getString(R.string.onboarding_welcome_title))
        assertEquals("Are you ready to learn goo-goo ga-ga?", context.getString(R.string.onboarding_welcome_desc))
        assertEquals("All done!", context.getString(R.string.btn_all_done))
        assertTrue(context.getString(R.string.onboarding_step_1).contains("aistudio.google.com"))
        assertEquals("How to get your API Key (Free):", context.getString(R.string.onboarding_guide_title))
        assertEquals("Welcome & Setup Guide", context.getString(R.string.label_onboarding_guide))
    }

    @Test
    fun testMorphingShapes_toComposePath() {
        val circle = androidx.graphics.shapes.RoundedPolygon.circle(numVertices = 8)
        val star = androidx.graphics.shapes.RoundedPolygon.star(
            numVerticesPerRadius = 8,
            innerRadius = 0.7f,
            rounding = androidx.graphics.shapes.CornerRounding(0.2f)
        )
        val pill = androidx.graphics.shapes.RoundedPolygon.pill(width = 1.5f, height = 1f)
        val morph1 = androidx.graphics.shapes.Morph(circle, star)
        val morph2 = androidx.graphics.shapes.Morph(star, pill)

        val composePath = androidx.compose.ui.graphics.Path()
        var isFirst = true
        morph1.forEachCubic(0.5f) { cubic ->
            if (isFirst) {
                composePath.moveTo(cubic.anchor0X, cubic.anchor0Y)
                isFirst = false
            }
            composePath.cubicTo(
                cubic.control0X, cubic.control0Y,
                cubic.control1X, cubic.control1Y,
                cubic.anchor1X, cubic.anchor1Y
            )
        }
        composePath.close()

        assertTrue(composePath.getBounds().width > 0f)
        assertTrue(composePath.getBounds().height > 0f)
        assertTrue(morph2.calculateBounds().isNotEmpty())
    }

    @Test
    fun testOnboardingActivity_createIntent() {
        val defaultIntent = com.techfox.ui.onboarding.OnboardingActivity.createIntent(context)
        assertFalse(defaultIntent.getBooleanExtra(com.techfox.ui.onboarding.OnboardingActivity.EXTRA_FROM_SETTINGS, false))

        val settingsIntent = com.techfox.ui.onboarding.OnboardingActivity.createIntent(context, fromSettings = true)
        assertTrue(settingsIntent.getBooleanExtra(com.techfox.ui.onboarding.OnboardingActivity.EXTRA_FROM_SETTINGS, false))
    }
}
