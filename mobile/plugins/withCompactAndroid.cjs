const { withGradleProperties, withAppBuildGradle, withDangerousMod } = require('expo/config-plugins');
const fs = require('node:fs');
const path = require('node:path');

const properties = {
  'org.gradle.jvmargs': '-Xmx3072m -XX:MaxMetaspaceSize=768m',
  'android.enableProguardInReleaseBuilds': 'true',
  'android.enableShrinkResourcesInReleaseBuilds': 'true',
  'android.enableBundleCompression': 'true',
  'expo.useLegacyPackaging': 'true',
  reactNativeArchitectures: 'armeabi-v7a,arm64-v8a,x86_64',
};
const marker = '// Nabd compact APKs';
function splitGradle(contents) {
  if (contents.includes(marker)) return contents;
  return `${contents}\n${marker}
android {
    splits {
        abi {
            enable true
            reset()
            include 'armeabi-v7a', 'arm64-v8a', 'x86_64'
            universalApk false
        }
    }
}
`;
}
function withCompactAndroid(config) {
  config = withGradleProperties(config, (mod) => {
    for (const [key, value] of Object.entries(properties)) {
      mod.modResults = mod.modResults.filter((entry) => entry.key !== key);
      mod.modResults.push({ type: 'property', key, value });
    }
    return mod;
  });
  config = withAppBuildGradle(config, (mod) => {
    if (mod.modResults.language !== 'groovy') throw new Error('Compact APK configuration requires Groovy');
    mod.modResults.contents = splitGradle(mod.modResults.contents);
    return mod;
  });
  return withDangerousMod(config, ['android', async (mod) => {
    const file = path.join(mod.modRequest.platformProjectRoot, 'app/proguard-rules.pro');
    const rules = fs.readFileSync(file, 'utf8');
    // JNI looks classes up by name. These libraries do not ship consumer rules.
    if (!rules.includes('# Nabd Nitro JNI')) {
      fs.appendFileSync(file, '\n# Nabd Nitro JNI\n-keep class com.margelo.nitro.** { *; }\n');
    }
    return mod;
  }]);
}
module.exports = withCompactAndroid;
module.exports.properties = properties;
module.exports.splitGradle = splitGradle;
