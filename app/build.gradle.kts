import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.api.tasks.testing.Test
import javax.xml.parsers.DocumentBuilderFactory

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.detekt)
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.crashlytics)
  jacoco
}

android {
  namespace = "com.example"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.aistudio.vvfsmartmanager.app"
    minSdk = 24
    targetSdk = 35
    versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
    versionName = project.findProperty("versionName") as String? ?: "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    val keystorePath = System.getenv("KEYSTORE_PATH")
    if (!keystorePath.isNullOrEmpty()) {
      create("release") {
        storeFile = file(keystorePath)
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
    val debugKeystoreFile = file("${rootDir}/debug.keystore")
    if (debugKeystoreFile.exists()) {
      create("debugConfig") {
        storeFile = debugKeystoreFile
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfigs.findByName("release")?.let { signingConfig = it }
    }
    debug {
      signingConfigs.findByName("debugConfig")?.let { signingConfig = it }
      enableUnitTestCoverage = true
      enableAndroidTestCoverage = true
    }
  }
  testCoverage { jacocoVersion = "0.8.13" }
  lint {
    checkReleaseBuilds = true
    abortOnError = true
    lintConfig = file("lint.xml")
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.documentfile)
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.tensorflow.lite)
  implementation(libs.mlkit.text.recognition)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.auth)
  implementation(libs.firebase.crashlytics)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.androidx.room.testing)
  testImplementation("io.mockk:mockk:1.13.11")
  testImplementation("androidx.work:work-testing:2.9.1")
  testImplementation("androidx.arch.core:core-testing:2.2.0")
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.leakcanary.android)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

detekt {
  buildUponDefaultConfig = true
  allRules = false
  ignoreFailures = false
  config.setFrom(file("config/detekt/detekt.yml"))
  baseline = file("detekt-baseline.xml")
}

val coverageExclusions = listOf(
  "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
  "**/*Test*.*", "android/**/*.*", "**/*_Factory.*", "**/*_MembersInjector.*",
  "**/*\$Companion.*", "**/*\$DefaultImpls.*"
)

tasks.withType<Test>().configureEach {
  extensions.configure(org.gradle.testing.jacoco.plugins.JacocoTaskExtension::class.java) {
    isIncludeNoLocationClasses = true
    excludes = listOf("jdk.internal.*")
  }
}

val debugUnitTest = tasks.named<Test>("testDebugUnitTest")

tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
  group = "verification"
  description = "Generate XML and HTML JaCoCo coverage for JVM/Robolectric debug tests."
  dependsOn(debugUnitTest)
  reports {
    xml.required.set(true)
    html.required.set(true)
  }
  val javaClasses = fileTree(layout.buildDirectory.dir("intermediates/javac/debug/classes")) { exclude(coverageExclusions) }
  val kotlinClasses = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) { exclude(coverageExclusions) }
  classDirectories.setFrom(files(javaClasses, kotlinClasses))
  sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
  executionData.setFrom(debugUnitTest.map { it.extensions.getByType(org.gradle.testing.jacoco.plugins.JacocoTaskExtension::class.java).destinationFile })
}

tasks.register<JacocoReport>("jacocoDebugAndroidTestReport") {
  group = "verification"
  description = "Generate XML and HTML JaCoCo coverage for debug instrumented Android tests."
  dependsOn("connectedDebugAndroidTest")
  reports {
    xml.required.set(true)
    html.required.set(true)
  }
  val javaClasses = fileTree(layout.buildDirectory.dir("intermediates/javac/debug/classes")) { exclude(coverageExclusions) }
  val kotlinClasses = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) { exclude(coverageExclusions) }
  classDirectories.setFrom(files(javaClasses, kotlinClasses))
  sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
  executionData.setFrom(fileTree(layout.buildDirectory) { include("outputs/code_coverage/debugAndroidTest/connected/**/*.ec") })
}

fun coverageTotals(report: File): Pair<Long, Long> {
  if (!report.exists()) return 0L to 0L
  val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }.newDocumentBuilder().parse(report)
  val counters = document.getElementsByTagName("counter")
  var covered = 0L
  var missed = 0L
  for (i in 0 until counters.length) {
    val node = counters.item(i)
    if (node.attributes.getNamedItem("type")?.nodeValue == "INSTRUCTION") {
      covered += node.attributes.getNamedItem("covered")?.nodeValue?.toLongOrNull() ?: 0L
      missed += node.attributes.getNamedItem("missed")?.nodeValue?.toLongOrNull() ?: 0L
    }
  }
  return covered to missed
}

tasks.register("verifyAggregateCoverage") {
  group = "verification"
  description = "Require aggregate JVM + instrumented instruction coverage to be at least 80%."
  dependsOn("jacocoDebugUnitTestReport", "jacocoDebugAndroidTestReport")
  doLast {
    val reports = listOf(
      layout.buildDirectory.file("reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml").get().asFile,
      layout.buildDirectory.file("reports/jacoco/jacocoDebugAndroidTestReport/jacocoDebugAndroidTestReport.xml").get().asFile
    )
    val totals = reports.map(::coverageTotals).reduce { a, b -> (a.first + b.first) to (a.second + b.second) }
    val total = totals.first + totals.second
    val percent = if (total == 0L) 0.0 else totals.first * 100.0 / total
    logger.lifecycle("Aggregate instruction coverage: %.2f%% (%d covered / %d total)".format(percent, totals.first, total))
    check(percent >= 80.0) { "Aggregate instruction coverage %.2f%% is below required 80%%.".format(percent) }
  }
}
