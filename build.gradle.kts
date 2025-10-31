// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        maven("https://maven.google.com")
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.6.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.0")
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.0.0-1.0.21")
        classpath("org.jetbrains.kotlin:kotlin-serialization:2.0.0")
    }
}