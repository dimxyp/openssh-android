# AndroSSH

AndroSSH is a brand-new, original, open-source Android SSH client app skeleton written in Kotlin. It is a clean-room project: it is not derived from JuiceSSH code, decompiled APK output, proprietary assets, or any other closed-source application code. Similar Android terminal clients may inspire broad feature and UX goals only; all implementation in this repository is original and uses open-source libraries.

> **Originality statement:** every line of Kotlin, every Compose layout, and every XML/resource file in this repository — including the extra-keys terminal keyboard, the SFTP browser, the home-screen widget, and the encrypted export/import feature — was written from scratch for this project. None of it was copied, ported, decompiled, or otherwise derived from JuiceSSH or any other closed-source application. Where UX ideas are conceptually similar to other terminal apps (e.g. "an extra-keys bar with Ctrl/Esc/Tab/arrows and a swipe gesture"), only the *idea* was used as inspiration; the implementation is entirely original.

## Goals

- Modern Android app foundation using Kotlin and Jetpack Compose.
- Package ID: `com.androssh.app`.
- Display name: `AndroSSH`.
- Minimum SDK 24, compile/target SDK 36.
- MVVM-ish structure with separate UI, ViewModel, data/repository, and SSH layers.
- SSH connectivity scaffolded through [`sshj`](https://github.com/hierynomus/sshj) (Apache-2.0). `SshConnectionManager` explicitly registers the real `org.bouncycastle` JCE provider (bundled transitively via sshj) at startup, ahead of Android's own incomplete built-in "BC" provider, so modern key exchange algorithms such as `curve25519-sha256`/X25519 negotiate correctly.
- Local host profiles stored with Room.
- Sensitive password values stored outside Room using AndroidX Security Crypto encrypted preferences backed by Android Keystore.

## Current project structure

```text
app/src/main/java/com/androssh/app/
├── data/
│   ├── backup/   # AES-GCM encrypted export/import of saved connections
│   └── sftp/     # Repository wrapping sshj's SFTPClient for browsing/transfers
├── ssh/          # SSHJ connection manager, the process-scoped live-session holder, the keep-alive foreground service, and SFTP session wrappers
├── terminal/     # TerminalEmulator: original VT100/ANSI-subset parser + screen buffer (no Compose deps, unit-testable)
├── ui/
│   ├── backup/   # Export/import Compose screen
│   ├── sftp/     # Dual-pane local/remote SFTP browser Compose screen
│   ├── terminal/ # TerminalGrid (renders the emulator's screen buffer) + ExtraKeysBar + TerminalColors + terminal key mappings
│   └── widget/   # Home-screen widget provider + configuration activity
└── viewmodel/    # UI state and ViewModel orchestration (main, SFTP, backup)
```

## Features

- Connection list screen for saved SSH host profiles, with add/edit/delete and per-connection SFTP access.
- Each connection card shows TCP reachability for its configured host and SSH port. This is not
  ICMP ping: a blocked SSH port appears offline even if ping works, while an open SSH port appears
  online even if the host blocks ICMP.
- Password authentication path wired first; private key authentication intentionally stubbed for later work.
- Known-hosts based SSH host-key verification foundation; a trust-on-first-use or host-key management UI is a follow-up task.
- **Real interactive terminal emulator** (`terminal/TerminalEmulator.kt`, `ui/terminal/TerminalGrid.kt`): a from-scratch, original VT100/ANSI-subset parser and in-memory screen buffer (rows/cols grid of styled characters) - not derived from JuiceSSH, Termux, or any other terminal emulator. It supports printable characters, `\r`/`\n`/backspace/tab, cursor positioning (`ESC[<row>;<col>H`), relative cursor movement (`ESC[A/B/C/D`), erase in line/display (`ESC[K`, `ESC[J` and their `0`/`1`/`2` variants), and basic SGR attributes (`ESC[...m` bold + the 8 standard foreground/background colors). Typing is live: every keystroke from a hidden input capture is streamed straight to the shell channel as it's typed (no "Send" button), and `SshSessionHolder` batches/throttles screen-buffer updates (~20 fps) so fast output (e.g. `top`) doesn't recompose the UI per byte. Known limitations: fixed screen size (not yet resized to the device's actual font metrics/orientation), no scrollback buffer, and no support for alternate-screen mode, 256-color/truecolor SGR codes, or bracketed paste.
- **Extra-keys bar** (`ui/terminal/ExtraKeysBar.kt`): a from-scratch Compose component laid out as two compact rows of flat text keys (no chip borders or elevation) spread evenly across the width: `ESC / | - HOME ↑ END PGUP FN` and `TAB CTRL ALT ← ↓ → PGDN ⇄ ⌨`. `FN` toggles a function-keys row, `CTRL`/`ALT` open a letter picker, `⇄` flips the bar into a text-edit/selection mode (select all/copy/cut/paste/undo/redo) - which the swipe gesture still does too - and `⌨` shows/hides the soft keyboard. Keys send raw ANSI escape sequences / ASCII control codes (`TerminalKeys.kt`) straight to the SSH shell channel, and the key set is data-driven so more keys can be added later.
- **SFTP file transfer** (`data/sftp/`, `viewmodel/SftpViewModel.kt`, `ui/sftp/`): dual-pane browser — local device storage via the Storage Access Framework (`DocumentFile`) on one side, the connected remote server (sshj `SFTPClient`) on the other. Supports browsing, upload, download, delete, rename (via `SftpViewModel.renameRemote`), and create-directory. Transferring a file between two *saved remote servers* is implemented as a "transfer via device" stream-through (`SftpRepository.transferBetweenServers`) since SFTP has no server-to-server copy primitive; progress (bytes/percentage) and cancellation are supported for all transfers.
- **Full-screen terminal UI**: once connected, the "AndroSSH" title and the outer padding are hidden so the terminal grid and the extra-keys bar use nearly the whole screen; the terminal content starts directly below the system status bar with no app chrome above it. The palette lives in `ui/terminal/TerminalColors.kt` (a single place to tweak it): a dark desaturated teal background (`#0E3A44`), warm off-white monospace text (`#E8E4D8`) for anything the shell doesn't color itself, ANSI SGR colors where the emulator parses them, a solid light block cursor, and a slightly lighter teal strip behind the extra-keys bar. The connected `user@host` and a compact disconnect icon are shown as a small translucent overlay in the top-right corner that fades out ~3 seconds after the last tap - so it never permanently covers output - and fades back in whenever the terminal area is tapped. Tapping the terminal area also re-opens the on-screen keyboard, even if it was dismissed with the IME's own back button.
- **Stay connected in the background** (`ssh/SshSessionHolder.kt`, `ssh/SshForegroundService.kt`): the live shell, its output-reading coroutine and the terminal scrollback are owned by a process-scoped `SshSessionHolder` created in `AndroSshApplication`, *not* by `AndroSshViewModel`. Destroying the Activity/ViewModel (backgrounding, app switching, rotation) therefore cannot cancel the reader or close the socket: `AndroSshViewModel` merely observes the holder's `StateFlow` of terminal snapshots and, when it is recreated while a session is still open, restores the terminal screen with its scrollback instead of reconnecting. `SshForegroundService` (type `dataSync`) keeps the process alive for as long as a session is open and posts a single **ongoing** notification on a dedicated low-importance channel: it shows `user@host is running`, a ticking elapsed time (`setUsesChronometer` + `setWhen`), returns to the live terminal when tapped (`MainActivity` is `singleTask`, and the intent carries no profile id so it can never open a second connection), and offers a **Disconnect** action that closes the session, stops the service and dismisses the notification. Reconnecting or switching hosts updates that same notification, so none are duplicated or leaked. This requires the `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` and `POST_NOTIFICATIONS` permissions in `app/src/main/AndroidManifest.xml`; `MainActivity` requests `POST_NOTIFICATIONS` at runtime on Android 13+, and if the user denies it the session still runs in the background - only the notification (and with it tap-to-return/Disconnect) is not shown.
- **Home-screen widget** (`ui/widget/`): a classic `AppWidgetProvider` + `RemoteViews` widget (no Glance dependency required) that shows a pinned saved connection and opens the terminal for it directly on tap. `WidgetConfigureActivity` lets the user pick which saved connection to pin per widget instance.
- **Export/import of saved connections** (`data/backup/BackupManager.kt`, `ui/backup/BackupScreen.kt`): exports all saved profiles (including passwords) as JSON, encrypted with AES-256-GCM using a passphrase supplied at export time (PBKDF2-derived key; the plaintext JSON only ever exists in memory, never on disk). Import decrypts a file picked via the SAF file picker, validates it, and merges profiles into Room, asking the user to skip/overwrite/duplicate on host+username+port conflicts. A Room migration (`AndroSshDatabase.MIGRATION_1_2`) adds an `updatedAt` column used to track when a profile was last saved or imported.

## Non-goals for now

- Full xterm-compatible terminal emulation (256-color/truecolor, alternate screen, scrollback, bracketed paste - see the terminal emulator's known limitations above).
- Port forwarding.
- Key management UI / private-key authentication.
- Resumable SFTP transfers (marked with `TODO` where relevant).
- Proprietary or copied assets.

## Building

Use the Gradle wrapper from the repository root:

```sh
./gradlew assembleDebug
```

The first build requires access to Google Maven, Maven Central, and Gradle distributions so Android Gradle Plugin, AndroidX, Compose, Room, KSP, and sshj dependencies can be downloaded.

## License

Apache License 2.0. See [LICENSE](LICENSE).

