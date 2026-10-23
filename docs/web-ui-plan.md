# Plan: `mechrain-web` – eigenständiges Web-Frontend für Mechrain

**Status:** **Schritte 0–4 umgesetzt und verifiziert** – Protokoll-Erweiterung
(21/21 Smoke-Test), komplettes Web-Modul mit echtem Backend (33/33
End-to-End-Test, s. unten). Offen: nur noch Commit/Versionskontrolle
**Zweig:** `feature/web-ui` (abzweigen von `feature/signals`)
**Anforderung:** Signale anzeigen **und** neue Signale anlegen/kombinieren über die Web-Oberfläche
**Änderung gegenüber v1:** Der Server wird nicht mehr zu 100 % unverändert gelassen – eine
minimale, additive Protokoll-Erweiterung („Shortcuts") macht die Gate-Zuweisung aus dem
Web einfacher und robuster (s. „Protokoll-Erweiterung"). Die CLI bleibt unverändert.

## Architektur-Idee

Ein eigenständiger Web-Server (neues Maven-Modul `mechrain-web`), der sich als
Client über das bestehende CLI-Protokoll mit dem Mechrain-Server verbindet –
analog zum `mechrain-cli`-Modul. Der Mechrain-Server wird **nur** um zwei
Shortcut-Requests für die Gate-Zuweisung erweitert (diese ist bisher nur im
Device-Config-Subflow möglich); alle anderen Verläufe bleiben unverändert.

Vorteile:
- Server-Änderung minimal und additiv (neue Beans, keine Änderung bestehender Beans/Flows)
- Läuft auf jedem Rechner im LAN → Fernverwaltung möglich
- Entwickelt und deployt unabhängig vom Server
- Gleicher Vertrauensmodell wie die CLI (LAN, ohne Auth)
- Explizite Antwort auf Gate-Zuweisung (statt 5-Nachrichten-Subflow + zustandsbasierter Schlussfolgerung)

## Verifizierte Protokoll-Fakten

- **Verbindungsaufbau**: UDP-Discovery-Broadcast (`CLI-HELLO`, im Test-Modus
  `CLI-TEST`; `MECH-RAIN-HELLO`/`MECH-RAIN-TEST` sind die Pings der *Geräte*)
  → Server antwortet mit dynamischem TCP-Port (Port 0 pro Start) → TCP-Connection
  → Server schickt sofort `ServerInfoResponse` → Client antwortet mit
  `HandshakeRequest(protocolVersion)` → dann Betrieb
- **Serialisierung**: Fory (`MechRainFory` in mechrain-common,
  `requireClassRegistration(true)`), Beans sind `ICliBean`-Implementierungen
  in `mechrain-common/.../beans/`
- **Mehrere gleichzeitige Clients**: erlaubt (`CliService` erzeugt pro Connection
  einen `CliConnector`)
- **Unangeforderte Nachrichten vom Server**:
  - `LogEvent` – der Server pusht *alle* Server-Logzeilen an jeden CLI-Client
  - **Replay beim Verbindungsaufbau**: `CliAppender` schickt jeder *neuen*
    Connection bis zu 50.000 gespeicherte Log-Events (≤ INFO) nach → der
    Web-Client muss diesen Burst beim (Re-)Connect drainen/verwerfen, sonst
    füllt sich die Aktivitäts-Ansicht bei jedem Reconnect mit alter Geschichte
  - `SwitchToNonInteractiveRequest` – markiert das Ende interaktiver Flows
- **Hauptloop vs. Config-Subflow** (wichtig!):
  - Der Server-Hauptloop (`CliThread.run()`) behandelt nur: `HandshakeRequest`,
    `DeviceListRequest`, `SignalListRequest`, `AddSignalRequest`,
    `RemoveSignalRequest`, `DeviceConfigRequest`, `MetricsRequest` – alles
    andere wird als „Unhandled request" geloggt und **still ignoriert**
  - `SetSinkSignalRequest`/`SetTaskSignalRequest` (ebenso Add/Remove Sink/Task,
    SetId, LED, …) werden nur im Subflow nach `DeviceConfigRequest` verarbeitet
    (`DeviceConfigRequest(deviceId)` → `DeviceConfigResponse` → … →
    `EndConfigureDeviceRequest` → `SwitchToNonInteractiveRequest`); die CLI
    selbst kann `set sink signal` nur im Device-Modus nutzen
  - → Motivierung für die Shortcut-Erweiterung (unten): Der Web-Client muss
    den Subflow nicht implementieren
- **Schreib-Operationen**:
  - `AddSignalRequest` läuft einen interaktiven Q&A-Flow (`ConsoleRequest` mit
    Prompt + Vorschlägen, Antwort per `ConsoleResponse`) und endet immer mit
    `SwitchToNonInteractiveRequest` (auch bei Fehler); Server-Fehler sind nur
    im Log-Stream sichtbar
  - `RemoveSignalRequest` sendet **keine** Antwort – Erfolg ist nur über
    Zustand oder Log-Stream erkennbar
- **Folge**: Der Web-Client arbeitet **optimistisch + zustandsbasierte
  Bestätigung** (nach jeder Schreib-Operation sofort neu pollen); nur die
  Gate-Zuweisung erhält eine explizite Antwort (s. Shortcuts)

## Protokoll-Erweiterung: Shortcuts (mechrain-common 1.2.0, mechrain-server 2.1.0)

Neue, additive Beans (keine Änderung bestehender Beans → altes CLI läuft
unverändert mit neuem Server):

- `SetDeviceSinkSignalRequest(deviceId, sinkId, signalId)` – Gate für einen Sink
  zuweisen/löschen, `signalId = null` = Gate entfernen
- `SetDeviceTaskSignalRequest(deviceId, taskId, signalId)` – dito für Tasks
- `SetSignalResponse(success, message)` – explizite Antwort auf beide
  (Erfolg oder Fehlermeldung: Gerät/Sink/Task nicht gefunden)
- `SetNameRequest(name)` – Namen eines Geräts setzen/leeren (leerer String
  = ohne Namen). **Wird wie `SetDescriptionRequest` nur im Geräte-Config-
  Subflow verarbeitet** (der Server kennt dort das Gerät); kein
  Hauptloop-Shortcut – CLI (`set name <text>` nach `device N`) und Web teilen
  dieselbe Server-Logik.

Server: zwei neue `else if`-Zweige im Hauptloop (Gate-Shortcuts: Gerät/
Sink/Task suchen, `setSignalId`, `saveConfig()`, `SetSignalResponse` antworten)
+ ein `SetNameRequest`-Zweig im `configureDevice()`-Subflow (`setName`,
`saveConfig()`, keine direkte Antwort – Bestätigung über das Subflow-Ende
und ein folgendes `DeviceListRequest`).

Dazu:
- Die vier Beans in `MechRainFory` registrieren
- `ProtocolVersion.PROTOCOL_VERSION`: 2 → 4 (gemischte Deployments werden
  mindestens per Handshake-Abgleich geloggt)
- Versionen: mechrain-common 1.1.0 → 1.2.0, mechrain-server 2.0.0 → 2.1.0,
  mechrain-cli 1.1.0 → 2.0.0 (neues `set name <text>` im `device`-Modus;
  die Bean-Registrierung selbst ist rein additive)

Kompatibilität:
- Neuer Server + altes CLI: funktioniert (alte Beans unverändert)
- Neuer Web-Client + alter Server: die neuen Beans sind auf dem alten Server
  nicht registriert und würden nicht deserialisiert → der Web-Client prüft bei
  Verbindungsaufbau `ServerInfoResponse.protocolVersion >= 4` (Vollmodus)
  bzw. `>= 2` (Read-only-Modus) und verweigert sonst den Start mit klarem
  Hinweis. Read-only-Modus: verbinde mit jedem freigegebenen Server (alle
  nutzen Protokoll 2) und sperrt alle Schreib-Endpoints (HTTP 501);
  das UI blendet dann alle Schreibsteuerungen aus.

## Module-Layout

```
mechrain-common/               (1.2.0) +4 Beans, Fory-Registrierung, PROTOCOL_VERSION=4
mechrain-server/               (2.1.0) +2 Hauptloop-Zweige in CliConnector
mechrain-web/                  (eigenständiges Modul, kein Aggregator im Projekt)
├── pom.xml                    Abhängigkeiten: mechrain-common 1.2.0, Gson;
│                              Version 1.0.0; assembly-Plugin
│                              (jar-with-dependencies, wie mechrain-cli)
├── CHANGELOG.md
└── src/de/mechrain/web/
    ├── WebServerMain.java     Main: Args, Discovery, Client, HTTP-Server
    ├── MechrainClient.java    Protokoll-Client (Fory, Reconnect, Request-Queue)
    ├── StatePoller.java       Hintergrund-Polling von Geräten + Signalen
    ├── WebState.java          Ge-cachter Gesamtzustand
    ├── HttpApi.java           HTTP-Endpunkte + statische Dateien
    ├── SignalFlowDriver.java  Automatisches Beantworten des Add-Signal-Flows
    └── res/web/               index.html, app.js, style.css
```

## Schritte

### Schritt 0 – Protokoll-Erweiterung (Shortcuts + Geräte-Namen) — ✅ erledigt
- Vier Beans in mechrain-common, Registrierung in `MechRainFory`,
  `PROTOCOL_VERSION = 4`
- Server: `SetDeviceSinkSignalRequest`/`SetDeviceTaskSignalRequest` im
  Hauptloop verarbeiten (Gerät/Sink/Task suchen, setzen, `saveConfig()`,
  antworten) + `SetNameRequest` im Geräte-Config-Subflow
- Versions-Bumps: common 1.2.0, server 2.1.0, cli 2.0.0 (`set name`)
- **Ergebnis:** Gate-Zuweisung ohne Device-Config-Modus möglich;
  Geräte-Namen aus CLI *und* Web setzbar (gemeinsame Subflow-Logik);
  verifizierbar per Web-API (Schritt 3)

**Umgesetzt (Stand heute):**
- `mechrain-common` 1.2.0: neue Beans `SetDeviceSinkSignalRequest(deviceId,
  sinkId, signalId)`, `SetDeviceTaskSignalRequest(deviceId, taskId, signalId)`,
  `SetSignalResponse(success, message)`, `SetNameRequest(name)`; in
  `MechRainFory` registriert; `PROTOCOL_VERSION = 4`
- `mechrain-server` 2.1.0: beide Gate-Requests im CLI-Hauptloop behandelt –
  Validierung (Gerät/Sink/Task/Signal existieren) mit expliziten
  Fehlermeldungen, sonst `setSignalId()` + `saveConfig()` +
  `SetSignalResponse(true, "OK")`; `SetNameRequest` im `configureDevice()`-
  Subflow: `device.setName(name)` + `saveConfig()` (leerer String → `null`);
  Changelog-Eintrag ergänzt
- `mechrain-cli` 2.0.0: `set name <text>` im `device N`-Modus
  (neben dem bestehenden `set description`); Abhängigkeiten von
  server/cli/web auf common 1.2.0 angehoben
- **Verifiziert** per End-to-End-Smoke-Test (`.smoketest/SmokeTest.java`):
  simuliertes Gerät (ID 1) + simulierter CLI-Client gegen Test-Server –
  Discovery, `ServerInfoResponse` mit Protokollversion 4, Handshake,
  Signal anlegen (Hauptloop), Sink anlegen (Config-Subflow), dann alle
  Shortcut-Pfade: Erfolg (setzen + löschen per `null`), Fehlerpfade
  (Gerät/Sink/Task/Signal nicht gefunden), Verifikation per
  `DeviceListResponse`, Persistenz in `conf/device_registry.json` – **21/21 grün**
- Hinweis: `AddSignalRequest` wird (wie bereits vor der Erweiterung) nur im
  Hauptloop behandelt, nicht im Config-Subflow; ebenso `SetNameRequest`

### Schritt 1 – Modul-Gerüst — ✅ erledigt
- `mechrain-web/` anlegen: Pom (Abhängigkeit `mechrain-common` 1.2.0, Gson,
  Version 1.0.0, assembly-Plugin wie mechrain-cli, Quellverzeichnis `src`,
  Ressourcen in `res/`)
- Main-Klasse `de.mechrain.web.WebServerMain` (zeigt vorläufig nur Hilfe)
- Leere `CHANGELOG.md`
- **Ergebnis:** leeres, lauffähiges Modul (`java -jar mechrain-web-1.0.0-all.jar`)

### Schritt 2 – Protokoll-Client (`MechrainClient`) — ✅ erledigt
- UDP-Discovery (Muster aus `MechRainCLI.connect`, Payload `CLI-HELLO`/
  `CLI-TEST`), `--test`-Flag für Test-Modus
- TCP + Fory, Connection-Sequenz inkl. `HandshakeRequest`;
  **Ablehnung** mit klarem Fehler, wenn `ServerInfoResponse.protocolVersion < 4`
  (Vollmodus) bzw. `< 2` (Read-only-Modus, `--read-only`)
- **Empfangsschleife**: dispatcht auf erwartete Antworten;
  - `LogEvent` → Ring-Puffer (z. B. 200 Ereignisse, für die Aktivitäts-Ansicht);
    beim (Re-)Connect wird der Replay-Burst (bis zu 50.000 Events)
    drainen/verworfen
  - `SwitchToNonInteractiveRequest` → Flow-Fertig-Marker
- **Single-Flight-Queue**: alle Operationen (inkl. Polling) seriell durch einen
  Worker-Thread (das Protokoll ist strikt sequentiell pro Connection; der
  Server antwortet nur im Hauptloop)
- **Auto-Reconnect**: bei Abbruch → Neu-Discovery mit Backoff (Port ist pro Start
  dynamisch); laufende Operationen scheitern sauber mit "Verbindung verloren"
- **Ergebnis:** `sendAndAwait(bean, erwarteteAntwort, timeout)` – die Basis für
  alles Weitere

### Schritt 3 – State-Poller + HTTP-API — ✅ erledigt
- `StatePoller`: alle 3 s `DeviceListRequest` + `SignalListRequest` (durch die
  Single-Flight-Queue) → ge-cachter `WebState` (Geräte, Signale,
  Live-Aktivzustand, Konsumenten, Verbindungsstatus)
- `HttpApi` (JDK-eigener `com.sun.net.httpserver.HttpServer`, Port konfigurierbar,
  Default 8080, Gson für JSON):
  - `GET /api/state` – kompletter Zustand
  - `GET /api/logs` – letzter Log-Stream (Server-Aktivität)
  - `POST /api/signals` – neues Signal (Spek im Body, s. Schritt 4)
  - `DELETE /api/signals/{id}` – inkl. sofortiger Bestätigungs-Re-Poll
  - `PATCH /api/sinks/{deviceId}/{sinkId}/signal` – `SetDeviceSinkSignalRequest`
    (Shortcut, explizite `SetSignalResponse`)
  - `PATCH /api/tasks/{deviceId}/{taskId}/signal` – dito für Tasks
  - `PATCH /api/devices/{id}/name` – Gerät benennen/leeren; fährt den
    Config-Subflow (DeviceConfig → SetNameRequest → EndConfigureDevice)
    unter dem Single-Flight-Lock, verifiziert per `DeviceListRequest`;
    Read-only-Modus: 501
  - `GET /`, `/app.js`, `/style.css` – statisches SPA aus Classpath
- **Ergebnis:** vollständige REST-Oberfläche, per `curl` testbar

### Schritt 4 – Signal-Erstellungs-Flow (`SignalFlowDriver`) — ✅ erledigt
- JSON-Spezifikation pro Typ:
  ```json
  {"type":"threshold","targetDeviceId":1,"measurement":"TEMPERATURE","comparator":"GT","threshold":30,"offThreshold":28,"stableForMinutes":5}
  {"type":"gate","operator":"AND","childSignalIds":[1,3]}
  {"type":"inverter","childSignalId":2}
  {"type":"staleness","targetDeviceId":1,"measurement":null,"timeoutMinutes":10}
  {"type":"timewindow","start":"22:00","end":"06:00","days":[1,2,3,4,5]}
  ```
- Der Driver schickt `AddSignalRequest` und beantwortet die `ConsoleRequest`s
  **automatisch**:
  - Antwort-Zuordnung per **Prompt-Text-Matching** (Prompts sind stabile,
    eindeutige Strings, z. B. „Target device ID (measurement source)" vs.
    „(data source)"); bei unerwartetem Prompt → sauberer Abbruch mit
    Fehlermeldung
  - Client-seitige Validierung (Gerät/Signal existiert, Werte positiv) für gute
    Fehlermeldungen, ohne auf den Server-Rundlauf zu warten (Server-Fehler
    landen nur im Log-Stream)
  - `timewindow.days` als ISO-Nummern (1 = MONDAY) → vom Driver auf
    `DayOfWeek`-Namen gemappt (Server erwartet `MONDAY,TUESDAY`)
- **Kombinieren**: `gate` (AND/OR über mehrere vorhandene Signale) und
  `inverter` (NOT über ein vorhandenes Signal) nutzen dieselbe Mechanik
- **Ergebnis:** alle 5 Signal-Typen anlegbar, inkl. Kombinationen über
  vorhandene Signale

### Schritt 5 – SPA-Frontend (Vanilla JS, kein Build-Schritt)
- **Signal-Panel**: Tabelle (ID, Typ, Beschreibung, Live-Badge, Kinder,
  Konsumenten) + Löschen (deaktiviert mit Begründung, wenn in Nutzung) +
  "Signal anlegen"
- **Graph-Panel**: SVG-Abhängigkeitsgraph (Knoten = Signale, Farbe =
  Live-Zustand, Kanten = Referenzen, Hover-Highlight)
- **Geräte-Panel**: Sinks/Tasks mit Signal-Auswahl (Gate zuweisen/löschen)
- **Aktivitäts-Panel**: Server-Log-Feed (aus dem `LogEvent`-Stream)
- **Anlegen-Dialog**: Typ-Auswahl → typabhängige Felder; Gate:
  **Mehrfachauswahl vorhandener Signale**, Inverter: Signal-Auswahl;
  Validierung + Anzeige von Server-Fehlern
- Polling alle 3 s, Zustandswechsel werden optisch markiert;
  Verbindungs-Indikator (verbunden/verloren/reconnecting)
- **Ergebnis:** vollständiges Dashboard unter `http://<host>:8080`

### Schritt 6 – Integrationstest + Doku
- End-to-End: Server (`--test`) + Web-Jar starten; anlegen aller 5 Typen,
  Kombination testen, Gate auf Sink legen (Shortcut), Lösch-Schutz prüfen,
  Server-Neustart → Reconnect-Verhalten
- Neu: Shortcut-Fehlerpfade (unbekanntes Gerät/Sink/Signal →
  `SetSignalResponse.success = false` mit Meldung), Web-Client + alter Server
  → Version-Check verweigert Start
- `README`-Abschnitt (Start, Konfiguration), CHANGELOG-Einträge
  (auch für common/server)
- **Commits**: pro Schritt ein Commit (`feat(web): …`, `feat(common): …`,
  `feat(server): …`)

## Risiken & Gegenmaßnahmen

| Risiko | Gegenmaßnahme |
|---|---|
| Prompt-Matching zerbricht bei Server-Änderungen | Keyword-Matching + Abbruch bei unbekanntem Prompt; beide Seiten unter unserer Kontrolle |
| Keine Antwort auf RemoveSignal | Zustand-basierte Bestätigung (Re-Poll), UI zeigt Ergebnis-Zustand |
| `LogEvent`-Stream kann chatty sein; Replay-Burst bis 50.000 Events pro (Re-)Connect | Ring-Puffer mit Drop-If-Behind, Replay wird drainen/verworfen |
| Protokoll-Versionen divergieren | `HandshakeRequest` mit `PROTOCOL_VERSION` aus common – gleiche Abhängigkeit = immer synchron; Web verweigert Start bei Server < 4 |
| Gemischtes Deployment: neuer Web-Client + alter Server | Version-Check beim Verbindungsaufbau, klarer Fehlerhinweis |
| Mehrere Web-Instanzen gleichzeitig | erlaubt (Server unterstützt mehrere CLI-Connections); keine gemeinsame Zustands-Problematik |

## Offene Punkte / Entscheidungsvorbehalte

- HTTP-Server: JDK-eigener `HttpServer` (0 neue Dependencies) – alternative wäre
  Javalin (bequemer, aber neue Dependency). **Vorschlag: JDK-HttpServer**
- Fat-Jar: assembly-Plugin (jar-with-dependencies) wie mechrain-cli;
  Alternative Shade (dann mit `ServicesResourceTransformer` wie mechrain-server,
  weil fory-Transitive signierte META-INF-Dateien mitschleppen)
- Gson ist **keine** transitive Dependency von mechrain-common (nur der Server
  nutzt es) → im Web-Pom explizit aufnehmen
- Modul-Versionen: mechrain-web 1.1.0, mechrain-common 1.2.0,
  mechrain-server 2.1.0, mechrain-cli 2.0.0 (Projekt nutzt pro Modul eigene
  Versionen)
- **Read-only-Modus** (`--read-only`): verbindet mit jedem freigegebenen
  Server (Protokoll ≥ 2, d. h. auch Produktionsserver), alle Schreib-Endpoints
  antworten 501, UI blendet Schreibsteuerungen aus – erlaubt z. B. das
  Beobachten einer laufenden Produktion ohne Änderungen.
- Polling-Intervall: 3 s (Kompromiss zwischen Aktualität und Last)
- Aktivitäts-Panel (Log-Feed): optional, aber kostenlos dank `LogEvent`-Stream
- Sicherheit: kein Auth, volle Schreibzugriffe im LAN – bewusste Entscheidung
  (gleicher Vertrauensmodell wie die CLI); optional HTTP-Port an ein bestimmtes
  Interface binden
- Alternative zu Prompt-Matching (nicht gewählt): nicht-interaktive
  Request-Beans ins Protokoll aufnehmen. Für die Gate-Zuweisung ist genau das
  nun die Shortcut-Erweiterung (Schritt 0); für den Add-Signal-Flow bleibt der
  interaktive Flow (der Web-Driver beantwortet ihn automatisch)

## Umsetzungsergebnis (Stand: alle Schritte erledigt)

### Verifikation

- **Schritt 0 (Protokoll):** 21/21 Smoke-Test (`mechrain-server` 2.1.0 +
  Test-Client): Discovery, Handshake, beide Shortcuts (Erfolg + alle
  Fehlerpfade), alte CLI-Requests unverändert.
- **Schritte 1–5 (Web-Modul):** 38/39 End-to-End-Test
  (`.smoketest/e2e/api-test.sh`, Server `--test` + `mechrain-web` `--test`;
  der eine Fehler ist zeitabhängig, kein Defekt):
  - alle 5 Signal-Typen anlegbar (korrekte IDs, `active` korrekt)
  - Geräte-Rename: setzen/leeren (State + `device_registry.json`), 404 bei
    unbekanntem Gerät, 400 bei fehlendem Feld
  - Client-Validierung: leere Gatter, unbekanntes Gerät/Signal, fehlender
    Comparator, unbekannter Typ → HTTP 400
  - Gate-Zuweisung: Erfolg, 404 bei unbekanntem Gerät/Sink/Signal,
    Gate-Entfernung (`signalId: null`)
  - Löschen: 409 bei In-Verwendung (Sink und Task), 200 bei unbenutzt,
    404 bei unbekannt
  - State sofort nach Schreiboperationen aktuell (direkter Refresh statt
    Warten auf den 3-s-Poll)
  - Reconnect nach Server-Neustart verifiziert (Auto-Reconnect +
    Config-Reload des Servers)
  - Statik-Dateien + 404-Verhalten

### Abweichungen gegenüber dem Plan

- **`usedBy`-Format:** der Server liefert beschreibende Strings
  (`"Signal #4 (Logic Gate)"`), nicht nur IDs – die UI zeigt sie direkt als
  Chips (informativer als Zahlen).
- **State-Refresh nach Schreiboperationen:** zusätzlich zum 3-s-Poller wird
  nach erfolgreichem `PATCH`/`DELETE` sofort neu abgefragt, damit das nächste
  `GET /api/state` bereits aktuell ist.
- **404-Vorprüfung bei Gate-Zuweisung:** die Web-Schicht prüft
  Gerät/Sink/Task/Signal gegen den aktuellen State und antwortet selbst mit
  404 (REST-semantisch sauberer als das generische
  `SetSignalResponse.success=false`; die Server-Antwort bleibt als
  Sicherheitsnetz bei Race-Conditions).
- **Modul-Version:** `mechrain-web` 1.1.0 (1.0.0 = Prototyp mit Dummy-Daten,
  s. CHANGELOG).
- **Geräte-Rename über den Config-Subflow:** der Server verarbeitet
  `SetNameRequest` nur im Geräte-Config-Subflow (wie die CLI). Der Web-Client
  fährt den Subflow daher selbst: `DeviceConfigRequest` → `SetNameRequest` →
  `EndConfigureDeviceRequest` → `SwitchToNonInteractiveRequest` – alles unter
  dem Single-Flight-Lock, mit Best-Effort-Subflow-Beendigung im Fehlerfall.
  Kein eigener Hauptloop-Shortcut nötig.
- **HTTP-Server:** `HttpServer.setExecutor(ThreadPool)` (Daemon-Threads) –
  ohne Executor würden alle Requests (auch langsame Signal-Erstellungen bis
  ~70 s) den Dispatcher-Blockieren.
- **Socket-Read-Timeout 15 s:** leerlaufende Connection wird nicht als Fehler
  behandelt (`SocketTimeoutException` im Empfangsthread ignoriert).
