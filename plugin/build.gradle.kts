plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
    // IntelliJ Platform Gradle Plugin 2.x. Pinned to 2.7.0: recent versions require Gradle 9.
    id("org.jetbrains.intellij.platform") version "2.7.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        pluginVerifier()
        zipSigner()
    }
    // Bundled with the plugin instead of relying on the IDE's internal Gson (not a stable public API).
    implementation("com.google.code.gson:gson:2.11.0")
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        id = providers.gradleProperty("pluginId")
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        vendor {
            name = providers.gradleProperty("pluginVendorName")
            email = providers.gradleProperty("pluginVendorEmail")
            url = providers.gradleProperty("pluginVendorUrl")
        }
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null } // no upper bound: avoids rebuilding for every Android Studio release
        }
    }

    // Credentials ONLY via environment variables: never in the repository.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.gradleProperty("pluginVersion").map {
            listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" })
        }
    }

    // ./gradlew verifyPlugin: checks the built plugin against the IDEs recommended by JetBrains
    // (downloads several GB). For a quick check against your local Android Studio use
    // scripts/verify-plugin.sh (local() here clashes with the main platform dependency in 2.7.0).
    pluginVerification {
        ides {
            recommended()
        }
    }
}

// Prevents accidentally publishing with the placeholder values.
tasks.publishPlugin {
    doFirst {
        val props = listOf("pluginId", "pluginVendorName", "pluginVendorEmail", "pluginVendorUrl")
        val placeholders = props.filter { providers.gradleProperty(it).get().contains("CHANGE_ME") }
        check(placeholders.isEmpty()) { "Fill these in gradle.properties before publishing: $placeholders" }
    }
}
