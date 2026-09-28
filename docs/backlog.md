# Bewusst verschoben

Ideen und Features, die beim Durcharbeiten der App aufgefallen sind, aber
nicht zum Ziel „jede erwartbare Aktion ist möglich, jeder Screen stimmig“
gehören. Sie werden hier notiert statt gebaut.

| Was | Warum verschoben |
| --- | --- |
| Merkliste für Kaffee-Empfehlungen („Merken“ in Entdecken) | Die Empfehlungen sind noch Beispielinhalte ohne echten Katalog; eine Merkliste ohne Ort, an dem man sie wiederfindet, wäre ein Knopf ohne Funktion. Kommt mit dem echten Katalog. |
| iOS-App | Der Account-Screen versprach Sync „mit dem iPhone“. Es gibt noch keine iOS-App, der Text ist entfernt. Das Kotlin-Multiplatform-Fundament (`core`, `data` mit iOS-Targets) steht bereit. |
| Echter Katalog für Entdecken (Cafés, Röstereien, Empfehlungen, Öffnungszeiten) | Die Inhalte sind klar als Beispiele gekennzeichnet. Ein echter Katalog braucht eine Datenquelle und Pflege, das ist ein eigenes Produkt-Thema. |
| Eigener Standort für „In der Nähe“ | Setzt den echten Katalog voraus. Ohne ihn wäre die Standortfreigabe eine Berechtigung ohne Nutzen. |
| Wasserfilter nach Litern | Pflegeintervalle kennen Tage, Shots und kg. Liter kann die App ohne Durchflussmessung nicht zählen, deshalb bleibt „oder 50 l“ reine Beschreibung. |
| Mehrere Maschinen oder Mühlen gleichzeitig | Das Modell erlaubt es, die Oberfläche geht von je einem Gerät aus. „Tauschen“ deckt den häufigen Fall ab. |
| Shot nachträglich einer anderen Bohne zuordnen | Beim Bearbeiten bleibt die Bohne fest, weil sonst zwei Tüten und die Zähler umgebucht werden müssten. Der Weg über Löschen (mit Rückgängig) und neu Brühen funktioniert. |
| Kaufdatum und Preis pro Nachkauf (mehrere Tüten derselben Bohne) | Eine Bohne steht heute für eine Tüte. Nachkäufe als eigene Einträge wären eine Modelländerung mit Auswirkungen auf den Sync. |
| Einheitliche Datumsanzeige mit Wochentag und Uhrzeit in `core/format` | Datum und Uhrzeit werden an zwei Stellen noch im UI zusammengesetzt. Das funktioniert und ist deutsch, aber noch nicht zentral. |
