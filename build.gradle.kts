plugins {
    kotlin("jvm") version "2.1.0"
    antlr
    application
}

group = "transpiler"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    antlr("org.antlr:antlr4:4.13.2")
    implementation("org.antlr:antlr4-runtime:4.13.2")
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

application {
    mainClass.set("transpiler.MainKt")
}

tasks.generateGrammarSource {
    maxHeapSize = "64m"
    val outDir = layout.buildDirectory
        .dir("generated-src/antlr/main/transpiler/parser/generated")
        .get().asFile
    // Copy grammar files to the output dir so -lib can find both imported grammars
    // (UnicodeClasses.g4) and generated tokens files (KotlinLexer.tokens) in one place.
    doFirst {
        outDir.mkdirs()
        fileTree("src/main/antlr/transpiler/parser/generated").matching {
            include("*.g4")
        }.forEach { it.copyTo(File(outDir, it.name), overwrite = true) }
    }
    arguments = arguments + listOf(
        "-visitor",
        "-no-listener",
        "-package", "transpiler.parser.generated",
        "-lib", outDir.absolutePath
    )
}

// Generated ANTLR Java sources must be on the compile classpath
sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated-src/antlr/main"))
        }
    }
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}

tasks.compileJava {
    dependsOn(tasks.generateGrammarSource)
}

tasks.compileTestKotlin {
    dependsOn(tasks.generateTestGrammarSource)
}

tasks.test {
    useJUnitPlatform()
    // Allow tests to invoke kotlinc and g++ subprocesses
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
