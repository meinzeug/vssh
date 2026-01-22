# Device Testing (Codex)

## Ziel
Die vssh App wird direkt auf einem per USB verbundenen Android‑Gerät getestet (Install, Launch, UI‑Flow, SSH, Agent, STT/TTS). Tests erfolgen über ADB.

## Vorgehen (Kurz)
1) Build APK via `./gradlew assembleDebug`.
2) Installieren mit `adb install -r`.
3) App starten mit `adb shell am start -n com.example.vssh/.MainActivity`.
4) UI bedienen mit `adb shell input` (tap/swipe/text/keyevent).
5) Screenshots via `adb exec-out screencap -p`.
6) UI‑Struktur prüfen mit `adb shell uiautomator dump`.
7) Logs prüfen mit `adb logcat -d`.

## ADB‑Keyboard
Für zuverlässige Texteingaben wird das ADB‑Keyboard IME verwendet:
- Install: APK von F‑Droid
- Enable: `adb shell ime enable com.android.adbkeyboard/.AdbIME`
- Set: `adb shell ime set com.android.adbkeyboard/.AdbIME`

## Typische Commands
- Geräteliste: `adb devices`
- App starten: `adb shell am start -n com.example.vssh/.MainActivity`
- Screenshot: `adb exec-out screencap -p > screen.png`
- UI dump: `adb shell uiautomator dump /sdcard/uidump.xml`
- Log prüfen: `adb logcat -d | rg -i "vssh|AndroidRuntime"`

## ADB‑Keyboard Texteingabe (Broadcast)
Wenn `adb shell input text` zu fehlerhaft ist, Text über Broadcast setzen:
- Text senden: `adb shell am broadcast -a ADB_INPUT_TEXT --es msg "text"`
- Enter: `adb shell input keyevent 66`
- Löschen (mehrfach): `adb shell input keyevent 67`

## Testfälle (Beispiele)
- SSH verbinden (Host/Port/User/Pass)
- Kommando senden (z.B. `whoami`)
- Agent öffnen, Frage senden
- TTS: Antwort wird vorgelesen
- STT: Spracheingabe in Agentenfeld
- Send‑to‑SSH: Agentantwort in SSH schicken

## Aktuelle Fokus‑Tests
- Netflix‑Style UI prüfen (Farben, Kontrast, Buttons)
- Agent‑Tools‑Flow: Frage “Was war letzte Nacht auf dem Server?”
- Logs/Status: `journalctl`, `last`, `lastb` Outputs im Agent‑Reply
- Fehlerfall: ungültiger OpenRouter‑Key -> klare Fehlermeldung

## Letzter Testlauf (ADB, USB‑Device)
- SSH Login: famyos.com:22 (root) -> verbunden
- Terminal: `whoami` gibt `root` zurück
- Agent‑Tools‑Flow: Frage “Was war letzte Nacht auf dem Server?” liefert Zusammenfassung
- TTS: Antwort wurde vorgelesen (SamsungTTS Log sichtbar)
