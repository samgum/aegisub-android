plugins {
    kotlin("jvm")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin { jvmToolchain(17) }

dependencies {
    // AssScript.events: ImmutableList 出现在公开 API → 用 api 暴露给消费者
    api("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.1.0")
}

tasks.test { useJUnitPlatform() }
