# Signed releases and updates

The app checks the companion site's `/update.json` and downloads the APK from
GitHub Releases. Android asks the rider to approve installation. Automatic
checks can be switched off in Settings; installation is blocked during a ride.

## One-time signing setup

Create and back up your release keystore yourself. Keep the same key for every
release: Android cannot update an APK signed with a different certificate.
Store these values in ignored `local.properties`, or supply the corresponding
environment variables to Gradle:

| Property | Environment variable |
| --- | --- |
| `release.storeFile` | `PELONOT_RELEASE_STORE_FILE` |
| `release.storePassword` | `PELONOT_RELEASE_STORE_PASSWORD` |
| `release.keyAlias` | `PELONOT_RELEASE_KEY_ALIAS` |
| `release.keyPassword` | `PELONOT_RELEASE_KEY_PASSWORD` |

Also configure `pelonot.webUrl` / `PELONOT_WEB_URL` to the companion site's HTTPS
URL. Without it the APK cannot discover updates. Cloud backup has its own
configuration in `cloud.properties`; check that configuration before shipping.

Changing from an existing debug APK to the first release APK requires exporting
a backup from Settings, uninstalling the debug copy, installing the release and
restoring the backup. Export before uninstalling. Database backups omit local
avatar photos. Future updates signed by the same release key retain app data.

## Each release

Requires Python 3, Android SDK build-tools, Java/Gradle, and the GitHub `gh` CLI
signed into an account that can publish to the repository. Start with a clean,
committed working tree. Neither script asks for nor prints signing passwords.

```sh
./tools/release.sh prepare 1.0.1 --notes 'More reliable updates and easier setup.'
```

This increments `versionCode`, sets `versionName`, builds the minified release,
runs the JVM suite, verifies its certificate and package, and prepares the APK,
checksum and manifest under `app/build/releases/1.0.1/`. An unsigned APK or an
APK signed with the Android debug certificate is refused. A failed preparation
restores `version.properties`.

Review the artifacts and test the APK on a disposable emulator using the same
release certificate. Commit `version.properties` and push that commit before
publishing, so GitHub can associate the release with its exact source:

```sh
./tools/release.sh publish 1.0.1
```

This is the publishing step. It re-verifies the APK, hash, version and source,
uploads the binary to GitHub Releases, then copies the prepared manifest to
`web/update.json`. It does not commit or push anything. Review and commit that
manifest, then `git push` to deploy it through the existing Cloudflare route.
Run `./web/check-deployed.sh` afterwards; it includes `update.json` in the
deployed-file check. Version 1.0.5 is published with a release-signed APK and a live manifest. The revised
system installer callbacks still need an in-place device rehearsal (30.7.3).

If GitHub reports an existing release, inspect it before retrying; the script
never overwrites a published binary. If an upload succeeded but writing the
manifest failed, compare the uploaded APK's SHA-256 with the prepared manifest
before copying that manifest into `web/` manually.
