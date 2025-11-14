# Cargo NDK for Android projects
[![CI](https://github.com/gemwalletcom/cargo-ndk-android-gradle/actions/workflows/ci.yml/badge.svg)](https://github.com/gemwalletcom/cargo-ndk-android-gradle/actions/workflows/ci.yml)

Allows building Rust code via `cargo ndk` command in android projects.

It is somewhat similar to the Mozilla
[Rust Android Gradle Plugin](https://github.com/mozilla/rust-android-gradle),
however, it uses [`cargo ndk`](https://github.com/bbqsrc/cargo-ndk)
to find the right  `linker` and `ar` and
build the project. Also, it allows configuring rust release profile (`dev` vs `release`)
for each gradle `buildType`. Actually, any options can be configured per gradle `buildType`,
it works similar to `android` configuration.

> **Maintained fork**: the original plugin has been inactive for a while, so this
> repository publishes a compatible fork under the
> `com.gemwallet.rust.cargo-ndk-android` id.

## Usage

Add the plugin via the Gradle Plugins DSL (Groovy example shown here—see
[Gradle docs](https://docs.gradle.org/current/userguide/plugins.html#sec:plugins_block) for Kotlin DSL):

```groovy
// settings.gradle
pluginManagement {
    repositories {
        mavenLocal()
        maven {
            url = uri("https://maven.pkg.github.com/gemwalletcom/cargo-ndk-android-gradle")
            credentials {
                username = System.getenv("GITHUB_USER") ?: findProperty("github.user")
                password = System.getenv("GITHUB_TOKEN") ?: findProperty("github.token")
            }
        }
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
```

> GitHub Packages requires authentication. Create a personal access token with
> the `read:packages` scope (or use a fine-grained token), expose it via
> `GITHUB_TOKEN`/`GITHUB_USER`, or store it in Gradle properties referenced
> above. You can drop the GitHub repository entirely if you only consume the
> plugin from `mavenLocal()`.

```groovy
// build.gradle in the root project
plugins {
    id "com.gemwallet.rust.cargo-ndk-android" version "0.4.0" apply false
}
```

If you prefer `buildscript {}` blocks instead of the plugins DSL, depend on
`com.gemwallet.rust:plugin:0.4.0` from the same repository.

In your _project's_ `build.gradle`, `apply plugin` and add the `cargoNdk`
configuration (optionally):

```groovy
android { ... }

apply plugin: "com.gemwallet.rust.cargo-ndk-android"

// The following configuration is optional and works the same way by default
cargoNdk {
    buildTypes {
        release {
            buildType = "release"
        }
        debug {
            buildType = "debug"
        }
    }
}
```

Install rust toolchains:

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
```

Install [`cargo-ndk`](https://github.com/bbqsrc/cargo-ndk):

```bash
cargo install cargo-ndk
```

If you already have `cargo-ndk`, please make sure it is up to date:

```bash
cargo install --force cargo-ndk
```

Next, you need to specify path to NDK via either setting
[`ANDROID_NDK_HOME`](#android-ndk-home) env variable, or [`ndk.dir`](#ndk-dir)
property in `local.properties`.

This plugin adds the following targets: `buildCargoNdkDebug`,
`buildCargoNdkRelease`, however, they should be run automatically by building
your android project as usual. So:

1. `./gradlew assembleDebug` will build `dev` (`debug`) profile.
   Depends on, and so will run `buildCargoNdkDebug`.
1. `./gradlew assembleRelease` will build `release` profile.
   Depends on, and so will run `buildCargoNdkRelease`.

## Configuration

### All options

```groovy
cargoNdk {
    // List of all targets
    // By default: ["arm64", "arm", "x86", "x86_64"]
    targets = ["arm64", "arm", "x86", "x86_64"]

    // Path to directory with rust project
    // By default: "app/src/main/rust"
    module = "../rust"

    // Absolute path to the cargo binary if PATH is not propagated to Gradle
    // By default: tries ~/.cargo/bin/cargo and then falls back to "cargo"
    cargoExecutable = System.getProperty("user.home") + "/.cargo/bin/cargo"

    // Path to rust 'target' dir (the dir where build happens), relative to module
    // By default: "target"
    targetDirectory = "target"

    // List of all library names to copy from target to jniLibs
    // By default parses Cargo.toml and gets all dynamic libraries
    librariesNames = ["libmy_library.so"]

    // The apiLevel to build link with
    // By default: android.defaultConfig.minSdkVersion
    apiLevel = 19

    // Whether to build cargo with --offline flag
    // By default: false
    offline = true

    // The rust profile to build
    // Possible values: "debug", "release", "dev" (an alias for "debug")
    // By default: "release" for release gradle builds,
    //             "debug"   for debug   gradle builds
    buildType = "release"

    // Extra arguments to pass to cargo command
    // By default: []
    extraCargoBuildArguments = ["--offline"]

    // Extra environment variables
    // By default: [:]
    extraCargoEnv = ["foo": "bar"]

    // Whether to pass --verbose flag to cargo command
    // By default: false
    verbose = true
}
```

As it was already mentioned, any of those options can be configured
separately for each buildTypes:

```groovy
cargoNdk {
    apiLevel = 19  // default

    buildTypes {
        debug {
            apiLevel = 26  // overwrite for debug
        }
    }
}
```

### ANDROID_NDK_HOME env variable

<a id="android-ndk-home"></a>

One way to specify path to NDK root for `cargo-ndk` is to set
`ANDROID_NDK_HOME`. In the most common **Linux** case, you would need to set
it in both `~/.bashrc` (for terminal usage) and `~/.profile`
(for Android Studio usage); read `~/.bashrc` vs `~/.profile`. I usually have
a separate `.env_setup` file, which is included in both `~/.bashrc` and
`~/.profile`.

Usually, after updating `~/.profile`, you need to relogin to see the effect.

Read about `~/.bashrc` vs `~/.profile`.

### `ndk.dir` in `local.properties`

<a id="ndk-dir"></a>

Instead of specifying `ANDROID_NDK_HOME` env variable, you can set `ndk.dir`
in `local.properties` file.

### Specify target via gradle property

You can also compile only one target by specifying the `rust-target` property to gradle.
E.g. to build only `arm64` target you can: `gradle assembleDebug -Prust-target=arm64`.
It can be useful during development to speed up each build
via not rebuilding targets that are not used during testing.

## Troubleshooting

### Rust error messages

To get the full error message in Android Studio - select the `build` tab at
the bottom of Android Studio, and then select the topmost error group
(`Build: failed at`); it should show you the full log.

### Cargo binary cannot be found inside Android Studio

Some recent Android Studio releases do not inherit your shell `PATH` when they
are launched from the Dock/Finder. As a result Gradle might fail with
`Failed to run 'cargo --version'` even though `cargo` is installed.

The plugin now tries to detect `cargo` automatically by checking
`~/.cargo/bin/cargo` and finally falling back to the plain `cargo` executable in
`PATH`. If that still fails, either start Android Studio from a terminal
(`open -na "Android Studio.app"`), or set `cargoNdk.cargoExecutable` to the
absolute path of your `cargo` binary. You can expand the user home dynamically
to avoid hardcoding usernames:

```groovy
cargoNdk {
    cargoExecutable = System.getProperty("user.home") + "/.cargo/bin/cargo"
}
```

The property applies globally and can also be overridden per `buildType`.

## Publishing this fork

Artifacts are uploaded to [GitHub Packages](https://github.com/gemwalletcom/cargo-ndk-android-gradle/packages)
via the `Release` workflow. To publish a new version:

1. Update `plugin/build.gradle` (and related docs) with the desired version.
2. Merge the change into your release branch and create a git tag following the
   `v*` pattern (e.g. `git tag v0.4.1 && git push origin v0.4.1`). The workflow
   automatically runs on tag pushes.
3. Alternatively, trigger the workflow manually from GitHub → Actions →
   `Release` (`Run workflow`) to publish from any ref.

The workflow runs tests and executes
`./gradlew :plugin:publishAllPublicationsToGitHubPackagesRepository` using the
repository-provided `GITHUB_TOKEN`. Consumers need `read:packages` access and
must authenticate (see the configuration snippet above) to resolve the plugin.
