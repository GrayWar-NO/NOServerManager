package com.graywar.noServerManager.dbManager

enum class Aircraft(val craft: String) {
    Cricket("CI-22 Cricket"),
    Compass("T/A-30 Compass"),
    Ibis("UH-90 Ibis"),
    Chicane("SAH-46 Chicane"),
    Brawler("A-19 Brawler"),
    Revoker("FS-12 Revoker"),
    Vortex("FS-20 Vortex"),
    Tarantula("VL-49 Tarantula"),
    Ifrit("KR-67 Ifrit"),
    Medusa("EW-25 Medusa"),
    Darkreach("SFB-81 Darkreach")
}


fun isAircraft(name: String): Boolean{
    return Aircraft.entries.any { it.craft == name }
}
