#!/data/data/com.termux/files/usr/bin/bash
# BTChat Ultra Pro — Bootstrap (from zero)

set +e
G='\033[0;32m'; Y='\033[1;33m'; C='\033[0;36m'; RD='\033[0;31m'; R='\033[0m'

echo -e "${C}=== BTChat Bootstrap ===${R}"

# ---- 1. Folders ----
echo -e "${Y}[1/6] Creating folders…${R}"
mkdir -p app/src/main/java/com/example/btchat/{ui/{theme,splash,home/components,chat/components,settings,ai,file,qr,group/components,call,common,voice,walkie,onboarding},bluetooth,adapter,model,database,repository,di,utils,notification,widget,tile,backup}
mkdir -p app/src/main/res/{drawable,layout,values,font,raw,xml,mipmap-anydpi-v26,mipmap-mdpi,mipmap-hdpi,mipmap-xhdpi,mipmap-xxhdpi,mipmap-xxxhdpi}
mkdir -p app/src/main/assets
mkdir -p gradle/wrapper
mkdir -p docs
echo -e "${G}  ✓ Folders ready${R}"

# ---- 2. Root gradle files ----
echo -e "${Y}[2/6] Root gradle files…${R}"

cat > settings.gradle.kts << 'EOF'
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "BTChat"
include(":app")
EOF

cat > build.gradle.kts << 'EOF'
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.dagger.hilt.android") version "2.50" apply false
}
EOF

cat > gradle.properties << 'EOF'
org.gradle.jvmargs=-Xmx2048m
org.gradle.parallel=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
EOF

cat > gradle/wrapper/gradle-wrapper.properties << 'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.4-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF

cat > local.properties << 'EOF'
# SDK path set karo — Termux me usually nahi hota
# Android Studio me kholne pe auto-fill hoga
EOF

cat > .gitignore << 'EOF'
.gradle/
build/
.idea/
local.properties
*.apk
EOF

echo -e "${G}  ✓ Root gradle files${R}"

# ---- 3. app/build.gradle.kts ----
echo -e "${Y}[3/6] app/build.gradle.kts…${R}"

cat > app/build.gradle.kts << 'EOF'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.btchat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.btchat"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "2.0.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
EOF

cat > app/proguard-rules.pro << 'EOF'
-keep class com.example.btchat.model.** { *; }
-keep @com.google.gson.annotations.SerializedName class * { *; }
EOF

echo -e "${G}  ✓ app/build.gradle.kts${R}"

# ---- 4. AndroidManifest ----
echo -e "${Y}[4/6] AndroidManifest…${R}"

cat > app/src/main/AndroidManifest.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.CAMERA" />

    <application
        android:name=".BTChatApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher"
        android:supportsRtl="true"
        android:theme="@style/Theme.BTChat">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

echo -e "${G}  ✓ AndroidManifest.xml${R}"

# ---- 5. Resources ----
echo -e "${Y}[5/6] Resources…${R}"

cat > app/src/main/res/values/strings.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">BTChat</string>
</resources>
EOF

cat > app/src/main/res/values/colors.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
    <color name="ic_launcher_background">#0A0A14</color>
</resources>
EOF

cat > app/src/main/res/values/themes.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.BTChat" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@android:color/black</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
    </style>
</resources>
EOF

cat > app/src/main/res/drawable/ic_launcher_foreground.xml << 'EOF'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#00F0FF"
        android:pathData="M54,20 L54,44 L38,30 L42,26 L54,38 L66,26 L70,30 L54,44 L54,64 L66,52 L70,56 L54,72 L54,88 L50,88 L50,72 L34,56 L38,52 L50,64 L50,44 L38,56 L34,52 L50,36 Z" />
</vector>
EOF

cat > app/src/main/res/drawable/ic_btchat_logo.xml << 'EOF'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#00F0FF"
        android:pathData="M54,20 L54,44 L38,30 L42,26 L54,38 L66,26 L70,30 L54,44 L54,64 L66,52 L70,56 L54,72 L54,88 L50,88 L50,72 L34,56 L38,52 L50,64 L50,44 L38,56 L34,52 L50,36 Z" />
</vector>
EOF

cat > app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
EOF
cp app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml

for dpi in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
cat > "app/src/main/res/mipmap-$dpi/ic_launcher.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@color/ic_launcher_background" />
    <item android:drawable="@drawable/ic_launcher_foreground" />
</layer-list>
EOF
cp "app/src/main/res/mipmap-$dpi/ic_launcher.xml" "app/src/main/res/mipmap-$dpi/ic_launcher_round.xml"
done

echo -e "${G}  ✓ Resources${R}"

# ---- 6. Kotlin minimal skeleton ----
echo -e "${Y}[6/6] Kotlin skeleton files…${R}"

# BTChatApp
cat > app/src/main/java/com/example/btchat/BTChatApp.kt << 'EOF'
package com.example.btchat

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BTChatApp : Application()
EOF

# MainActivity
cat > app/src/main/java/com/example/btchat/ui/MainActivity.kt << 'EOF'
package com.example.btchat.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
fun App() {
    MaterialTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("BTChat", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("Setup ready! Ab baaki files bhejo.")
                }
            }
        }
    }
}
EOF

echo -e "${G}  ✓ Kotlin skeleton${R}"

echo ""
echo -e "${G}============================================================${R}"
echo -e "${G}  ✅ Bootstrap Complete!${R}"
echo -e "${G}============================================================${R}"
echo ""
echo -e "${C}📁 Check karo:${R}"
echo "   ls -la"
echo "   find app -type f | head -30"
echo ""
echo -e "${Y}⚠️  IMPORTANT:${R}"
echo "   Termux me Android SDK nahi hota — yahan build nahi hoga!"
echo "   Options:"
echo "   1. Files ko PC pe le jao → Android Studio me build karo"
echo "   2. Ya phir Termux me: pkg install openjdk-17 gradle (partial)"
echo ""
echo -e "${C}💡 Full build ke liye Android Studio use karo — best hai${R}"
echo ""
