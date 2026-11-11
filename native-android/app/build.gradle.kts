plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\""

// معرّف عميل Web ديال Google — البلاصة الوحيدة فين كاين فالكود كامل.
// ماشي سر: نفس القيمة راه مدفونة فالتطبيق ومستعملة فالخادم كـ audience.
// ما كنحتاجوش الـ client secret نهائياً (Google كتستعملو غير فتطبيقات السيرڤور).
val googleWebClientId = "805340599517-erlmojpehga8ifj04ppb63kbbqpiakb8.apps.googleusercontent.com"
android {
    namespace = "app.nabd.nativeapp"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.nabd.social"
        minSdk = 26
        targetSdk = 35
        versionCode = 40
        versionName = "2.9.20"
        buildConfigField("String", "TELEGRAM_TOKEN", quoted(System.getenv("NABD_TELEGRAM_BOT_TOKEN") ?: ""))
        buildConfigField("String", "TELEGRAM_CHAT", quoted(System.getenv("NABD_TELEGRAM_CHAT_ID") ?: ""))
        buildConfigField("String", "UPDATE_REPOSITORY", quoted("f0uri/Nabd"))
        // Web client ID ديال Google (ماشي تاع Android): الخادم كيفحقق idToken ضدّو.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", quoted(System.getenv("NABD_GOOGLE_WEB_CLIENT_ID") ?: googleWebClientId))
        // العنوان ديال خادم GraphQL (مثال: https://nabd.onrender.com/graphql).
        buildConfigField("String", "API_URL", quoted(System.getenv("NABD_API_URL") ?: ""))
        // معرّف وسر عميل Pinterest: من GitHub Secrets فقط، وما كاينش فالكود.
        // NOTE: السر كيولّي مدفون فالـ APK (Pinterest ما كتقبلش عميل بلا سر) —
        // هادشي معروف فتطبيقات الموبايل، وعلاجه الحقيقي هو بروكسي، ومحظور علينا.
        buildConfigField("String", "PINTEREST_CLIENT_ID", quoted(System.getenv("NABD_PINTEREST_CLIENT_ID") ?: ""))
        buildConfigField("String", "PINTEREST_CLIENT_SECRET", quoted(System.getenv("NABD_PINTEREST_CLIENT_SECRET") ?: ""))
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("upgrade") {
            storeFile = file("../signing/debug.keystore")
            storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("upgrade") }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("upgrade")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // الدخول بحساب Google بـ Credential Manager (البديلة العصرية ديال Google Sign-In).
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
