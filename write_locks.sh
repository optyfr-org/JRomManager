#!/bin/bash
set -e

# Root build: writes gradle.lockfile + settings-gradle.lockfile
./gradlew dependencies --refresh-dependencies --write-locks

# buildSrc is a separate build: writes buildSrc/gradle.lockfile + buildSrc/settings-gradle.lockfile
./gradlew --project-dir buildSrc dependencies --refresh-dependencies --write-locks
