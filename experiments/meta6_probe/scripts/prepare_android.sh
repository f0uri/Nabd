#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Generate in a temporary directory so flutter create cannot add counter-app
# widget tests, overwrite main.dart, or change our supplied pubspec.yaml.
generated=$(mktemp -d)
trap 'rm -rf "$generated"' EXIT
flutter create --platforms=android --org app.nabd --project-name meta6_probe --no-pub "$generated/scaffold"
rm -rf android
cp -R "$generated/scaffold/android" android
cp "$generated/scaffold/.metadata" .metadata
cp platform/AndroidManifest.xml android/app/src/main/AndroidManifest.xml
cp platform/app.build.gradle.kts android/app/build.gradle.kts
mkdir -p android/app/src/main/res/drawable android/app/src/main/res/raw
cp platform/notification_icon.xml android/app/src/main/res/drawable/probe_message.xml
cp platform/keep_resources.xml android/app/src/main/res/raw/meta6_keep.xml
# Exact supplied launcher artwork, resized only; legacy + adaptive Android icons.
cp -R platform/launcher/res/. android/app/src/main/res/
base64 --decode platform/public-test-key.base64 > android/app/probe-test.keystore
# flutter create uses app.nabd.meta6_probe; use a distinct, fixed identity.
find android/app/src -name MainActivity.kt -exec sed -i 's/^package .*/package app.nabd.meta6probe/' {} \;
flutter pub get
