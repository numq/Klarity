import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.0"
}

group = "com.github.numq"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.bytedeco:javacv:1.5.9") {
        exclude("org.bytedeco", "artoolkitplus")
        exclude("org.bytedeco", "flandmark")
        exclude("org.bytedeco", "flycapture")
        exclude("org.hamcrest", "hamcrest-core")
        exclude("org.bytedeco", "leptonica")
        exclude("org.bytedeco", "libdc1394")
        exclude("org.bytedeco", "libfreenect")
        exclude("org.bytedeco", "libfreenect2")
        exclude("org.bytedeco", "librealsense")
        exclude("org.bytedeco", "librealsense2")
        exclude("org.bytedeco", "openblas")
        exclude("org.bytedeco", "opencv")
        exclude("org.bytedeco", "tesseract")
        exclude("org.bytedeco", "videoinput")
    }

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    testRuntimeOnly("org.bytedeco:ffmpeg:6.0-1.5.9:windows-x86_64")
    testRuntimeOnly("org.bytedeco:openblas:0.3.23-1.5.9:windows-x86_64")
    testRuntimeOnly("org.bytedeco:opencv:4.7.0-1.5.9:windows-x86_64")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}