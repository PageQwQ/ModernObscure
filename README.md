<div align="center"><center>

# ModernObscureCompat

[![License](https://img.shields.io/github/license/PageQwQ/ModernObscure?style=flat)](https://github.com/PageQwQ/ModernObscure)
[![Available for Fabric](https://img.shields.io/badge/Available%20for-Fabric-dbd0b4)](https://fabricmc.net)
[![Available for Forge](https://img.shields.io/badge/Available%20for-Forge-e3e0d8)](https://files.minecraftforge.net)
[![MC 1.20.1](https://img.shields.io/badge/MC-1.20.1-ff69b4)]()

A multi-loader (Fabric & Forge) Minecraft 1.20.1 mod that bridges ModernUI's rounded SDF tooltip backgrounds with obscure-tooltips' feature-rich tooltip system.

The mod requires [Obscure Tooltips](https://modrinth.com/mod/obscure-tooltips) and [Fragmentum](https://modrinth.com/mod/fragmentum), and recommends [ModernUI](https://modrinth.com/mod/modern-ui).

![preview](/images/preview.png)

</center></div>

## Features

- **ModernUI Rounded Backgrounds** — Replaces obscure-tooltips' default panel/frame rendering with ModernUI's SDF (Signed Distance Field) rounded background, including shadow, border, and rainbow color cycling support.
- **obscure-tooltips Compatibility** — Retains all obscure-tooltips features: armor preview, tool preview, particles, effects (rim light, glow, shimmer), and scrollable tooltips.
- **Slot Texture** — Draws a subtle item-slot background behind the item icon in tooltip headers.
- **AppleSkin Compatible** — Hunger/saturation bars keep rendering correctly on top of the rounded background.
- **Graceful Fallback** — When ModernUI is not installed, tooltips fall back to obscure-tooltips' default rendering.

## Requirements

| Dependency | Required | Purpose |
|---|---|---|
| [obscure-tooltips](https://modrinth.com/mod/obscure-tooltips) | Yes | Tooltip rendering (3.10.0 for 1.20.1) |
| [Fragmentum](https://modrinth.com/mod/fragmentum) | Yes | Library used by obscure-tooltips |
| [ModernUI](https://modrinth.com/mod/modern-ui) | No (recommended) | Rounded SDF background; without it the mod falls back to obscure-tooltips' default rendering |

## Installation

1. Install the required mods above, plus [Fabric Loader](https://fabricmc.net/use/installer/) or [Forge](https://files.minecraftforge.net/).
2. Drop the matching `modernobscure-compat-*.jar` into the `mods/` folder.
3. Launch the game.

## Building from Source

Requires JDK 17 and the 1.20.1 dependency jars in `libs/` (already included in this repository):

- `obscure_tooltips-fabric-1.20.1-3.10.0.jar`, `fragmentum-fabric-1.20.1-1.5.2.jar`
- `obscure_tooltips-forge-1.20.1-3.10.0.jar`, `fragmentum-forge-1.20.1-1.5.2.jar`

```bash
git clone https://github.com/PageQwQ/ModernObscure.git
cd ModernObscure
git checkout 1.20.1
JAVA_HOME=/path/to/jdk-17 ./gradlew build
```

Built JARs:

- `fabric/build/libs/modernobscure-compat-fabric-1.3.0.jar`
- `forge/build/libs/modernobscure-compat-forge-1.3.0.jar`

> Looking for Minecraft 1.21.1 (Fabric & NeoForge)? Check out the `main` branch.

## Technical Notes

- Multi-loader project using Architectury Loom: shared code lives in `common/`, with per-loader `fabric/` and `forge/` modules for loader-specific metadata
- All ModernUI access is through Java reflection, so there is no compile-time dependency on ModernUI
- ThreadLocal guards prevent double-rendering when both mods try to render the same tooltip
- The SDF background uses `GL_ALWAYS` depth function to prevent AppleSkin bars from being rejected by the depth buffer
- AppleSkin bars are re-rendered after the SDF flush with clean GL state; reflection is used because AppleSkin's `FoodTooltipRenderer` is package-private and its method name differs between loaders
- `MixinHeaderComponent` is split per loader because the Fabric jar of obscure-tooltips names the slot-drawing method `method_32666` (intermediary) while the Forge jar uses the SRG name `m_183452_`
- Forge 1.20.1 registers mixin configs via the jar manifest `MixinConfigs` attribute (the `mods.toml` `[[mixins]]` section is not parsed); the Forge jar also ships a `pack.mcmeta` because Forge silently drops mod packs that lack one
- The `ModelViewStack` depth is tracked and restored to prevent crashes from unbalanced push/pop in ModernUI's internal methods
