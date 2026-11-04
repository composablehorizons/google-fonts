# Compose Google Fonts

- Keep `CHANGELOG.md` current: add a one-line entry under `Unreleased` in the same commit as any user-visible change, as `Area: change (#PR, thanks @user)`. Leave out refactors, test-only changes and dependency bumps users cannot feel. Mark breaking changes with `**Breaking:**`.
- Releases run through the **Release** workflow in GitHub Actions: pick patch, minor or major. It releases the library and the Gradle plugin together at the same version. Never edit the versions in `package.yml` or `gradle-plugin/package.yml` by hand or push release tags. An empty `Unreleased` section stops the release.
- Before handing off changes, run `./gradlew jvmTest iosSimulatorArm64Test :gradle-plugin:check`. It needs JDK 17, Xcode and Android SDK platform 37.
