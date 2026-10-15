import java.util.Properties

plugins {
	id("com.android.application")
	kotlin("android")
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.aboutlibraries)
}

android {
	namespace = "org.jellyfin.androidtv"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		minSdk = libs.versions.android.minSdk.get().toInt()
		targetSdk = libs.versions.android.targetSdk.get().toInt()

		// Release version
		applicationId = "com.aeontv.tv"
		versionName = project.getVersionName()
		versionCode = getVersionCode(versionName!!)
		setProperty("archivesBaseName", "aeontv-tv-v$versionName")
	}

	buildFeatures {
		buildConfig = true
		viewBinding = true
		compose = true
	}

	compileOptions {
		isCoreLibraryDesugaringEnabled = true
	}

	// AeonTV permanent signing key (workspace keystore, survives build-machine
	// changes so updates always install over the previous build). The keystore
	// lives OUTSIDE this repo; when it is absent the default debug signing is
	// used instead.
	val aeonPropsFile = file("${rootProject.projectDir}/../signing/keystore.properties")
	val aeonProps = Properties()
	val aeonHasKey = aeonPropsFile.exists()
	if (aeonHasKey) {
		aeonPropsFile.inputStream().use { aeonProps.load(it) }
	}

	signingConfigs {
		if (aeonHasKey) {
			create("aeon") {
				storeFile = file("${rootProject.projectDir}/../signing/${aeonProps.getProperty("storeFile")}")
				storePassword = aeonProps.getProperty("storePassword")
				keyAlias = aeonProps.getProperty("keyAlias")
				keyPassword = aeonProps.getProperty("keyPassword")
			}
		}
	}

	buildTypes {
		val release by getting {
			isMinifyEnabled = false

			// Set package names used in various XML files
			resValue("string", "app_id", "com.aeontv.tv")
			resValue("string", "app_search_suggest_authority", "com.aeontv.tv.content")
			resValue("string", "app_search_suggest_intent_data", "content://com.aeontv.tv.content/intent")

			// Set flavored application name
			resValue("string", "app_name", "@string/app_name_release")

			if (aeonHasKey) signingConfig = signingConfigs.getByName("aeon")

			buildConfigField("boolean", "DEVELOPMENT", "false")
		}

		val debug by getting {
			// Use different application id to run release and debug at the same time
			applicationIdSuffix = ".debug"

			// Set package names used in various XML files
			resValue("string", "app_id", "com.aeontv.tv" + applicationIdSuffix)
			resValue("string", "app_search_suggest_authority", "com.aeontv.tv${applicationIdSuffix}.content")
			resValue("string", "app_search_suggest_intent_data", "content://com.aeontv.tv${applicationIdSuffix}.content/intent")

			// Set flavored application name
			resValue("string", "app_name", "@string/app_name_debug")

			if (aeonHasKey) signingConfig = signingConfigs.getByName("aeon")

			buildConfigField("boolean", "DEVELOPMENT", (defaultConfig.versionCode!! < 100).toString())
		}
	}

	lint {
		lintConfig = file("$rootDir/android-lint.xml")
		abortOnError = false
		sarifReport = true
		checkDependencies = true
	}

	testOptions.unitTests.all {
		it.useJUnitPlatform()
	}
}

aboutLibraries {
	// Remove the "generated" timestamp to allow for reproducible builds
	excludeFields = arrayOf("generated")
}

val versionTxt by tasks.registering {
	val path = layout.buildDirectory.asFile.get().resolve("version.txt")

	doLast {
		val versionString = "v${android.defaultConfig.versionName}=${android.defaultConfig.versionCode}"
		logger.info("Writing [$versionString] to $path")
		path.writeText("$versionString\n")
	}
}

dependencies {
	// Jellyfin
	implementation(projects.playback.core)
	implementation(projects.playback.jellyfin)
	implementation(projects.playback.media3.exoplayer)
	implementation(projects.playback.media3.session)
	implementation(projects.preference)
	implementation(libs.jellyfin.apiclient)
	implementation(libs.jellyfin.sdk) {
		// Change version if desired
		val sdkVersion = findProperty("sdk.version")?.toString()
		when (sdkVersion) {
			"local" -> version { strictly("latest-SNAPSHOT") }
			"snapshot" -> version { strictly("master-SNAPSHOT") }
			"unstable-snapshot" -> version { strictly("openapi-unstable-SNAPSHOT") }
		}
	}

	// Kotlin
	implementation(libs.kotlinx.coroutines)
	implementation(libs.kotlinx.serialization.json)

	// Android(x)
	implementation(libs.androidx.core)
	implementation(libs.androidx.activity)
	implementation(libs.androidx.fragment)
	implementation(libs.androidx.fragment.compose)
	implementation(libs.androidx.leanback.core)
	implementation(libs.androidx.leanback.preference)
	implementation(libs.androidx.preference)
	implementation(libs.androidx.appcompat)
	implementation(libs.androidx.tvprovider)
	implementation(libs.androidx.constraintlayout)
	implementation(libs.androidx.recyclerview)
	implementation(libs.androidx.work.runtime)
	implementation(libs.bundles.androidx.lifecycle)
	implementation(libs.androidx.window)
	implementation(libs.androidx.cardview)
	implementation(libs.androidx.startup)
	implementation(libs.bundles.androidx.compose)
	implementation(libs.androidx.tv.material)

	// Dependency Injection
	implementation(libs.bundles.koin)

	// Media players
	implementation(libs.androidx.media3.exoplayer)
	implementation(libs.androidx.media3.exoplayer.hls)
	implementation(libs.androidx.media3.ui)
	implementation(libs.jellyfin.androidx.media3.ffmpeg.decoder)

	// Markdown
	implementation(libs.bundles.markwon)

	// Image utility
	implementation(libs.bundles.coil)

	// Crash Reporting
	implementation(libs.bundles.acra)

	// Licenses
	implementation(libs.aboutlibraries)

	// Logging
	implementation(libs.timber)
	implementation(libs.slf4j.timber)

	// Compatibility (desugaring)
	coreLibraryDesugaring(libs.android.desugar)

	// Testing
	testImplementation(libs.kotest.runner.junit5)
	testImplementation(libs.kotest.assertions)
	testImplementation(libs.mockk)
}
