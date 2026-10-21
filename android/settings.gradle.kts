pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "AionLinkOpen"
include(":app", ":core", ":protocol-api", ":protocol-iccoa", ":transport-bluetooth", ":transport-usb", ":transport-wifi", ":projection", ":vehicle-profiles")
