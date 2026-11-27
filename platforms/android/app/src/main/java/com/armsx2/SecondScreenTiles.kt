package com.armsx2

/**
 * What the second-screen panel shows, and in what order (NiceRon: "I'd love to be able to
 * customize what options are being shown... a grid which can be filled with boxes containing the
 * things one need like quick save/load, fps info, fast forward, aspect ratio and so on").
 *
 * A declared registry rather than hard-coded rows in [SecondScreen] so the panel builder is one
 * loop over the user's list, and so the settings UI can enumerate the choices without knowing
 * anything about them. Adding a tile is one entry here plus one `when` branch in the panel.
 *
 * Where each tile sits is stored by [SecondScreenLayout] (SecondScreenGrid.kt in this fork).
 */
enum class SecondScreenTile(val id: String, val labelKey: String, val stat: Boolean = false,
    /**
     * A glyph shown above the label on ACTION tiles, so a tile can be recognised at a glance
     * from across a desk -- "much easier to quickly recognize pictures than text" (NiceRon).
     *
     * Deliberately geometric Unicode rather than emoji: emoji render in their own colours and
     * their own house style, which is exactly the stock-Android look the panel was being
     * restyled away from. These take the theme accent like everything else.
     *
     * Stat tiles leave this empty -- their label IS the identifier, and a value needs the
     * second line.
     */
    val icon: String = "",
) {
    // Read-outs. These fill their box with live text and ignore taps.
    TITLE("title", "secondScreen.tile.title", stat = true),
    FPS("fps", "secondScreen.tile.fps", stat = true),
    SPEED("speed", "secondScreen.tile.speed", stat = true),
    BATTERY("battery", "secondScreen.tile.battery", stat = true),
    CLOCK("clock", "secondScreen.tile.clock", stat = true),
    ACHIEVEMENTS("achievements", "secondScreen.tile.achievements", stat = true),
    // Thermals (Cotcho, Mike22). Stat tiles like the rest -- a device with no readable zone
    // simply shows a dash rather than the tile being hidden, so the grid does not reflow
    // depending on what the kernel happens to expose.
    // The game's cover, and more of what RetroAchievements already knows. The panel had the
    // title as text and a bare unlocked-count; on a screen sitting beside you, the cover is what
    // makes it read as "this game" at a glance.
    COVER("cover", "secondScreen.tile.cover", stat = true),
    RA_POINTS("rapoints", "secondScreen.tile.raPoints", stat = true),
    RA_RECENT("rarecent", "secondScreen.tile.raRecent", stat = true),
    RICH_PRESENCE("presence", "secondScreen.tile.presence", stat = true),

    // The rest of what the in-game OSD shows (Mike22). Backed by new JNI getters -- until those
    // existed, FPS was the only figure the panel could reach.
    VPS("vps", "secondScreen.tile.vps", stat = true),
    CPU_LOAD("cpuload", "secondScreen.tile.cpuLoad", stat = true),
    GS_LOAD("gsload", "secondScreen.tile.gsLoad", stat = true),
    GPU_LOAD("gpuload", "secondScreen.tile.gpuLoad", stat = true),
    FRAME_TIME("frametime", "secondScreen.tile.frameTime", stat = true),
    CPU_TEMP("cputemp", "secondScreen.tile.cpuTemp", stat = true),
    GPU_TEMP("gputemp", "secondScreen.tile.gpuTemp", stat = true),
    BATTERY_TEMP("battemp", "secondScreen.tile.batteryTemp", stat = true),

    // Actions.
    SAVE("save", "touch.stateAction.save", icon = "▼"),
    LOAD("load", "touch.stateAction.load", icon = "▲"),
    FAST_FORWARD("ff", "secondScreen.fastForward", icon = "▶▶"),
    PAUSE("pause", "secondScreen.pause", icon = "❚❚"),
    SCREENSHOT("screenshot", "touch.stateAction.screenshot", icon = "◉"),
    ASPECT("aspect", "secondScreen.tile.aspect", icon = "▭"),
    SLOT("slot", "secondScreen.tile.slot", icon = "▣"),
    // The way out from the panel itself — asked for after the panel landed on the display the game
    // was running on, with no way to dismiss it from there (BrainBeat: "I wonder if there is a way
    // to toggle it on inside the panel"). Turns the whole feature off, same as the App setting.
    HIDE("hide", "secondScreen.tile.hide", icon = "✕"),
    // "Not on THIS screen" as distinct from "off entirely" — the panel is the only place that
    // knows which display it landed on, so the opt-out belongs on it.
    NOT_HERE("nothere", "secondScreen.tile.notHere", icon = "⤫"),

    MACRO1("macro1", "secondScreen.tile.macro1", icon = "①"),
    MACRO2("macro2", "secondScreen.tile.macro2", icon = "②"),
    MACRO3("macro3", "secondScreen.tile.macro3", icon = "③"),
    MACRO4("macro4", "secondScreen.tile.macro4", icon = "④"),

    // Fork (see PANNEAU-ECRAN-BAS.md): switches the internal resolution between x2 and x3, live.
    // The label is the text itself: I18n falls back to the key, so no translation table changes.
    RESOLUTION("resolution", "Résolution", icon = "▦"),
}
