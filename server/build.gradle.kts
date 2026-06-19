plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
}

group = "com.tneff.cyppie"
version = "1.0.0"
application {
    mainClass = "com.tneff.cyppie.ApplicationKt"
}

dependencies {
    api(projects.core)
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    // KAN-112 key-proxy (ADR-0021): the server forwards to Alchemy/RPC over a Ktor client (CIO engine),
    // injecting the API key server-side so no key ships in the app binary.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    // KAN-125 (ADR-0022): parse the JSON-RPC method for the read/broadcast-only allow-list.
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlin.testJunit)
}

// KAN-112: bridge the machine-wide `alchemyApiKey` gradle property (set in ~/.gradle/gradle.properties,
// never committed) into the server runtime as a JVM system property, so `./gradlew :server:run` serves
// the proxy locally without a key ever touching the repo. In deploy, the key comes from $ALCHEMY_API_KEY.
tasks.withType<JavaExec>().configureEach {
    providers.gradleProperty("alchemyApiKey").orNull?.let { systemProperty("alchemyApiKey", it) }
}
