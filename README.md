# Fog Control (Forge 1.20.1)

Server-controlled terrain fog. Ops change it with a command, and it syncs to every player.
Fog goes from a **minimum** amount at the camera to a **maximum** amount exactly at the render-distance edge.

## Build
Part of the FRS mod framework (`framework/` submodule, Stonecutter node `1.20.1-forge`). Clone with
`git clone --recurse-submodules`, or run `git submodule update --init` after cloning.
```
./gradlew build          (Windows: gradlew.bat build)
```
Output: `versions/1.20.1-forge/build/libs/fogcontrol-<version>+1.20.1-forge.jar`. Needs a local JDK 25 for
Gradle (the mod itself is compiled for Java 17). Releases: `framework/bump.bat` (Windows) or `framework/bump.sh`.

Install the jar on the **server** (for control) and on **clients** (to see the effect).
Clients without it can still join and see vanilla fog.

## Commands
| Command | Who | Effect |
|---|---|---|
| `/fogcontrol` | anyone | Show current settings |
| `/fogcontrol min <0-100>` | op | Fog amount right at the camera |
| `/fogcontrol max <0-100>` | op | Fog amount at the render-distance edge |
| `/fogcontrol set <min> <max>` | op | Both at once |
| `/fogcontrol reset` | op | Defaults: min 0, max 70 |
| `/fogcontrol color` | anyone | Show color settings |
| `/fogcontrol color toggle` | op | Switch custom fog color on/off (also `color on` / `color off`) |
| `/fogcontrol color rgb <r> <g> <b>` | op | Set the color (0-255 each); turns custom color on |
| `/fogcontrol color hex <#RRGGBB>` | op | Set the color, e.g. `#88AACC`; turns custom color on |
| `/fogcontrol transition_speed` | anyone | Show how long transitions take |
| `/fogcontrol transition_speed <ticks>` | op | How long fog and sky take to blend to new settings (20 ticks = 1 s, 0 = instant, default 200 = 10 s) |
| `/fogcontrol sky` | anyone | Show sky color settings |
| `/fogcontrol sky toggle` | op | Switch custom sky color on/off (also `sky on` / `sky off`) |
| `/fogcontrol sky rgb <r> <g> <b>` | op | Set the sky color (0-255 each); turns custom sky color on |
| `/fogcontrol sky hex <#RRGGBB>` | op | Set the sky color, e.g. `#78A7FF`; turns custom sky color on |

Min can't be set higher than max. Settings are saved per world in `<world>/serverconfig/fogcontrol-server.toml`.
To set defaults for new worlds, copy that file into the server's `defaultconfigs/` folder.

Examples:
- `/fogcontrol set 0 100` - clear up close, fully opaque at the edge
- `/fogcontrol set 50 100` - half-foggy even up close, very dense
- `/fogcontrol set 30 30` - uniform 30% haze at every distance
- `/fogcontrol set 0 40` - light haze toward the edge

## How it works
A mixin at the end of `FogRenderer.setupFog` replaces the terrain fog start/end.
Vanilla and Embeddium both compute fog as `smoothstep(start, end, distance)`.
The mod picks a (negative) fog start and a fog end so that the curve gives exactly
`min` at distance 0 and exactly `max` at the render distance.
In between, the fog follows a section of that smooth S-curve.

Embeddium reads fog start/end from `RenderSystem`, so the same values apply to its chunk shaders.

### Custom color
When custom color is on, a third injection replaces vanilla's fog color at the end of `setupColor`.
That one color feeds the terrain fog, the sky's horizon blend and the background, and Embeddium and
Distant Horizons read it too. The color is treated as the *daytime* color: it darkens at night (following the dimension's own
brightness rule) but is independent of weather, so it keeps matching the custom sky color in rain.
A hook at the end of `levelFogColor` re-sends the custom color to the shaders, so other mods'
weather tints (e.g. Dynamic Surroundings' overcast shift) can't change it.
It only applies where Fog Control's fog is active, so water, lava, blindness and Nether fog keep their colors.

### Transitions
When new settings arrive, each client blends from what's on screen to the new values over
`transitionTicks` (eased in and out, measured in real time: 1 tick = 1/20 s). This covers fog min/max,
fog color and sky color; switching a custom color on or off fades from/to the live vanilla color.
A change during a transition starts from the current blend. Joining a server applies settings instantly.

### Custom sky color
`ClientLevel.getSkyColor` is hooked at its return value and recomputed from the custom color with vanilla's
time-of-day and lightning-flash steps. Rain and thunder don't change it, so sky and fog stay matched.
The vanilla sky dome, the sky tint in vanilla fog, Distant Horizons and shader packs (Oculus `skyColor`
uniform; Complementary builds its sky from it) all follow.
The hook runs at mixin priority 800, before Dynamic Surroundings: Rebirth's own getSkyColor override
(priority 900, which ends the method when it sets its value). With a custom sky color on, Fog Control's
color wins; with it off, Dynamic Surroundings' weather sky works as usual. Embeddium redirects the biome lookup inside
that method, which is why the hook works on the result rather than the lookup.

A second injection removes vanilla's sunset tint on the fog (the fog turning orange when you face the sun at sunrise/sunset).
The sunset glow in the sky itself is unchanged.

## Distant Horizons (optional)
If Distant Horizons is installed, Fog Control lines up DH's fog with its own:
- DH normally hides vanilla fog. While you're on a Fog Control server, the mod turns DH's
  "Enable Vanilla Fog" on through DH's API. This is an in-memory override: your DH config file
  isn't changed, and the override is removed when you disconnect.
- DH's far-fog **minimum thickness** is set to Fog Control's **max** value, and DH's fog is never
  allowed to start before the vanilla render-distance edge, so the two meet without a visible seam.
- DH's own far-fog falloff, end distance and max thickness still come from DH's settings.
  Set DH's *Far Fog Start* to 0 if you want DH fog to keep thickening right from the vanilla edge;
  with DH's default (0.4) it stays flat at your max value until 40% of the DH render distance.

The build compiles against the DH API from Modrinth's Maven (compile-only; it isn't bundled or required).
Without DH installed, the integration simply does nothing.

## Shader packs (Oculus)
Shader packs replace Minecraft's fog with their own, so the pack has to read Fog Control's values.
Oculus passes them to every pack as the `fogStart`, `fogEnd` and `fogColor` uniforms.
Fog Control always sets `fogStart <= 0` (vanilla terrain fog never does), so a patched pack can
detect the mod and fall back to its own fog on other servers.
A patch for Complementary Unbound r5.8.1 is provided separately (`apply_fogcontrol_patch.py`).

Left vanilla on purpose: the sky, water, lava, powder snow, blindness, darkness, and Nether/boss fog.
