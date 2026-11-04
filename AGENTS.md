# Compose Google Fonts

## Build locally

Use JDK 17, Xcode for iOS, and Android SDK platform 37. From this directory:

```sh
./gradlew jvmTest :gradle-plugin:check publishToMavenLocal
```

The standalone build includes all supported targets.

## Releasing

Install KPM:

```sh
npm install --global @composablehorizons/kpm@0.4.0
```

Set the release notes in `CHANGELOG.md`, then run from this directory:

```sh
./gradlew jvmTest :gradle-plugin:check publishToMavenLocal
kpm version patch --no-git-tag-version
release_version="$(kpm version)"
(cd gradle-plugin && kpm version "$release_version" --no-git-tag-version)
git add CHANGELOG.md package.yml gradle-plugin/package.yml
git commit -m "Release $release_version"
git push origin main
git tag "v$release_version"
git push origin "v$release_version"
```

The root and `gradle-plugin/package.yml` files own their Maven publishing metadata. Keep their versions equal before tagging a release. The tagged GitHub Actions release checks both versions, runs runtime and plugin tests, builds all publications, and publishes signed artifacts for both modules with KPM. After every artifact is downloadable from Maven Central, it creates a draft GitHub release from the changelog. Review and publish that draft separately.

Publishing uses the `maven-central` environment and the organization secrets `OSSRH_USERNAME`, `OSSRH_PASSWORD`, `SIGNING_KEY_ID`, `SIGNING_PASSWORD`, and `SIGNING_KEY`.

Bump the versions in the README's installation snippets when releasing.
