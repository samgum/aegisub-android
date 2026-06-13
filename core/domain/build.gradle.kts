plugins {
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.1.0")
}

tasks.test { useJUnitPlatform() }
