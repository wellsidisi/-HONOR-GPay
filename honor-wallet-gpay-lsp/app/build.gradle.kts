plugins {
    id("com.android.application")
}

// 签名密钥不入库：本地有 keystore/honor-wallet-gpay.jks 才会签名，否则出 unsigned
val ksFile = file("../../keystore/honor-wallet-gpay.jks")
val ksPass = "honorwalletgpay"
val ksAlias = "honorwalletgpay"

android {
    namespace = "com.honorwallet.gpay.lsp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.honorwallet.gpay.lsp"
        minSdk = 30
        targetSdk = 34
        versionCode = 6
        versionName = "1.1.0"
    }

    signingConfigs {
        if (ksFile.exists()) {
            create("local") {
                storeFile = ksFile
                storePassword = ksPass
                keyAlias = ksAlias
                keyPassword = ksPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (ksFile.exists()) {
                signingConfig = signingConfigs.getByName("local")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    // Xposed API 只参与编译，运行时由 LSPosed 提供
    compileOnly(files("libs/xposed-api.jar"))
}
