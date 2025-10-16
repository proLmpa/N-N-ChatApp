plugins {
    // 서브 프로젝트에서 Kotlin/JVM을 사용하도록 설정
    id("org.jetbrains.kotlin.jvm") version "1.9.23" apply false
}

allprojects {
    group = "com.example"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        val implementation by configurations
        // 모든 서브 프로젝트가 Kotlin 표준 라이브러리를 사용하도록 설정
        implementation(kotlin("stdlib"))
    }
}
