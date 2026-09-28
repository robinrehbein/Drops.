package de.birneklub.drop.android

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import de.birneklub.drop.android.ui.DropsTheme
import de.birneklub.drop.android.ui.DropsViewModel
import de.birneklub.drop.android.ui.ScreenBox
import de.birneklub.drop.android.ui.screens.AccountScreen
import de.birneklub.drop.android.ui.screens.AddBeanScreen
import de.birneklub.drop.android.ui.screens.BeanDetailScreen
import de.birneklub.drop.android.ui.screens.BeansScreen
import de.birneklub.drop.android.ui.screens.EquipmentScreen
import de.birneklub.drop.android.ui.screens.MapMode
import de.birneklub.drop.android.ui.screens.MapScreen
import de.birneklub.drop.android.ui.screens.OnboardingScreen
import de.birneklub.drop.android.ui.screens.RecipeScreen
import de.birneklub.drop.android.ui.screens.ShotEditScreen
import de.birneklub.drop.android.ui.screens.ShotsScreen
import de.birneklub.drop.android.ui.screens.RoasterCardScreen
import de.birneklub.drop.android.ui.screens.SetupScreen
import de.birneklub.drop.android.ui.screens.ShotScreen
import de.birneklub.drop.android.ui.screens.TaskScreen
import de.birneklub.drop.android.ui.screens.TodayScreen
import de.birneklub.drop.core.model.EquipmentKind
import de.birneklub.drop.core.model.Process
import de.birneklub.drop.core.roaster.RoasterCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

/**
 * Renders every screen so the CI shows it: with sample data, without data, at
 * 200 % font size and in dark mode, on a 360 dp wide phone. The images are
 * uploaded as a CI artifact (`./gradlew :androidApp:recordRoborazziDebug`).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp-xxhdpi")
class ScreenshotTest(private val variant: Variant) {

    enum class Variant(val data: Boolean, val fontScale: Float = 1f, val dark: Boolean = false) {
        DATA(true), EMPTY(false), LARGE_FONT(true, fontScale = 2f), DARK(true, dark = true),
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants() = Variant.entries.map { arrayOf<Any>(it) }

        val fixedNow: Instant = Instant.parse("2026-09-25T07:30:00Z")

        private val card = RoasterCard(
            roaster = "Hafenrösterei", coffee = "Guji Hambela", country = "Äthiopien", region = "Guji", process = Process.WASHED,
            notes = listOf("Jasmin", "Pfirsich"), doseGrams = 18.0, yieldGrams = 40.0, timeMinSec = 26, timeMaxSec = 30, temperatureC = 93,
            hint = "5 s Vorbrühen",
        )
    }

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shoot(name: String, content: @Composable (DropsViewModel) -> Unit) {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        val app = ApplicationProvider.getApplicationContext<TestApp>()
        val container = AppContainer(app, object : Clock { override fun now() = fixedNow }, Dispatchers.Unconfined)
        if (variant.data) runBlocking { container.repository.seedIfEmpty(); container.repository.markSetupDone() }
        val vm = DropsViewModel(container)
        compose.setContent {
            // Keeps the library subscribed even on screens that do not read it.
            vm.library.collectAsState()
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, variant.fontScale)) {
                DropsTheme(dark = variant.dark) { ScreenBox { content(vm) } }
            }
        }
        compose.waitUntil(15_000) { vm.library.value.loaded }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_${variant.name.lowercase()}.png")
    }

    @Test fun onboarding() = shoot("01_onboarding") { OnboardingScreen(it, rememberNavController()) }
    @Test fun setup() = shoot("02_setup") { SetupScreen(it, rememberNavController()) }
    @Test fun equipment() = shoot("02b_equipment") { EquipmentScreen(it, rememberNavController(), EquipmentKind.GRINDER) }
    @Test fun task() = shoot("02c_task") { TaskScreen(it, rememberNavController(), "sample-machine", "sample-t-backflush") }
    @Test fun today() = shoot("03_today") { TodayScreen(it, rememberNavController()) }
    @Test fun beans() = shoot("04_beans") { BeansScreen(it, rememberNavController()) }
    @Test fun beanDetail() = shoot("05_bean_detail") { BeanDetailScreen(it, rememberNavController(), "sample-guji") }
    @Test fun addBean() = shoot("06_add_bean") { AddBeanScreen(it, rememberNavController()) }
    @Test fun editBean() = shoot("06b_edit_bean") { AddBeanScreen(it, rememberNavController(), "sample-guji") }
    @Test fun recipe() = shoot("06c_recipe") { RecipeScreen(it, rememberNavController(), "sample-guji", "sample-r-guji", null) }
    @Test fun shots() = shoot("07b_shots") { ShotsScreen(it, rememberNavController(), "sample-guji") }
    @Test fun editShot() = shoot("07c_edit_shot") { ShotEditScreen(it, rememberNavController(), "sample-s8") }
    @Test fun shot() = shoot("07_shot") { ShotScreen(it, rememberNavController(), "sample-guji") }
    @Test fun map() = shoot("08_map") { MapScreen(it) }
    @Test fun discover() = shoot("09_discover") { MapScreen(it, MapMode.DISCOVER) }
    @Test fun account() = shoot("10_account") { AccountScreen(it, rememberNavController()) }
    @Test fun roasterCard() = shoot("11_roaster_card") { RoasterCardScreen(it, rememberNavController(), card.encode()) }
}
