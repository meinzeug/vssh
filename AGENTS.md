# Projekt AGENTS

## Ziel
Native Android-SSH-Client, der eine Shell-Session über Smartphone-Eingabe steuert. Später wird STT (Speech-to-Text) integriert, damit Kommandos gesprochen werden können.

## Struktur
- `app/src/main/java/com/example/vssh/MainActivity.kt`: UI, Button-Logik, Terminal-Ausgabe.
- `app/src/main/java/com/example/vssh/SshClient.kt`: SSH-Verbindung, Shell-Channel, IO-Streams.
- `app/src/main/java/com/example/vssh/SpeechInput.kt`: STT-Interface + Android SpeechRecognizer-Implementierung.
- `app/src/main/java/com/example/vssh/ChatActivity.kt`: OpenRouter-Chat-Agent UI.
- `app/src/main/java/com/example/vssh/SettingsActivity.kt`: OpenRouter Settings (API Key, Model, Base URL).
- `app/src/main/java/com/example/vssh/OpenRouterClient.kt`: OpenRouter HTTP-Client.
- `app/src/main/java/com/example/vssh/SettingsStore.kt`: Persistenz für OpenRouter Settings.
- `app/src/main/java/com/example/vssh/SshBridge.kt`: Bridge zwischen Agent und SSH-Session.
- `app/src/main/java/com/example/vssh/MessageBubbleView.kt`: Chat-Bubbles inkl. Codeblocks + Send-to-SSH.
- `app/src/main/java/com/example/vssh/SshCommandSafety.kt`: Allowlist für sichere SSH-Commands im Tools-Flow.
- `app/src/main/res/layout/activity_main.xml`: Terminal-UI mit Connect/Send/Voice.
- `app/src/main/res/layout/activity_chat.xml`: Chat-UI.
- `app/src/main/res/layout/activity_settings.xml`: Settings-UI.
- `app/src/main/AndroidManifest.xml`: Internet + Mikrofon-Permission.
- `docs/device-testing.md`: ADB-Testablauf + aktuelle Testergebnisse.

## Entwicklungs-Workflow
1) Änderungen an SSH-Logik in `SshClient.kt` halten, UI nur für Darstellung.
2) Keine Credentials persistieren; später auf Key-Auth umstellen.
3) Für STT eine Implementierung von `SpeechInputController` ergänzen und in `MainActivity` instanziieren.
4) Neue Features zuerst in kleinen UI-Schritten testen (Verbinden, Befehl senden, Ausgabe prüfen).
5) Nach erfolgreicher Arbeit: `git status` prüfen, committen und pushen.

## STT-Erweiterung (geplant)
- SpeechRecognizer liefert Text direkt in `commandInput` und triggert `sendCommand()`.
- Optional: Push-to-talk und visuelles Feedback bei aktivem Zuhören.

## Hinweise
- Shell-Output wird aktuell als Text appended. Für umfangreiche Sessions später Paging oder Buffer-Strategie einplanen.
- Für Produktion: Host-Key-Checking und sichere Key-Verwaltung ergänzen.
