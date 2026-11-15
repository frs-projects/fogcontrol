# Changelog

All notable changes to Fog Control. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions are the `mod.version` released with `framework/bump.sh` / `framework/bump.bat`.

## [Unreleased]

### Added
- `/fogcontrol transition_speed <ticks>`: fog amount, fog color and sky color fade to new settings
  instead of switching at once (default 200 ticks = 10 s, 0 = instant). Synced to clients.

### Changed
- Moved to the FRS mod framework (Stonecutter node `1.20.1-forge`). The Java package is now
  `net.frsprojects.fogcontrol`; the mod id stays `fogcontrol`, so configs and servers keep working.
- The Distant Horizons API comes from Modrinth's Maven instead of a jar in `libs/`.
- Fog curve math, color parsing and transition blending live in a pure-Java `:core` module with
  JUnit tests.
- Custom fog and sky colors no longer change with rain or thunder, so they stay matched in any
  weather. They still follow day and night, and lightning still flashes the sky.

### Fixed
- **Custom sky color had no effect with Dynamic Surroundings: Rebirth.** Its `getSkyColor` hook
  (priority 900) ended the method before ours ran; ours now runs first (priority 800).
- **Dynamic Surroundings tinted the custom fog color** toward grey-white during its weather sky
  events. The custom color is now sent to the shaders again after it.
- **Transitions stalled and ran long after a command.** Every config file write makes Forge reload
  the config, and every reload re-sent the settings, so one command sent them up to four times and
  each copy restarted the fade. Clients now ignore settings they already have.
- `customColor` / `skyColor` in the config accepted a sign (`#+12345`); colors must be six hex digits.

## [1.1.0]

### Added
- Server-controlled terrain fog: `/fogcontrol min|max|set|reset` sets the fog amount at the camera
  and at the render-distance edge (op level 2), saved per world and synced to every player.
- Custom fog color and sky color (`/fogcontrol color …`, `/fogcontrol sky …`).
- Distant Horizons: its fog continues from where the vanilla fog ends.
- Fog values passed to shader packs (Oculus) for packs edited to read them.
