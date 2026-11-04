# Client Setup Guide

This guide shows how to configure **pacman** (Arch Linux), **apt** (Debian/Ubuntu/Kali), and **dnf** (Fedora) to use a local mirror served over **HTTP** or the local filesystem (`file://`).

> **Replace `192.168.1.22`** with the actual IP address or hostname of your mirror server.

---

## Mirror Structure

The examples below assume the following layout on the server (as created by JazzySync + nginx):

| Distribution | HTTP URL | Server path |
|---|---|---|
| **Arch Linux** | `http://192.168.1.22/archlinux/` | `/mnt/big/mirrors/archlinux/` |
| **Debian** | `http://192.168.1.22/debian/` | `/mnt/big/mirrors/debian/` |
| **Fedora** | `http://192.168.1.22/fedora/` | `/mnt/big/mirrors/fedora/` |
| **Kali Linux** | `http://192.168.1.22/kali/` | `/mnt/big/mirrors/kali/` |
| **Fedora Updates** | `http://192.168.1.22/fedora-updates/` | `/mnt/big/mirrors/fedora-updates/` |

If the mirror is mounted locally (e.g. via NFS), use `file://` with the local mount point instead.

---

## Arch Linux (pacman)

### Via HTTP

Create or overwrite `/etc/pacman.d/mirrorlist`:

```bash
sudo tee /etc/pacman.d/mirrorlist << 'EOF'
# Local JazzySync mirror
Server = http://192.168.1.22/archlinux/$repo/os/$arch
EOF
```

Update the package database:
```bash
sudo pacman -Syy
```

### Via local path (file://)

If the mirror is available locally (for example mounted at `/mnt/data6/mirrors/archlinux`):

```bash
sudo tee /etc/pacman.d/mirrorlist << 'EOF'
# Local JazzySync mirror (direct path)
Server = file:///mnt/data6/mirrors/archlinux/$repo/os/$arch
EOF
```

> **Note:** `pacman` supports `file://`, but the path must point to the repository root where `core/`, `extra/`, etc. are located.

---

## Debian / Ubuntu (apt)

### Via HTTP

Create `/etc/apt/sources.list.d/local-mirror.list`:

```bash
sudo tee /etc/apt/sources.list.d/local-mirror.list << 'EOF'
deb http://192.168.1.22/debian trixie main contrib non-free non-free-firmware
deb-src http://192.168.1.22/debian trixie main contrib non-free non-free-firmware
EOF
```

> Replace `trixie` with your Debian version (e.g. `bookworm`, `sid`).

Update the index:
```bash
sudo apt update
```

### Via local path (file://)

```bash
sudo tee /etc/apt/sources.list.d/local-mirror.list << 'EOF'
deb file:///mnt/data6/mirrors/debian trixie main contrib non-free non-free-firmware
deb-src file:///mnt/data6/mirrors/debian trixie main contrib non-free non-free-firmware
EOF
```

Update:
```bash
sudo apt update
```

> **Important:** when using `file://`, apt may complain about missing `Release.gpg`. If you synced without GPG signatures (`debmirror --ignore-release-gpg`), either configure apt with `Acquire::AllowInsecureRepositories "true"` or ensure `Release.gpg` is present.

---

## Kali Linux (apt)

Replace the contents of `/etc/apt/sources.list`:

```bash
sudo tee /etc/apt/sources.list << 'EOF'
deb http://192.168.1.22/kali kali-rolling main contrib non-free non-free-firmware
EOF
sudo apt update
```

Or via local path: `deb file:///mnt/data6/mirrors/kali kali-rolling main contrib non-free non-free-firmware`.

The same note about `Release.gpg` as for Debian applies.

---

## Fedora (dnf)

Fedora uses **two separate** repositories: `fedora` (release) and `updates` (updates).

### Via HTTP

Create `/etc/yum.repos.d/local-mirror.repo`:

```bash
sudo tee /etc/yum.repos.d/local-mirror.repo << 'EOF'
[fedora]
name=Fedora $releasever - $basearch - Local Mirror
baseurl=http://192.168.1.22/fedora/Everything/x86_64/os/
gpgcheck=0
enabled=1
skip_if_unavailable=1

[updates]
name=Fedora $releasever - $basearch - Updates - Local Mirror
baseurl=http://192.168.1.22/fedora-updates/Everything/x86_64/
gpgcheck=0
enabled=1
skip_if_unavailable=1
EOF
```

Disable the default internet repositories (so dnf does not fall back to them):
```bash
sudo dnf config-manager --set-disabled fedora fedora-modular updates updates-modular
```

Refresh the cache:
```bash
sudo dnf clean all
sudo dnf makecache
```

### Via local path (file://)

```bash
sudo tee /etc/yum.repos.d/local-mirror.repo << 'EOF'
[fedora]
name=Fedora $releasever - $basearch - Local Mirror
baseurl=file:///mnt/data6/mirrors/fedora/Everything/x86_64/os/
gpgcheck=0
enabled=1
skip_if_unavailable=1

[updates]
name=Fedora $releasever - $basearch - Updates - Local Mirror
baseurl=file:///mnt/data6/mirrors/fedora-updates/Everything/x86_64/
gpgcheck=0
enabled=1
skip_if_unavailable=1
EOF
```

Disable internet repos:
```bash
sudo dnf config-manager --set-disabled fedora fedora-modular updates updates-modular
```

Refresh:
```bash
sudo dnf clean all
sudo dnf makecache
```

---

## Quick Verification

### Arch
```bash
sudo pacman -Syy
pacman -Sl core | head -5
```

### Debian
```bash
sudo apt update
apt-cache policy | grep 192.168.1.22
```

### Fedora
```bash
sudo dnf repolist
sudo dnf list available --repo=fedora | head -5
```

---

## Security Notes

- `gpgcheck=0` in the examples disables RPM GPG verification. This simplifies setup for a private mirror, but **do not use it** for a public mirror unless you have properly imported and configured the distribution GPG keys.
- Debian `apt` requires a signed `Release` file by default. If you sync with `debmirror --ignore-release-gpg`, configure apt accordingly or sync `Release.gpg` together with the other files.

---

## See Also

- [Docker Mirror Setup](DOCKER_SETUP.md) — how to run the HTTP + rsync server with Docker Compose
