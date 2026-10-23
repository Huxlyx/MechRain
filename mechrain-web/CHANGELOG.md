# Changelog

## 1.1.0
- Echter Backend-Anschluss an den Mechrain-Server:
  - `MechrainClient`: UDP-Entdeckung, TCP/Fory-Verbindung, Single-Flight
    Request/Antwort-Protokoll, automatischer Reconnect (exponentieller
    Backoff 2 s … 30 s), Protokollversionsprüfung (voll: mind. 4, read-only: mind. 2)
  - `StatePoller`: alle 3 s Geräte- und Signalliste abfragen
  - `SignalFlowDriver`: interaktiven AddSignal-Dialog des Servers
    maschinell beantworten (alle 5 Signal-Typen), inkl. clientseitiger
    Validierung (unbekannte Geräte/Signale, leere Gatter, Zyklen)
  - `HttpApi`: REST-API (`/api/state`, `/api/logs`, `POST/DELETE
    /api/signals`, `PATCH /api/sinks|tasks/…/signal`, `PATCH
    /api/devices/…/name`) + Statische Dateien;
    404-Vorprüfungen für Gate-Zuweisungen, 409 bei in-Verwendung-Stehendem
    beim Löschen, direkter State-Refresh nach Schreiboperationen
  - Geräte umbenennen: `PATCH /api/devices/{id}/name` (leerer String löscht
    den Namen); der Client fährt dafür den Geräte-Config-Subflow des Servers
    (DeviceConfig → SetNameRequest → EndConfigureDevice, wie die CLI) unter
    dem Single-Flight-Lock und bestätigt per `DeviceListRequest`-Re-Poll;
    UI: „✎“-Button in der Geräte-Karte öffnet ein Inline-Feld
    (Enter/Blur speichert, Escape bricht ab); nur im Vollmodus sichtbar
  - `--test`-Modus (CLI-TEST-Entdeckung) für die Entwicklungsumgebung
  - `--read-only`-Modus: Verbindung auch zu älteren Servern (Protokoll ≥ 2),
    alle Schreiboperationen blockiert (HTTP 501), UI blendet Schalter und
    Löschen aus – geeignet, um die Web-Oberfläche sicher an produktive
    Server anzubinden, die noch keine Signale unterstützen
- Prototyp-Daten (DummyBackend) entfernt; UI unverändert weiterverwendet
- Fehlerbehebungen:
  - Statische Dateien: korrekter `Content-Type` je nach Dateinamen
    (davor wurde die Startseite vom Browser heruntergeladen statt angezeigt)
  - `StatePoller`: Server ohne Signalunterstützung (vor 2.1.0) antworten
    nie auf `SignalListRequest`; nach einem Timeout wird die Anfrage
    nicht mehr gesendet (wird nach jeder Neuverbindung erneut geprüft),
    Geräte und Verbindungszustand werden weiterhin aktualisiert
  - Protokollierungsstufen: korrekte Zuordnung der Log4j2-intLevel-Werte
    (davor unlesbare `LEVELxxx`-Kennungen im Log-Feed)
  - UI: Demo-Banner wird im echten Backend ausgeblendet; Geräte-ID in der
    Geräteübersicht und im Abhängigkeitsgraphen sichtbar; die
    Geräte-Beschreibung dient als Label-Zeile, wenn ein Name gesetzt ist,
    andernfalls zeigt die Karte die Beschreibung als Hauptlabel

## 1.0.0
- Erstes Gerüst: statisches Web-UI-Prototyp mit Dummy-Daten
  (Signale anzeigen/anlegen/löschen, Gate-Zuweisung, Abhängigkeitsgraph,
  Aktivitäts-Feed) – noch ohne echte Server-Verbindung
