# AndroSSH

AndroSSH is a brand-new, original, open-source Android SSH client app skeleton written in Kotlin. It is a clean-room project: it is not derived from JuiceSSH code, decompiled APK output, proprietary assets, or any other closed-source application code. Similar Android terminal clients may inspire broad feature and UX goals only; all implementation in this repository is original and uses open-source libraries.

## Goals

- Modern Android app foundation using Kotlin and Jetpack Compose.
- Package ID: `com.androssh.app`.
- Display name: `AndroSSH`.
- Minimum SDK 24, compile/target SDK 36.
- MVVM-ish structure with separate UI, ViewModel, data/repository, and SSH layers.
- SSH connectivity scaffolded through [`sshj`](https://github.com/hierynomus/sshj) (Apache-2.0).
- Local host profiles stored with Room.
- Sensitive password values stored outside Room using AndroidX Security Crypto encrypted preferences backed by Android Keystore.

## Current project structure

```text
app/src/main/java/com/androssh/app/
├── data/       # Room entities, DAO, database, repository, encrypted credential store
├── ssh/        # SSHJ connection manager and active shell session wrapper
├── ui/         # Jetpack Compose screens
└── viewmodel/  # UI state and ViewModel orchestration
```

## Scaffolded features

- Connection list screen for saved SSH host profiles.
- Add/edit connection form with host, port, username, password auth, and private-key placeholders.
- Basic terminal screen with text output and input plumbing for an SSH shell channel.
- Password authentication path wired first.
- Private key authentication intentionally stubbed for later work.
- Known-hosts based SSH host-key verification foundation; a trust-on-first-use or host-key management UI is a follow-up task.

## Non-goals for the initial skeleton

- Full ANSI/VT100 terminal emulation.
- Port forwarding.
- SFTP browser.
- Key management UI.
- Proprietary or copied assets.

## Building

Use the Gradle wrapper from the repository root:

```sh
./gradlew assembleDebug
```

The first build requires access to Google Maven, Maven Central, and Gradle distributions so Android Gradle Plugin, AndroidX, Compose, Room, KSP, and sshj dependencies can be downloaded.

## License

Apache License 2.0. See [LICENSE](LICENSE).
