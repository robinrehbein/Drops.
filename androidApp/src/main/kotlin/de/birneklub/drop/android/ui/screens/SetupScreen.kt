package de.birneklub.drop.android.ui.screens

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import de.birneklub.drop.android.ui.rememberLargeFont
import de.birneklub.drop.android.ui.Space
import de.birneklub.drop.android.ui.ConfirmDialog
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import de.birneklub.drop.core.format.Format
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import de.birneklub.drop.android.ui.ButtonKind
import de.birneklub.drop.android.ui.Drops
import de.birneklub.drop.android.ui.DropsCard
import de.birneklub.drop.android.ui.DropsIcons
import de.birneklub.drop.android.ui.DropsType
import de.birneklub.drop.android.ui.DropsViewModel
import de.birneklub.drop.android.ui.HeroCard
import de.birneklub.drop.android.ui.Icon
import de.birneklub.drop.android.ui.MonoFamily
import de.birneklub.drop.android.ui.PillButton
import de.birneklub.drop.android.ui.Routes
import de.birneklub.drop.android.ui.SectionHeader
import de.birneklub.drop.android.ui.TaskRow
import de.birneklub.drop.android.ui.TextAction
import de.birneklub.drop.android.ui.rememberNotificationPermission
import de.birneklub.drop.core.domain.TaskState
import de.birneklub.drop.core.model.EquipmentKind

@Composable
fun SetupScreen(vm: DropsViewModel, nav: NavController) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val plan = lib.carePlan(vm.now())
    val overdue = plan.count { it.state == TaskState.OVERDUE }
    val c = Drops.colors
    val machine = lib.equipment.firstOrNull { it.kind == EquipmentKind.MACHINE }
    val grinder = lib.equipment.firstOrNull { it.kind == EquipmentKind.GRINDER }
    val uri = LocalUriHandler.current
    val (notificationsAllowed, askNotifications) = rememberNotificationPermission()

    ScreenColumn {
        ScreenTitle("Setup", if (overdue > 0) "$overdue überfällig" else "Alles im grünen Bereich", if (overdue > 0) c.bad else c.ok)

        // Machine and grinder side by side, stacked with large text; each card opens its details.
        val large = rememberLargeFont()
        val cards = listOfNotNull(
            machine?.let { m -> Triple(m, DropsIcons.Machine, Format.integer(m.shotCount) to "Shots gesamt") },
            grinder?.let { g -> Triple(g, DropsIcons.Grinder, Format.kilograms(g.groundKg) to "gemahlen") },
        )
        if (cards.isNotEmpty()) {
            val card: @Composable (Modifier, Triple<de.birneklub.drop.core.model.Equipment, androidx.compose.ui.graphics.vector.ImageVector, Pair<String, String>>) -> Unit = { mod, (e, icon, count) ->
                HeroCard(mod.clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClickLabel = "Details öffnen") { nav.navigate(Routes.equipment(e.kind)) }) {
                    Icon(icon, null, tint = c.heroAccent, size = 28.dp)
                    Column {
                        Text(e.name, style = DropsType.bodyStrong, color = c.heroInk)
                        if (e.details.isNotBlank()) Text(e.details, style = DropsType.caption, color = c.heroMuted)
                    }
                    Column {
                        Text(count.first, style = DropsType.number.copy(fontSize = 20.sp), color = c.heroInk)
                        Text(count.second, style = DropsType.caption, color = c.heroMuted)
                    }
                }
            }
            if (large || cards.size == 1) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.m)) { cards.forEach { card(Modifier.fillMaxWidth(), it) } }
            } else {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    cards.forEach { card(Modifier.weight(1f).fillMaxHeight(), it) }
                }
            }
        }

        if (machine == null || grinder == null) {
            val setupButtons = listOfNotNull(
                machine?.let { null } ?: ("Maschine einrichten" to EquipmentKind.MACHINE),
                grinder?.let { null } ?: ("Mühle einrichten" to EquipmentKind.GRINDER),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                setupButtons.forEach { (label, kind) -> PillButton(label, { nav.navigate(Routes.equipment(kind)) }, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost, height = Space.touch) }
            }
        } else {
            Text("Tippe auf ein Gerät für Details, Zähler und Pflege.", style = DropsType.caption, color = c.muted)
        }

        if (machine?.waterHardness != null) {
            DropsCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = Space.l, vertical = Space.m)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    Icon(DropsIcons.Drop, null, tint = c.ice, size = 20.dp)
                    Text(
                        "Wasserhärte: Leitung ${Format.hardness(machine.waterHardness)}" + (machine.filteredHardness?.let { " → nach Filter ${Format.hardness(it)}" } ?: ""),
                        style = DropsType.body.copy(fontSize = 14.sp), color = c.ink, modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (!notificationsAllowed) {
            DropsCard(Modifier.fillMaxWidth(), onClick = { askNotifications() }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(DropsIcons.Bulb, null, tint = c.accent, size = 22.dp)
                    Column(Modifier.weight(1f)) {
                        Text("Erinnerungen einschalten", style = DropsType.bodyStrong, color = c.ink)
                        Text("Wenn Pflege fällig ist oder eine Tüte fast leer ist", style = DropsType.small, color = c.muted)
                    }
                    Icon(DropsIcons.Chevron, null, tint = c.muted, size = 20.dp)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader("Pflegeplan", trailing = "nach Dringlichkeit")
            if (plan.isEmpty()) {
                DropsCard(Modifier.fillMaxWidth()) {
                    Text("Noch keine Pflegeaufgaben.", style = DropsType.bodyStrong, color = c.ink)
                    Text(
                        if (machine == null && grinder == null) "Richte Maschine und Mühle ein, dann legt Drops. einen passenden Pflegeplan an."
                        else "Tippe auf ein Gerät und leg unter „Pflege“ eine Aufgabe an.",
                        style = DropsType.small, color = c.muted, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            plan.forEach { s ->
                DropsCard(Modifier.fillMaxWidth(), onClick = { nav.navigate(Routes.task(s.task.equipmentId, s.task.id)) }, padding = PaddingValues(0.dp)) {
                    TaskRow(s, compact = false, onBuy = { uri.openUri(vm.open(vm.supplyLink(it), reorder = false)) }, buySponsored = vm.supplyLinksSponsored) { vm.completeTask(s) }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader("Konto & Sync")
            DropsCard(Modifier.fillMaxWidth(), onClick = { nav.navigate(Routes.ACCOUNT) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(DropsIcons.Cloud, null, tint = if (account.session != null) c.ok else c.muted, size = 22.dp)
                    Column(Modifier.weight(1f)) {
                        Text(account.session?.email ?: "Nur auf diesem Gerät", style = DropsType.bodyStrong, color = c.ink)
                        Text(
                            if (account.session != null) account.lastSyncMessage ?: "Angemeldet" else "Optional: Konto für Backup und mehrere Geräte",
                            style = DropsType.small, color = c.muted,
                        )
                    }
                    Icon(DropsIcons.Chevron, null, tint = c.muted, size = 20.dp)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::exportBackup) }
            val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importBackup) }
            val bcImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importBeanconqueror) }
            var confirmImport by rememberSaveable { mutableStateOf<String?>(null) }
            confirmImport?.let { which ->
                ConfirmDialog(
                    title = if (which == "bc") "Von Beanconqueror übernehmen?" else "Backup einspielen?",
                    text = if (which == "bc") "Bohnen und Espresso-Shots aus dem Export kommen zu deinen Daten dazu. Nichts wird gelöscht."
                    else "Einträge aus der Datei werden übernommen, wenn sie neuer sind als die auf dem Gerät. Neuere Einträge auf dem Gerät bleiben, nichts wird gelöscht.",
                    confirm = "Datei wählen",
                    destructive = false,
                    onConfirm = {
                        if (which == "bc") bcImporter.launch(arrayOf("application/zip", "application/json", "application/octet-stream"))
                        else importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    },
                    onDismiss = { confirmImport = null },
                )
            }
            SectionHeader("Daten sichern")
            DropsCard(Modifier.fillMaxWidth()) {
                Text("Android sichert Drops. automatisch mit deinem Geräte-Backup.", style = DropsType.bodyStrong, color = c.ink)
                Text(
                    "Zusätzlich kannst du alles als Datei exportieren und wieder einspielen, auch auf einem neuen Gerät. Beim Einspielen bleiben neuere Einträge erhalten.",
                    style = DropsType.small, color = c.muted, modifier = Modifier.padding(top = 6.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Exportieren", { exporter.launch("drops-backup-${vm.now().toString().take(10)}.json") }, Modifier.weight(1f), kind = ButtonKind.Ghost, height = 44.dp)
                PillButton("Einspielen", { confirmImport = "backup" }, Modifier.weight(1f), kind = ButtonKind.Ghost, height = 44.dp)
            }
            TextAction("Von Beanconqueror übernehmen", { confirmImport = "bc" })
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader("Beta")
            FoundingCard(vm)
            StatsConsentCard(vm)
        }

        val samples = de.birneklub.drop.data.SampleData.PREFIX
        if (lib.beans.any { it.id.startsWith(samples) } || lib.equipment.any { it.id.startsWith(samples) }) {
            var confirmSamples by rememberSaveable { mutableStateOf(false) }
            PillButton("Beispieldaten entfernen", { confirmSamples = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost)
            if (confirmSamples) {
                ConfirmDialog(
                    title = "Beispieldaten entfernen?",
                    text = "Die Beispielbohnen mit Rezepten und Shots, die Beispielmaschine und -mühle mit Zählern und Pflegeplan werden gelöscht. Auch Shots, die du mit Beispielbohnen gespeichert hast. Deine eigenen Bohnen und Geräte bleiben.",
                    confirm = "Entfernen",
                    onConfirm = { vm.removeSampleData() },
                    onDismiss = { confirmSamples = false },
                )
            }
        }
        Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            TextAction("Impressum", { uri.openUri("https://robinrehbein.de/imprint") }, c.muted)
            TextAction("Datenschutz", { uri.openUri("https://drops.robinrehbein.de/datenschutz") }, c.muted)
        }
        Text("Version ${de.birneklub.drop.android.BuildConfig.VERSION_NAME}", style = DropsType.caption.copy(fontFamily = MonoFamily), color = c.muted, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}
