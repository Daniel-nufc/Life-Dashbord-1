import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Reine Kotlin-Bibliothek ohne Android: die Planungs-Logik.
// Dadurch laufen die Unit-Tests schnell auf jedem Rechner.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
