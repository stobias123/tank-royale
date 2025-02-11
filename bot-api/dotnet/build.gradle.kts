import org.apache.tools.ant.filters.ReplaceTokens
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly


val artifactName = "Robocode.TankRoyale.BotApi"
version = libs.versions.tankroyale.get()

val docfxArchiveFilename = "docfx.zip"

val buildArchiveDirProvider: Provider<Directory> = layout.buildDirectory.dir("archive")
val buildArchivePath = buildArchiveDirProvider.get().toString()

val apiPath: String by rootProject.extra
val dotnetApiPath = "$apiPath/dotnet"


@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.itiviti.dotnet)
}

dotnet {
    solution = "Robocode.TankRoyale.BotApi/Robocode.TankRoyale.BotApi.csproj"

    projectName = artifactName

    build {
        version = libs.versions.tankroyale.get()
        packageVersion = version
    }

    test {
        solution = "Robocode.TankRoyale.BotApi.Tests/Robocode.TankRoyale.BotApi.Tests.csproj"
    }

    nugetPush {
        solution = "Robocode.TankRoyale.BotApi/Robocode.TankRoyale.BotApi.csproj"

        // api key is set by `nuget setApiKey <key>`
        source = "nuget.org"
    }
}

tasks {
    val prepareNugetDocs by registering(Copy::class) {
        doFirst {
            delete("docs")
        }
        from("nuget_docs") {
            filter<ReplaceTokens>("tokens" to mapOf("VERSION" to version))
        }
        into("docs")
    }

    clean {
        doFirst {
            delete(
                "build",
                "Robocode.TankRoyale.BotApi/obj",
                "Robocode.TankRoyale.BotApi/bin",
                "Robocode.TankRoyale.BotApi.Tests/obj",
                "Robocode.TankRoyale.BotApi.Tests/bin",
            )
        }
    }

    build {
        dependsOn(":schema:dotnet:build", prepareNugetDocs)
    }

    val docfx by registering {
        dependsOn(assemble)

        doLast {
            exec {
                workingDir("docfx_project")
                commandLine("docfx", "metadata") // build /api before building the _site
            }
            exec {
                workingDir("docfx_project")
                delete("_site", "api", "obj")
                commandLine("docfx", "build")    // build /_site
            }
        }
    }

    register<Copy>("uploadDocs") {
        dependsOn(docfx)

        val dotnetApiDir = "../../docs/api/dotnet"

        delete(dotnetApiDir)
        mkdir(dotnetApiDir)

        duplicatesStrategy = DuplicatesStrategy.FAIL

        from("docfx_project/_site")
        into(dotnetApiDir)
    }

    register("pushLocal") {
        dependsOn(build)

        doLast {
            val userhome = System.getenv("USERPROFILE") ?: System.getenv("HOME")
            println("$userhome/.nuget/packages/${artifactName.toLowerCaseAsciiOnly()}/$version")
            delete("$userhome/.nuget/packages/${artifactName.toLowerCaseAsciiOnly()}/$version")
            exec {
                workingDir("Robocode.TankRoyale.BotApi/bin/Release")
                commandLine("dotnet", "nuget", "push", "$artifactName.$version.nupkg", "-s", "local")
            }
        }
    }
}