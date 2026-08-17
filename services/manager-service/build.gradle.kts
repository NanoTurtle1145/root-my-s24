plugins { alias(libs.plugins.agp.lib) }

android {
    buildFeatures { aidl = true }

    buildTypes { release { isMinifyEnabled = false } }

    namespace = "cn.nanoturtle.rootmys9280.managerservice"
}

dependencies { api(libs.rikkax.parcelablelist) }
