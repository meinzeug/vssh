# vssh

Native Android-App, um per SSH eine Shell-Session zu starten und den Server direkt vom Smartphone zu administrieren – inkl. Voice, Agent und OpenRouter-Integration.

## Features (aktuell)
- SSH-Verbindung per User/Passwort
- Interaktive Shell (ChannelShell) mit Terminal-Ausgabe
- Befehle per Text senden
- STT-Button (Android SpeechRecognizer) für SSH-Commands
- Chat-Agent über OpenRouter (API Key + Model + Base URL in Settings)
- Agent kann SSH-Commands vorschlagen und per “Send to SSH” senden
- Agent-Tools-Flow: holt Logs/Status per SSH-Exec und fasst zusammen
- TTS: Antworten werden vorgelesen

## Geplante Erweiterungen
- STT-Workflow stabilisieren (UI-Feedback, Push-to-talk, Fehlercodes mappen)
- Key-Auth + Host-Key-Verification
- Session-Handling verbessern (History, Paging, sichere Speicherung)
- STT-Workflow stabilisieren (UI-Feedback, Push-to-talk, Fehlercodes mappen)
- Sicherheits-Policy für Tools weiter ausbauen (Allowlist + Guardrails)

## Build
1) Android Studio öffnen und Projekt importieren
2) Gradle-Sync ausführen
3) App starten (Emulator oder Gerät)

CLI-Variante:
```bash
./gradlew assembleDebug
```

## Berechtigungen
- `INTERNET` für SSH
- `RECORD_AUDIO` für STT (Runtime Permission auf Android 6+)

## Sicherheit
Diese Version deaktiviert Host-Key-Checking für schnelle Tests. Für Produktion unbedingt Host-Key-Verification und Key-Auth ergänzen.
