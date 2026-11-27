plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

if (rootDir.absolutePath.any { it.code > 127 }) {
    val asciiBuildRoot = file("${System.getProperty("java.io.tmpdir")}/EnziiDictBuild")
    allprojects {
        layout.buildDirectory.set(file("$asciiBuildRoot/${project.name}"))
    }
}
