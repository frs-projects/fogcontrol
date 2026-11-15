plugins {
    id("frs.forge")
}

dependencies {
    // Distant Horizons (LGPL-3.0, optional at runtime): compile only, for its API
    // (com.seibel.distanthorizons.api.*), which uses no Minecraft types. Plain compileOnly instead
    // of modCompileOnly, because the jar is a combined Fabric/Forge build Loom shouldn't remap.
    // All calls are in compat/DistantHorizonsCompat, loaded only when DH is installed.
    compileOnly("maven.modrinth:distanthorizons:3.1.2-b-1.20.1") { isTransitive = false }
}
