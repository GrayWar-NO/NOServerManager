plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "NOServerManager"

include("proto")
include("edge-agent")
include("db-manager")
include("proto")
include("db-manager")
include("edge-agent")