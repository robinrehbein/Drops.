package de.birneklub.drop.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.clickable
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import de.birneklub.drop.android.ui.screens.RecipeScreen
import de.birneklub.drop.android.ui.screens.EquipmentReplaceScreen
import de.birneklub.drop.android.ui.screens.TaskScreen
import de.birneklub.drop.android.ui.screens.ShotEditScreen
import de.birneklub.drop.android.ui.screens.ShotsScreen
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.birneklub.drop.android.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import de.birneklub.drop.android.ui.screens.AccountScreen
import de.birneklub.drop.android.ui.screens.AddBeanScreen
import de.birneklub.drop.android.ui.screens.BeanDetailScreen
import de.birneklub.drop.android.ui.screens.BeansScreen
import de.birneklub.drop.android.ui.screens.EquipmentScreen
import de.birneklub.drop.android.ui.screens.MapScreen
import de.birneklub.drop.android.ui.screens.OnboardingScreen
import de.birneklub.drop.android.ui.screens.RoasterCardScreen
import de.birneklub.drop.core.model.EquipmentKind
import de.birneklub.drop.android.ui.screens.SetupScreen
import de.birneklub.drop.android.ui.screens.ShotScreen
import de.birneklub.drop.android.ui.screens.TodayScreen

object Routes {
    const val TODAY = "today"
    const val BEANS = "beans"
    const val MAP = "map"
    const val SETUP = "setup"
    const val BEAN = "bean/{id}"
    const val SHOT = "shot/{beanId}"
    const val ADD_BEAN = "add-bean"
    const val EDIT_BEAN = "bean/{id}/edit"
    const val SHOTS = "bean/{id}/shots"
    const val EDIT_SHOT = "shots/{shotId}/edit"
    const val RECIPE = "recipe/{beanId}/{recipeId}?copy={copy}"
    const val ACCOUNT = "account"
    const val ONBOARDING = "onboarding"
    const val EQUIPMENT = "equipment/{kind}"
    const val EQUIPMENT_REPLACE = "equipment/{kind}/replace"
    const val TASK = "task/{equipmentId}/{taskId}"
    const val ROASTER_CARD = "card/{payload}"

    fun bean(id: String) = "bean/$id"
    fun editBean(id: String) = "bean/$id/edit"
    fun shots(beanId: String) = "bean/$beanId/shots"
    fun editShot(shotId: String) = "shots/$shotId/edit"
    /** [recipeId] null = new recipe, [copyOf] = new recipe pre-filled from another one. */
    fun recipe(beanId: String, recipeId: String? = null, copyOf: String? = null) =
        "recipe/$beanId/${recipeId ?: "new"}" + (copyOf?.let { "?copy=$it" } ?: "")
    fun shot(beanId: String) = "shot/$beanId"
    fun equipment(kind: EquipmentKind) = "equipment/${kind.name}"
    fun replaceEquipment(kind: EquipmentKind) = "equipment/${kind.name}/replace"
    fun task(equipmentId: String, taskId: String? = null) = "task/$equipmentId/${taskId ?: "new"}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, "Heute", DropsIcons.Home),
    Tab(Routes.BEANS, "Bohnen", DropsIcons.Bean),
    Tab(Routes.MAP, "Karte", DropsIcons.Map),
    Tab(Routes.SETUP, "Setup", DropsIcons.Setup),
)

@Composable
fun DropsRoot(container: AppContainer, deepLink: MutableStateFlow<String?> = MutableStateFlow(null)) {
    val vm: DropsViewModel = viewModel(factory = DropsViewModel.factory(container))
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val reduced = rememberReducedMotion()
    val message by vm.messages.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        // Consumed after showing: clearing it first would restart this effect and cancel the snackbar.
        message?.let { m ->
            val result = snackbar.showSnackbar(
                m.text, actionLabel = m.action, withDismissAction = m.action != null,
                duration = if (m.action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            vm.consumeMessage()
            if (result == SnackbarResult.ActionPerformed) m.onAction?.invoke()
        }
    }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showTabs = route in tabs.map { it.route }
    val setupDone by vm.setupDone.collectAsStateWithLifecycle()
    // Decide the start screen once; later changes navigate explicitly.
    val start = remember(setupDone == null) { if (setupDone == false) Routes.ONBOARDING else Routes.TODAY }

    Scaffold(
        containerColor = Drops.colors.paper,
        snackbarHost = { SnackbarHost(snackbar) },
        // Enter and exit beyond screen bounds: the bar collapses away on detail screens.
        bottomBar = {
            AnimatedVisibility(showTabs, enter = Motion.barIn(reduced), exit = Motion.barOut(reduced)) { TabBar(route, nav) }
        },
    ) { padding ->
        if (setupDone == null) return@Scaffold
        val link by deepLink.collectAsStateWithLifecycle()
        LaunchedEffect(link, setupDone) {
            val target = link ?: return@LaunchedEffect
            if (setupDone != true) return@LaunchedEffect
            deepLink.value = null
            nav.navigate(target) { launchSingleTop = true }
        }
        NavHost(
            nav,
            startDestination = start,
            modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
            enterTransition = { patternFor(initialState.destination.route, targetState.destination.route, reduced, popping = false).first },
            exitTransition = { patternFor(initialState.destination.route, targetState.destination.route, reduced, popping = false).second },
            popEnterTransition = { patternFor(initialState.destination.route, targetState.destination.route, reduced, popping = true).first },
            popExitTransition = { patternFor(initialState.destination.route, targetState.destination.route, reduced, popping = true).second },
        ) {
            composable(Routes.TODAY) { TodayScreen(vm, nav) }
            composable(Routes.BEANS) { BeansScreen(vm, nav) }
            composable(Routes.MAP) { MapScreen(vm, nav) }
            composable(Routes.SETUP) { SetupScreen(vm, nav) }
            composable(Routes.BEAN) { BeanDetailScreen(vm, nav, it.arguments?.getString("id").orEmpty()) }
            composable(Routes.SHOT) { ShotScreen(vm, nav, it.arguments?.getString("beanId").orEmpty()) }
            composable(Routes.ADD_BEAN) { AddBeanScreen(vm, nav) }
            composable(Routes.RECIPE, arguments = listOf(navArgument("copy") { nullable = true; defaultValue = null })) {
                val args = it.arguments
                RecipeScreen(vm, nav, args?.getString("beanId").orEmpty(), args?.getString("recipeId")?.takeIf { id -> id != "new" }, args?.getString("copy"))
            }
            composable(Routes.SHOTS) { ShotsScreen(vm, nav, it.arguments?.getString("id").orEmpty()) }
            composable(Routes.EDIT_SHOT) { ShotEditScreen(vm, nav, it.arguments?.getString("shotId").orEmpty()) }
            composable(Routes.EDIT_BEAN) { AddBeanScreen(vm, nav, it.arguments?.getString("id").orEmpty()) }
            composable(Routes.ACCOUNT) { AccountScreen(vm, nav) }
            composable(Routes.ONBOARDING) { OnboardingScreen(vm, nav) }
            composable(Routes.ROASTER_CARD) { RoasterCardScreen(vm, nav, it.arguments?.getString("payload").orEmpty()) }
            composable(Routes.EQUIPMENT) {
                val kind = EquipmentKind.entries.firstOrNull { k -> k.name == it.arguments?.getString("kind") } ?: EquipmentKind.MACHINE
                EquipmentScreen(vm, nav, kind)
            }
            composable(Routes.EQUIPMENT_REPLACE) {
                val kind = EquipmentKind.entries.firstOrNull { k -> k.name == it.arguments?.getString("kind") } ?: EquipmentKind.MACHINE
                EquipmentReplaceScreen(vm, nav, kind, replacing = true)
            }
            composable(Routes.TASK) {
                TaskScreen(vm, nav, it.arguments?.getString("equipmentId").orEmpty(), it.arguments?.getString("taskId")?.takeIf { id -> id != "new" })
            }
        }
    }
}

@Composable
private fun TabBar(current: String?, nav: NavHostController) {
    val c = Drops.colors
    Column(Modifier.background(c.surface)) {
        Divider()
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 6.dp, vertical = 6.dp)) {
            tabs.forEach { tab ->
                val on = tab.route == current
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .semantics { selected = on }
                        .clickable(role = Role.Tab) {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    val tint = if (on) c.accent else c.muted
                    Icon(tab.icon, null, tint = tint)
                    Text(tab.label, style = DropsType.caption.copy(fontSize = 11.sp), color = tint)
                }
            }
        }
    }
}

@Composable
fun ScreenBox(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize().background(Drops.colors.paper)) { content() }
