plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish )
}

android {
    namespace = "com.xctech.excellibrary"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    // 添加库模块特定配置
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

// 添加发布配置

dependencies {


    api (libs.androidx.core.ktx)
    api (libs.androidx.appcompat)
    api (libs.material)
    testImplementation (libs.junit)
    androidTestImplementation (libs.androidx.junit)
    androidTestImplementation (libs.androidx.espresso.core)
    api (libs.poi) {
        exclude(group = "org.apache.logging.log4j")
    }
    api (libs.poi.ooxml) {
        exclude(group = "org.apache.logging.log4j")
    }
    api (libs.androidx.recyclerview)

    api (libs.androidx.lifecycle.viewmodel.ktx)
    api (libs.androidx.lifecycle.livedata.ktx)
    api (libs.androidx.activity.ktx)

    // 添加Gson依赖用于JSON解析
    api ("com.google.code.gson:gson:2.10.1")

    //添加glide仓库
    api ("com.github.bumptech.glide:glide:4.16.0")

    //PhotoView
    api ("com.github.chrisbanes:PhotoView:2.3.0")

}