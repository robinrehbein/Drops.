# Politur-Checkliste

Jeder Screen wird gegen dieselbe Liste geprüft. Ein Haken heißt: geprüft in den
Screenshots aus der CI (Artefakt `drops-screenshots`, Varianten `data`, `empty`,
`large_font` = 200 %, `dark`, jeweils 360 dp breit) und im Code.

Kriterien:

- **Zahlen**: nur über `ui/Format.kt`, deutsches Format (15,0 · 1:2,0 · 4,5/5 · 27 s · 93 °C), kein `Locale.ROOT`, keine rohen Doubles.
- **Sprache**: Deutsch, einheitliche Begriffe (siehe Glossar), kein Versprechen, das die App nicht hält.
- **Layout**: kein ungewollter Umbruch, nichts hart abgeschnitten bei 360 dp, 200 % Schrift und langen Namen; fehlende Werte als „–“ ohne „ · “-Reste.
- **Design**: Farben nur aus `Theme.kt`, Abstände aus `Spacing`, Kontrast WCAG AA hell und dunkel.
- **Barrierefreiheit**: Touch-Ziele ≥ 48 dp, Icons mit TalkBack-Beschreibung oder bewusst dekorativ.
- **Zustände**: jede Aktion gibt Feedback, Leerzustand vorhanden, Fehler am Feld mit Lösungshinweis.

## Glossar

| Begriff | Nicht |
| --- | --- |
| Drehzahl | RPM |
| Vorbrühen | Pre-Infusion |
| Aufbereitung: Gewaschen, Natural, Honey, Anaerob, Andere | Washed |
| Geschmack: Sauer, Leicht sauer, Ausgewogen, Leicht bitter, Bitter | Balance, Etwas sauer |
| Verhältnis 1:2,0 | Ratio |
| Brühtemperatur | Temp. |

## Screens

### Onboarding

Datei: `OnboardingScreen.kt` · Screenshot: `01_onboarding`

- [ ] Zahlen
- [ ] Sprache
- [ ] Layout
- [ ] Design
- [ ] Barrierefreiheit
- [ ] Zustände

### Setup (inkl. Geräte)

Datei: `SetupScreen.kt, OnboardingScreen.kt (EquipmentScreen)` · Screenshot: `02_setup, 02b_equipment`

- [x] Zahlen
- [x] Sprache
- [x] Layout
- [x] Design
- [x] Barrierefreiheit
- [x] Zustände

### Heute

Datei: `TodayScreen.kt` · Screenshot: `03_today`

- [x] Zahlen
- [x] Sprache
- [x] Layout
- [x] Design
- [x] Barrierefreiheit
- [x] Zustände

### Bohnen

Datei: `BeansScreen.kt` · Screenshot: `04_beans`

- [x] Zahlen
- [x] Sprache
- [x] Layout
- [x] Design
- [x] Barrierefreiheit
- [x] Zustände

### Bohnen-Detail

Datei: `BeanDetailScreen.kt` · Screenshot: `05_bean_detail`

- [x] Zahlen
- [x] Sprache
- [x] Layout
- [x] Design
- [x] Barrierefreiheit
- [x] Zustände

### Neue/Bearbeiten Bohne

Datei: `AddBeanScreen.kt` · Screenshot: `06_add_bean`

- [x] Zahlen
- [x] Sprache
- [x] Layout
- [x] Design
- [x] Barrierefreiheit
- [x] Zustände

### Shot

Datei: `ShotScreen.kt` · Screenshot: `07_shot`

- [x] Zahlen
- [x] Sprache
- [x] Layout
- [x] Design
- [x] Barrierefreiheit
- [x] Zustände

### Karte

Datei: `MapScreen.kt` · Screenshot: `08_map`

- [ ] Zahlen
- [ ] Sprache
- [ ] Layout
- [ ] Design
- [ ] Barrierefreiheit
- [ ] Zustände

### Entdecken

Datei: `DiscoverScreen.kt` · Screenshot: `09_discover`

- [ ] Zahlen
- [ ] Sprache
- [ ] Layout
- [ ] Design
- [ ] Barrierefreiheit
- [ ] Zustände

### Account

Datei: `AccountScreen.kt` · Screenshot: `10_account`

- [ ] Zahlen
- [ ] Sprache
- [ ] Layout
- [ ] Design
- [ ] Barrierefreiheit
- [ ] Zustände

### Röster-Karte

Datei: `RoasterCardScreen.kt` · Screenshot: `11_roaster_card`

- [ ] Zahlen
- [ ] Sprache
- [ ] Layout
- [ ] Design
- [ ] Barrierefreiheit
- [ ] Zustände

