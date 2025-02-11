import java.nio.file.Files
import java.nio.file.Paths

val title = "Robocode Tank Royale Bot API"
group = "dev.robocode.tankroyale"
version = libs.versions.tankroyale.get()
description = "Java API library for developing bots for Robocode Tank Royale"

val artifactBaseName = "robocode-tankroyale-bot-api"

val ossrhUsername: String? by project
val ossrhPassword: String? by project

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    `java-library`
    alias(libs.plugins.shadow.jar)
    `maven-publish`
    signing
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = sourceCompatibility

    withJavadocJar()
    withSourcesJar()
}

dependencies {
    implementation(project(":schema:jvm"))
    implementation(libs.gson)
    implementation(libs.gson.extras)
    implementation(libs.nv.i18n)

    testImplementation(libs.junit.api)
    testImplementation(libs.junit.params)
    testRuntimeOnly(libs.junit.engine)
    testImplementation(libs.assertj)
    testImplementation(libs.junit.pioneer)
    testImplementation(libs.java.websocket)
}

tasks {
    withType<Test> {
        useJUnitPlatform()
    }

    jar {
        enabled = false
        dependsOn(
            shadowJar
        )
    }

    shadowJar {
        manifest {
            attributes["Implementation-Title"] = title
            attributes["Implementation-Version"] = project.version
            attributes["Implementation-Vendor"] = "robocode.dev"
            attributes["Package"] = project.group
        }
        minimize()
        archiveBaseName.set(artifactBaseName)
        archiveClassifier.set("")
    }

    val javadoc = withType<Javadoc> {
        title
        source(sourceSets.main.get().allJava)

        (options as StandardJavadocDocletOptions).apply {
            memberLevel = JavadocMemberLevel.PUBLIC
            overview = "src/main/javadoc/overview.html"

            addFileOption("-add-stylesheet", File(projectDir, "src/main/javadoc/themes/prism.css"))
            addBooleanOption("-allow-script-in-comments", true)
            addStringOption("Xdoclint:none", "-quiet")
        }
        exclude(
            "**/dev/robocode/tankroyale/botapi/internal/**",
            "**/dev/robocode/tankroyale/botapi/mapper/**",
            "**/dev/robocode/tankroyale/sample/**"
        )
        doLast {
            Files.copy(
                Paths.get("$projectDir/src/main/javadoc/prism.js"),
                Paths.get("$buildDir/docs/javadoc/prism.js")
            )
        }
    }

    register<Copy>("uploadDocs") {
        dependsOn(javadoc)

        val javadocDir = "../../docs/api/java"

        delete(javadocDir)
        mkdir(javadocDir)

        duplicatesStrategy = DuplicatesStrategy.FAIL

        from("build/docs/javadoc")
        into(javadocDir)
    }

    val  javadocJar = named("javadocJar")
    val  sourcesJar = named("sourcesJar")

    publishing {
        publications {
            create<MavenPublication>("bot-api") {
                artifact(shadowJar)
                artifact(javadocJar)
                artifact(sourcesJar)

                groupId = group as String?
                artifactId = artifactBaseName
                version

                pom {
                    name.set(title)
                    description.set(project.description)
                    url.set("https://github.com/robocode-dev/tank-royale")

                    repositories {
                        maven {
                            setUrl("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2")
                            mavenContent {
                                releasesOnly()
                            }
                            credentials {
                                username = ossrhUsername
                                password = ossrhPassword
                            }
                        }
                        /*
                        maven {
                            setUrl("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                            mavenContent {
                                snapshotsOnly()
                            }
                            credentials {
                                username = ossrhUsername
                                password = ossrhPassword
                            }
                        }*/
                    }

                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("fnl")
                            name.set("Flemming Nørnberg Larsen")
                            organization.set("flemming-n-larsen")
                            organizationUrl.set("https://github.com/flemming-n-larsen")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/robocode-dev/tank-royale.git")
                        developerConnection.set("scm:git:ssh://github.com:robocode-dev/tank-royale.git")
                        url.set("https://github.com/robocode-dev/tank-royale/tree/master")
                    }
                }
            }
        }
    }
}

signing {
    sign(publishing.publications["bot-api"])
}