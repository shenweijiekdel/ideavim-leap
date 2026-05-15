import org.jetbrains.intellij.platform.gradle.tasks.BuildSearchableOptionsTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.3.0"
}

group = "com.github.shenweijie"
version = "0.3.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        local("/Applications/IntelliJ IDEA.app")
        zipSigner()
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "vim-leap"
        version = "0.3.0"
        description = "leap.nvim port for IdeaVim — 2-char search labels, safe-label autojump, label groups, traversal, equivalence classes, remote operation."
        changeNotes = """
            <ul>
              <li>; / , repeat: labels now update correctly after each navigation step</li>
              <li>; / , repeat: fixed direction — , always goes opposite to original search</li>
              <li>; / , repeat: eliminated flash/flicker between consecutive presses</li>
              <li>Nearest match highlight color changed to orange to distinguish from labels</li>
            </ul>
        """.trimIndent()
        ideaVersion {
            sinceBuild = "231"
            untilBuild = provider { null }
        }
        vendor {
            name = "shenweijie"
            url = "https://github.com/shenweijie/vim-leap"
        }
    }

    signing {
        certificateChainFile = file("certificate/chain.crt")
        privateKeyFile = file("certificate/private.pem")
        password = ""
    }

    publishing {
        token = providers.environmentVariable("INTELLIJ_PUBLISH_TOKEN")
    }
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks {
    withType<BuildSearchableOptionsTask> { enabled = false }
}
