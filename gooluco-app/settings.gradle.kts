pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "gooluco-app"

// Módulo compartido con el spike: un solo código fuente del decodificador OKOK (sin copias divergentes).
include(":okok-decoder")
project(":okok-decoder").projectDir = file("../spike-okok/okok-decoder")

include(":app")
