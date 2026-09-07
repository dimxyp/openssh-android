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
├── ssh/          # SSHJ connection manager, shell session, and SFTP session wrappers
├── terminal/     # TerminalEmulator: original VT100/ANSI-subset parser + screen buffer (no Compose deps, unit-testable)
├── ui/
│   ├── backup/   # Export/import Compose screen
│   ├── sftp/     # Dual-pane local/remote SFTP browser Compose screen
│   ├── terminal/ # TerminalGrid (renders the emulator's screen buffer) + ExtraKeysBar + terminal key mappings
│   └── widget/   # Home-screen widget provider + configuration activity
└── viewmodel/    # UI state and ViewModel orchestration (main, SFTP, backup)
```

## Features

- Connection list screen for saved SSH host profiles, with add/edit/delete and per-connection SFTP access.
- Password authentication path wired first; private key authentication intentionally stubbed for later work.
- Known-hosts based SSH host-key verification foundation; a trust-on-first-use or host-key management UI is a follow-up task.
- **Real interactive terminal emulator** (`terminal/TerminalEmulator.kt`, `ui/terminal/TerminalGrid.kt`): a from-scratch, original VT100/ANSI-subset parser and in-memory screen buffer (rows/cols grid of styled characters) - not derived from JuiceSSH, Termux, or any other terminal emulator. It supports printable characters, `\r`/`\n`/backspace/tab, cursor positioning (`ESC[<row>;<col>H`), relative cursor movement (`ESC[A/B/C/D`), erase in line/display (`ESC[K`, `ESC[J` and their `0`/`1`/`2` variants), and basic SGR attributes (`ESC[...m` bold + the 8 standard foreground/background colors). Typing is live: every keystroke from a hidden input capture is streamed straight to the shell channel as it's typed (no "Send" button), and `AndroSshViewModel` batches/throttles screen-buffer updates (~20 fps) so fast output (e.g. `top`) doesn't recompose the UI per byte. Known limitations: fixed screen size (not yet resized to the device's actual font metrics/orientation), no scrollback buffer, and no support for alternate-screen mode, 256-color/truecolor SGR codes, or bracketed paste.
- **Extra-keys bar** (`ui/terminal/ExtraKeysBar.kt`): a from-scratch Compose component with Esc/Tab/Ctrl/Alt/Home/End/PgUp/PgDn/arrow keys, a toggle-able function-keys row, and a swipe gesture that flips the bar into a text-edit/selection mode (select all/copy/cut/paste/undo/redo). Keys send raw ANSI escape sequences / ASCII control codes (`TerminalKeys.kt`) straight to the SSH shell channel, and the key set is data-driven so more keys can be added later.
- **SFTP file transfer** (`data/sftp/`, `viewmodel/SftpViewModel.kt`, `ui/sftp/`): dual-pane browser — local device storage via the Storage Access Framework (`DocumentFile`) on one side, the connected remote server (sshj `SFTPClient`) on the other. Supports browsing, upload, download, delete, rename (via `SftpViewModel.renameRemote`), and create-directory. Transferring a file between two *saved remote servers* is implemented as a "transfer via device" stream-through (`SftpRepository.transferBetweenServers`) since SFTP has no server-to-server copy primitive; progress (bytes/percentage) and cancellation are supported for all transfers.
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

