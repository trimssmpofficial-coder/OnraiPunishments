plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "dev.onrai"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.218-stable")
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.xerial:sqlite-jdbc:3.54.1.0")
    implementation("com.mysql:mysql-connector-j:9.5.0")
    implementation("org.mongodb:mongodb-driver-sync:5.13.0")
    compileOnly("org.slf4j:slf4j-api:2.0.17")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }

tasks.withType<JavaCompile> { options.encoding = "UTF-8"; options.release.set(25) }
tasks.test { useJUnitPlatform() }
tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    relocate("com.zaxxer.hikari", "dev.onrai.libs.hikari")
    // Do not minimize: JDBC discovery, drivers and MongoDB use service providers and reflection.
}
tasks.build { dependsOn(tasks.shadowJar) }
