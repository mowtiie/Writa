<div align="center">

<img src="metadata/en-US/images/icon.png" alt="Writa icon" width="160" height="160">

# Writa

**Your notes. Your server. Your rules.**

Writa is a simple, open-source notes app that connects to your own Supabase backend instead of a company's servers. Whether you're using a free Supabase Cloud project or a self-hosted instance, your notes live where you decide.

![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)

</div>

## Features

* **Bring your own backend:** Connect to any Supabase project, Cloud or self-hosted, with your project URL and publishable key. Switch servers anytime from Settings.
* **Private per account:** Each account only sees its own notes, enforced by Row Level Security in your database.
* **Search and sort:** Filter by title or content, and sort by newest, oldest, or title.
* **Tags:** Label notes and filter the list by one or more tags.
* **Archive:** Move notes you want to keep out of the main list, and bring them back any time.
* **Works offline:** Your notes are saved on the device (encrypted), so the list opens instantly. You can read and edit without a connection, and changes upload when you are back online. If the same note was changed elsewhere, your version is kept as a separate "conflict copy" so nothing is lost.
* **Pin and trash:** Pin notes to keep them at the top. Deleted notes go to the trash first, where you can restore them or delete them for good.
* **Backup:** Export your notes to a JSON file and import them back.
* **Themes and privacy:** Light, dark, and dynamic colors, plus an optional screen privacy mode that blocks screenshots.

## Screenshots

<div align="center">
  <img src="metadata/en-US/images/phoneScreenshots/screenshot-1.png" alt="Notes list" width="30%" />
  <img src="metadata/en-US/images/phoneScreenshots/screenshot-2.png" alt="Note editor" width="30%" />
  <img src="metadata/en-US/images/phoneScreenshots/screenshot-3.png" alt="Filtering by tag" width="30%" />
  <img src="metadata/en-US/images/phoneScreenshots/screenshot-4.png" alt="Search" width="30%" />
  <img src="metadata/en-US/images/phoneScreenshots/screenshot-5.png" alt="Dark theme" width="30%" />
  <img src="metadata/en-US/images/phoneScreenshots/screenshot-6.png" alt="Settings" width="30%" />
</div>

## Getting Started

You need Android 10 (API 29) or later, and a Supabase project that is reachable over **HTTPS**: either a free [Supabase Cloud](https://supabase.com) project or your own self-hosted instance.

1. Create a project at [supabase.com](https://supabase.com) (or run your own).
2. In the **SQL Editor**, run [`schema.sql`](schema.sql) to create the `notes` table, its Row Level Security policies and the edit-time trigger. The script is safe to run again. If Writa shows an "Update your database" screen, run it again and re-check.
3. Under **Authentication → Sign In / Providers**, enable **Email**. For the easiest setup, turn off **Confirm email**; otherwise new users must confirm by email first, which needs working email delivery.
4. Copy your **Project URL** and **publishable key** from the project's **Connect** dialog (or **Settings → API Keys**). Legacy `anon` keys still work but are being deprecated by Supabase, so prefer the publishable key (`sb_publishable_...`).
5. Open Writa, enter the URL and key on the setup screen, and create an account.

**Only use the publishable (or legacy anon) key.** Never paste a secret or `service_role` key into the app: it bypasses Row Level Security.

To use another server later, go to **Settings → Change server**.

## Verification

APK releases on GitHub are signed using my key. They can
be verified using
[apksigner](https://developer.android.com/studio/command-line/apksigner.html#options-verify):

```
apksigner verify --print-certs --verbose writa-vX.X.apk
```

The output should look like:

```
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v3.2 scheme (APK Signature Scheme v3.2): false
Verified using v4 scheme (APK Signature Scheme v4): false
```

The certificate fingerprints should correspond to the ones listed below:

```
Owner: CN=Mowtiie, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown
Issuer: CN=Mowtiie, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown
Serial number: 8a256fdcdde50069
Valid from: Wed Jun 10 22:57:23 PST 2026 until: Sun Oct 26 22:57:23 PST 2053
Certificate fingerprints:
         SHA1: 56:4E:2C:DB:E4:06:C9:EC:15:E6:BC:D9:0A:88:38:72:8B:FB:13:20
         SHA256: 8B:67:51:F3:C3:31:85:63:5F:98:95:30:B6:C0:73:A1:39:7B:3D:41:2B:EF:AE:69:06:A2:EB:58:45:D2:DE:63
```

**Warning:** Only install Writa APKs signed with the key above. Verifying the signature confirms you're running a genuine, unmodified build.

### PGP Signing

As an additional layer on top of the Android signature above, each release is also signed with my PGP key. While `apksigner` confirms the APK itself is intact, a PGP signature confirms that *I* am the one who published this specific file to GitHub, an independent check that doesn't rely on GitHub's account security alone.

**Public key fingerprint:**
```
9EA2 8F46 7802 5092 7643 1B69 42B5 FA42 AA63 90E1
```

Download and import the key from my website:

```
curl -O https://mowtiie.cc/PGP_PUBLIC_KEY.asc
gpg --import PGP_PUBLIC_KEY.asc
```

After importing, confirm the fingerprint printed by GPG matches the one listed above. That match is what actually establishes trust, not the import step itself. Fetching the key over HTTPS from a domain you already trust is arguably a stronger anchor than a keyserver, since keyservers accept uploads from anyone and don't vouch for identity.

Each release includes a detached `.asc` signature alongside the APK. Verify a downloaded release with:

```
gpg --verify writa-vX.X.apk.asc writa-vX.X.apk
```

A valid signature looks like:

```
Good signature from "Mowtiie <mowtiie.dev@gmail.com>"
```

**Note:** PGP signing is a supplementary trust measure, not a substitute for the `apksigner` check above. Verify both if you want the highest confidence that a release is genuine and unmodified.

## Mapping Files

Each release on GitHub includes a `mapping-<version>.txt` file alongside the APK. This file is needed to deobfuscate stack traces from crash reports: match the file to the version shown in the crash report header and use it with `retrace` from the Android SDK.

## License

This project is licensed under the GNU General Public License v3.0. See the
[LICENSE](LICENSE) file for details.
