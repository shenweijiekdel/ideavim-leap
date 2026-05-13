import org.jetbrains.intellij.platform.gradle.tasks.BuildSearchableOptionsTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.3.0"
}

group = "com.github.shenweijie"
version = "0.1.0"

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
        version = "0.1.0"
        description = "leap.nvim port for IdeaVim — 2-char search labels, safe-label autojump, label groups, traversal, equivalence classes, remote operation."
        changeNotes = """
            <ul>
              <li>Initial release</li>
              <li>2-char leap jump: forward, backward, bidirectional</li>
              <li>Preview after char1, safe-label autojump, label groups (Space/Backspace)</li>
              <li>Traversal mode (Enter/Backspace to step through matches)</li>
              <li>Enter shortcut: repeat last search or jump to nearest</li>
              <li>Equivalence classes (space/tab/newline)</li>
              <li>Till motions (forward/backward offset)</li>
              <li>Remote operation: jump, operate, return</li>
              <li>PSI treesitter-style structural selection</li>
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
