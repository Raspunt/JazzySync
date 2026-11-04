# JazzySync — Linux Mirror Sync

A CLI tool (`jazzy`) for managing local Linux distribution mirrors through a single TOML configuration. Supports **Arch Linux**, **Debian**, **Fedora**, and **Kali Linux**.

> **What it actually is:** a Java orchestrator that runs `rsync` (and optionally `debmirror`) based on your config. It does **not** generate repository metadata, merge repositories, or fix broken upstream mirrors.

---

## What It Does (and Doesn't)

### ✅ It does
- Reads one TOML config and runs the right external tool (`rsync` / `debmirror`) for each distribution
- Filters what to sync: Arch repos, Debian sections, Fedora editions
- Checks integrity of downloaded packages after sync (`zstd`, `dpkg-deb`, `rpm`)
- Removes corrupt packages and re-downloads them (`jazzy fix`)
- Supports per-distribution mirror overrides (`baseUrl` inside distro config)
- Supports custom distribution entries (e.g. `fedora-updates`) via `family`
- Bundles a minimal JRE via `jlink` — no Java installation required on target systems

### ❌ It does NOT
- Generate or repair `repodata/`, `Release` files, or other repository metadata
- Merge `releases` and `updates` into a single repository (Fedora requires separate entries)
- Work with HTTP/FTP mirrors — **rsync only**
- Fix an upstream mirror that is incomplete (e.g. missing `repodata/`)
- Add new distribution support without writing Java code (see [For Developers](#for-developers))

---

## How It Works

```
config.toml ──► ConfigManager ──► MirrorFactory ──► IMirror
                                                    ├── ArchMirror ──► rsync
                                                    ├── DebianMirror ──► debmirror (or rsync)   (debian, kali)
                                                    └── FedoraMirror ──► rsync
```

1. You write `~/.config/jazzy/config.toml`
2. `jazzy sync` reads the config and creates a mirror object for each enabled distribution
3. Each mirror builds the appropriate external command (`rsync ...` or `debmirror ...`)
4. Output is logged to both stdout and `~/.cache/jazzy/mirror-sync.log`

---

## System Requirements

### Runtime dependencies

| Mirror | Required tools |
|--------|---------------|
| **Arch Linux** | `rsync` (`zstd` optional for verify) |
| **Debian** | `rsync` (`debmirror`, `dpkg-deb`/`ar` optional) |
| **Fedora** | `rsync` (`rpm` optional for verify) |
| **Kali Linux** | same as Debian |

### Build dependencies (only if building from source)

- **JDK 21** or newer
- **Maven 3.9** or newer

---

## Installation

### Option 1: Build from source

#### Prerequisites

You need **JDK 21+** and **Maven 3.9+**.  
If your distribution ships older versions (e.g. Debian 12 has JDK 17), install a modern JDK first.

**Debian / Ubuntu (via Eclipse Temurin)**

```bash
sudo apt install -y wget apt-transport-https gnupg
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo gpg --dearmor -o /usr/share/keyrings/adoptium.gpg
echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update
sudo apt install temurin-21-jdk

# Ensure JAVA_HOME points to the new JDK
echo 'export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64' >> ~/.bashrc
source ~/.bashrc
java -version   # should print OpenJDK 21
```

**Any distro (via SDKMAN)**

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.5-tem
sdk install maven 3.9.9
sdk default java 21.0.5-tem
```

#### Build

```bash
git clone https://github.com/Raspunt/JazzySync.git
cd JazzySync
make dist
./dist/jazzy list
```

> **Note for Temurin users:** if `make dist` fails with `jdeps: not found` or `jlink: not found`, the Makefile may be looking under `/usr/lib/jvm/java-21-openjdk/`. Either export `JAVA_HOME` explicitly (`export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64`) or create compatibility symlinks:
> ```bash
> sudo mkdir -p /usr/lib/jvm/java-21-openjdk/bin
> sudo ln -sf /usr/lib/jvm/temurin-21-jdk-amd64/bin/jdeps /usr/lib/jvm/java-21-openjdk/bin/jdeps
> sudo ln -sf /usr/lib/jvm/temurin-21-jdk-amd64/bin/jlink /usr/lib/jvm/java-21-openjdk/bin/jlink
> ```

The `make dist` command:

1. Compiles the project with Maven
2. Analyzes required JDK modules via `jdeps`
3. Builds a minimal bundled JRE (~40 MB) via `jlink`
4. Creates a portable `dist/` directory

### Option 2: System-wide install

```bash
sudo make install
jazzy --help
```

### Option 3: Local install (no sudo)

```bash
make install-local
# ensure ~/.local/bin is in your PATH
jazzy --help
```

> **Note:** The bundled JRE is built for **x86_64 (amd64)** Linux. For ARM or other architectures you must build from source on the target machine.

---

## Documentation

- [Client Setup](docs/CLIENT_SETUP.md) — configure **pacman**, **apt**, and **dnf** to use your local mirror
- [Docker Mirror Setup](docs/DOCKER_SETUP.md) — run HTTP + rsync mirror server with Docker

---

## Configuration

Configuration file: `~/.config/jazzy/config.toml`

Created automatically with sensible defaults on the first run.

### Recommended Fedora configuration

Fedora **releases** and **updates** are **two independent repositories** with separate `repodata/`. You **must** define them as separate entries:

```toml
baseUrl = "rsync://mirror.yandex.ru/"
targetDir = "/mnt/big/mirrors/"
logDir = "~/.cache/jazzy"

[distros.debian]
sourcePath = "debian/"
enabled = true
repos = ["main", "contrib", "non-free", "non-free-firmware"]

[distros.arch]
sourcePath = "archlinux/"
enabled = true
repos = ["core", "extra", "multilib"]

[distros.fedora]
sourcePath = "fedora/linux/releases/44/"
enabled = true
repos = ["Everything"]
excludes = [
    "Everything/aarch64",
    "Everything/source",
    "Everything/x86_64/iso",
    "Everything/x86_64/images",
    "Everything/x86_64/debug",
]

[distros.fedora-updates]
sourcePath = "fedora/linux/updates/44/"
family = "fedora"
enabled = true
repos = ["Everything"]
excludes = ["Everything/aarch64", "Everything/x86_64/debug"]

[distros.kali]
sourcePath = "kali/"
baseUrl = "rsync://mirror.truenetwork.ru/"   # mirror.yandex.ru has no Kali
enabled = true
repos = ["main", "contrib", "non-free", "non-free-firmware"]
```

### Configuration fields

| Field | Description | Default |
|-------|-------------|---------|
| `baseUrl` | Base rsync URL for all mirrors | `rsync://mirror.yandex.ru/` |
| `targetDir` | Local root directory for mirrors | `~/mirrors` |
| `logDir` | Directory for log files | `~/.cache/jazzy` |

#### Per-distribution settings (`distros.<name>`)

| Field | Description | Applies to |
|-------|-------------|------------|
| `sourcePath` | Path on the rsync server (single source) | All |
| `sourcePaths` | List of paths for multi-source sync | Any distro |
| `enabled` | Enable or disable this distribution | All |
| `repos` | Whitelist/filters (Arch: repo names; Debian/Kali: sections; Fedora: editions) | All |
| `excludes` | Paths to exclude from sync (supports subdirectories inside included repos) | All |
| `baseUrl` | Override global `baseUrl` for this distribution only | All |
| `family` | Mirror family/provider to use (e.g. `"fedora"` for custom distro names) | All |
| `properties` | Extra key-value settings (`dist`, `arch`, `section`) | Debian, Kali |

### Per-distribution mirror override

If you want to sync a specific distribution from a different mirror, set `baseUrl` inside the distro config. It overrides the global `baseUrl` for that distribution only:

```toml
baseUrl = "rsync://mirror.yandex.ru/"

[distros.arch]
sourcePath = "archlinux/"
enabled = true

[distros.debian]
sourcePath = "debian/"
baseUrl = "rsync://ftp.de.debian.org/"
enabled = true
```

In this example Arch syncs from Yandex, while Debian syncs from `ftp.de.debian.org`.

### Arch repository whitelist

Use `repos` to sync only specific repositories:

```toml
[distros.arch]
repos = ["core", "extra", "multilib"]
```

Available repositories in a typical Arch mirror:
`core`, `extra`, `multilib`, `core-testing`, `extra-testing`, `multilib-testing`, `gnome-unstable`, `kde-unstable`

The `pool/` directory and metadata files (`lastupdate`, `lastsync`) are always synced regardless of the whitelist.

### Debian section whitelist

Use `repos` to sync only specific sections:

```toml
[distros.debian]
repos = ["main", "contrib"]
```

This overrides the `section` field passed to `debmirror`.

### Fedora edition whitelist and exclusions

Fedora `repos` selects **editions** (`Everything`, `Workstation`, `KDE`, `Silverblue`, etc.) at the top level of the release tree. Use `excludes` to prune unwanted subdirectories (architectures, ISOs, debug symbols):

```toml
[distros.fedora]
sourcePath = "fedora/linux/releases/44/"
repos = ["Everything"]
excludes = [
    "Everything/aarch64",
    "Everything/source",
    "Everything/x86_64/iso",
    "Everything/x86_64/images",
    "Everything/x86_64/debug",
]
```

**Why this matters:** `Everything/x86_64/os/` (~124 GB) is the actual DNF repository. Without exclusions you also get `iso/`, `images/`, and `debug/` — easily **600+ GB** extra.

### Custom distributions with `family`

You can define multiple mirrors of the same family under different names. This is required for Fedora because **releases** and **updates** are separate repositories with independent `repodata/`.

```toml
[distros.fedora]
sourcePath = "fedora/linux/releases/44/"
enabled = true
repos = ["Everything"]

[distros.fedora-updates]
sourcePath = "fedora/linux/updates/44/"
family = "fedora"
enabled = true
repos = ["Everything"]
```

Both will appear in `jazzy list` and sync with `jazzy sync all`.

---

## Distribution-Specific Notes

### Arch Linux
- Update detection: compares `lastupdate` timestamp file before running rsync
- Integrity check: `zstd -t <file>` on changed packages after sync
- Post-sync verification is incremental (only new/modified packages)

### Debian
- If `debmirror` is installed, it is used automatically. Otherwise falls back to plain `rsync`
- Integrity checks: `dpkg-deb -I` or `ar x` + `tar tf`

### Kali Linux
- Uses the same code path as Debian (`debmirror` or `rsync` fallback, `.deb` integrity checks)
- Defaults: `dist = "kali-rolling"`, `arch = "amd64"`, sections `main, contrib, non-free, non-free-firmware`
- Override via `[distros.kali.properties]`, e.g. `dist = "kali-last-snapshot"`
- `mirror.yandex.ru` does not carry Kali — set `baseUrl` to a mirror that does (e.g. `rsync://mirror.truenetwork.ru/`, module `kali`)
- Without `debmirror` the rsync fallback copies the **whole** archive (all architectures and dists), so installing `debmirror` is strongly recommended

### Fedora
- **No pre-sync update detection** — always runs rsync (but skips if `repomd.xml` is unchanged)
- `repos` acts as `rsync --include` filters on the top-level directories inside `sourcePath`
- `excludes` are applied **inside** included directories (e.g. exclude `Everything/aarch64` while keeping `Everything/x86_64`)
- Integrity check: `rpm -K --nosignature`
- **Important:** if your upstream mirror does not provide `repodata/` (e.g. `mirror.ps.kz`), DNF will not work. Switch to a full mirror like `mirror.yandex.ru`.

---

## Limitations

- **No metadata generation.** If `repodata/` is missing on the upstream mirror, `jazzysync` cannot create it. DNF will fail.
- **No repository merging.** You cannot merge Fedora `releases/` and `updates/` into one directory. They must remain separate.
- **rsync only.** No HTTP, FTP, or BitTorrent support.
- **No built-in scheduling.** Use `cron` or `systemd timers` for periodic sync.
- **x86_64 JRE only.** The bundled JRE is pre-built for amd64 Linux.

---

## CLI Usage

```
Usage: jazzy [-hV] [-t=<targetDir>] <COMMAND> [TARGET]

Arguments:
  <COMMAND>  Command to execute: sync, verify, check, fix, status, list
  [TARGET]   Distribution to process, or all (default: all)

Options:
  -t, --target-dir=<targetDir>  Override target directory for mirrors
  -h, --help                    Show help
  -V, --version                 Show version
```

### Commands

| Command | Description | Safe? |
|---------|-------------|-------|
| `sync` | Synchronize mirror(s) with upstream | ⚠️ Modifies files |
| `check` | Dry-run; show what would change | ✅ Read-only |
| `verify` | Verify integrity of local packages | ✅ Read-only |
| `fix` | Remove corrupt packages and re-download | ⚠️ Deletes bad files |
| `status` | Show mirror status, size, last sync | ✅ Read-only |
| `list` | List configured distributions | ✅ Read-only |

### Examples

```bash
# Sync all enabled mirrors
jazzy sync

# Sync only Arch Linux
jazzy sync arch

# Check all mirrors for updates (dry-run)
jazzy check

# Verify integrity of Debian packages
jazzy verify debian

# Fix corrupt Arch packages
jazzy fix arch

# Show status table
jazzy status

# List distributions
jazzy list
```

---

## For Developers

### Architecture

```
IMirror (interface)
├── ArchMirror    → RsyncStrategy
├── DebianMirror  → DebmirrorStrategy (or RsyncStrategy fallback)  — used by debian and kali families
└── FedoraMirror  → RsyncStrategy
```

- **Java acts as an orchestrator** — launches external tools via `ProcessBuilder`
- **Template method pattern** — `AbstractMirror` defines common logic; subclasses provide distribution-specific details
- **Parallel verification** — `ExternalToolChecker` uses `ExecutorService` with all CPU cores

### Adding a new distribution

1. Implement `MirrorProvider` (see `FedoraMirrorProvider` as example)
2. Register it in `MirrorFactory.registerDefaults()`
3. Set `family` in config to match `provider.getFamily()`
4. Rebuild with `make dist`

---

## Makefile Targets

| Target | Description |
|--------|-------------|
| `make all` | Build fat-jar (default) |
| `make dist` | Build portable distribution with bundled JRE |
| `make package` | Build jar only |
| `make compile` | Compile sources |
| `make test` | Run tests |
| `make clean` | Clean build artifacts |
| `make verify` | Full verification (clean + test + package) |
| `make install` | System-wide install to `/opt/jazzysync` |
| `make install-local` | Local install to `~/.local/jazzysync` |
| `make uninstall` | Remove system installation |
| `make uninstall-local` | Remove local installation |

---

## Logs

All operations are logged to both **stdout** and a log file:

```
~/.cache/jazzy/mirror-sync.log
```

Each mirror also maintains its own sync logs in the mirror directory:

```
/mnt/big/mirrors/archlinux/.logs/sync-YYYYMMDD-HHMMSS.log
```

---

## License

Licensed under the Apache License, Version 2.0.
See [LICENSE](LICENSE) and [NOTICE](NOTICE) for details.

JazzySync is a trademark of Raspunt.
