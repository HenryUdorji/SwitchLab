plugins { application }

dependencies {
    implementation(project(":iso"))
    implementation(libs.picocli)
}

application {
    mainClass = "io.switchlab.client.Cli"
    applicationName = "switchlab-client"
}
