plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    linuxX64 {
        binaries {
            sharedLib()
        }
    }
    linuxArm64 {
        binaries {
            sharedLib()
        }
    }
    macosX64 {
        binaries {
            sharedLib()
        }
    }
    macosArm64 {
        binaries {
            sharedLib()
        }
    }
    mingwX64 {
        binaries {
            sharedLib()
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(libs.sekret)
        }

        val jniNativeMain by creating {
            nativeMain.orNull?.let { dependsOn(it) } ?: dependsOn(commonMain.get())
            // androidNativeMain.orNull?.dependsOn(this)
            linuxMain.orNull?.dependsOn(this)
            mingwMain.orNull?.dependsOn(this)
            macosMain.orNull?.dependsOn(this)
        }

        val jniMain by creating {
            dependsOn(commonMain.get())
            // androidMain.orNull?.dependsOn(this)
            jvmMain.orNull?.dependsOn(this)
        }
    }
}
