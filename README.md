<div align="center"><center>

# ModernObscure

[![License](https://img.shields.io/github/license/PageQwQ/ModernObscure?style=flat)](https://github.com/PageQwQ/ModernObscure)
[![Available for Fabric](https://img.shields.io/badge/Available%20for-Fabric-dbd0b4?logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABoAAAAcBAMAAACNPbLgAAABhGlDQ1BJQ0MgcHJvZmlsZQAAKJF9kT1Iw0AcxV9TpX5UHMwgIpihOtlFRRxLFYtgobQVWnUwufQLmjQkKS6OgmvBwY/FqoOLs64OroIg+AHi6uKk6CIl/i8ptIj14Lgf7+497t4BQr3MNKsrAmi6bSZjUSmTXZUCr+iHiADG0Cszy4inFtPoOL7u4ePrXZhndT735xhQcxYDfBJxhBmmTbxBPLtpG5z3iUVWlFXic+JJky5I/Mh1xeM3zgWXBZ4pmunkPLFILBXaWGljVjQ14hnikKrplC9kPFY5b3HWylXWvCd/YTCnr6S4TnMUMSwhjgQkKKiihDJshGnVSbGQpP1oB/+I60+QSyFXCYwcC6hAg+z6wf/gd7dWfnrKSwpGge4Xx/kYBwK7QKPmON/HjtM4AfzPwJXe8lfqwNwn6bWWFjoCBreBi+uWpuwBlzvA8JMhm7Ir+WkK+TzwfkbflAWGboG+Na+35j5OH4A0dbV8AxwcAhMFyl7v8O6e9t7+PdPs7wd+dXKrd9SjeQAAAAlwSFlzAAAuIwAALiMBeKU/dgAAAAd0SU1FB+cLFAcgIbOcUjoAAAAbUExURQAAAB0tQTg0KoB6bZqSfq6mlLyynMa8pdvQtJRJT6UAAAABdFJOUwBA5thmAAAAAWJLR0QB/wIt3gAAAF5JREFUGNN10FENwCAMhOFqOQuzMAtYOAtYqGw6mkEvhL59yR9Ca5YDqyOC465eKYqQm6LoCkVwnwQOBYKdeA5l51zhFtrsnPmg6m3Z2akk15dFH1lWFQVxlUFv+2sAJlA9O7NwQRQAAAAASUVORK5CYII=)](https://fabricmc.net) [![Available for NeoForge](https://img.shields.io/badge/Available%20for-NeoForge-e3e0d8)](https://neoforged.net)

A multi-loader (Fabric & NeoForge) mod hat bridges ModernUI's rounded SDF tooltip backgrounds with obscure-tooltips' feature-rich tooltip system.

The mod requires [Obscure Tooltips](https://modrinth.com/mod/obscure-tooltips) and [Fragmentum](https://modrinth.com/mod/fragmentum), and recommends [ModernUI](https://modrinth.com/mod/modern-ui).

![preview](/images/preview.png)

</center></div>

## Features

- **ModernUI Rounded Backgrounds** — Replaces obscure-tooltips' default panel/frame rendering with ModernUI's SDF (Signed Distance Field) rounded background, including shadow, border, and rainbow color cycling support.
- **Obscure-tooltips Compatibility** — Retains all obscure-tooltips features: armor preview, tool preview, particles, effects (rim light, glow, shimmer), and scrollable tooltips.
- **AppleSkin Compatible** — Hunger/saturation bars keep rendering correctly on top of the rounded background.
- **Graceful Fallback** — When ModernUI is not installed, tooltips fall back to obscure-tooltips' default rendering.

## Building from Source

Requires JDK 21 (Gradle 8.10 does not support newer JDKs).

```bash
git clone https://github.com/PageQwQ/ModernObscure.git
cd ModernObscure

# Place required dependencies in libs/ directory:
# - obscure_tooltips-fabric-1.21.1-4.2.2.jar
# - fragmentum-fabric-1.21.1-2.1.0.jar
# - obscure_tooltips-neoforge-1.21.1-4.2.2.jar
# - fragmentum-neoforge-1.21.1-2.1.0.jar

JAVA_HOME=/path/to/jdk-21 ./gradlew build
```

Built JARs:
- `fabric/build/libs/modernobscure-compat-fabric-1.3.0.jar`
- `neoforge/build/libs/modernobscure-compat-neoforge-1.3.0.jar`

## Technical Notes

- Multi-loader project using Architectury Loom: shared code lives in `common/`, with per-loader `fabric/` and `neoforge/` modules for loader-specific metadata
- All ModernUI access is through Java reflection, so there is no compile-time dependency on ModernUI
- ThreadLocal guards prevent double-rendering when both mods try to render the same tooltip
- The SDF background uses `GL_ALWAYS` depth function to prevent AppleSkin bars from being rejected by the depth buffer
- AppleSkin bars are re-rendered after the SDF flush with clean GL state; `setAccessible(true)` is needed because NeoForge's `FoodTooltipRenderer` is package-private
- `MixinHeaderComponent` is split per loader because the Fabric jar of obscure-tooltips names the slot-drawing method `method_32666` (intermediary) while the NeoForge jar names it `renderImage` (Mojang)
- Only the Fabric mixin config carries a refmap (`modernobscure-compat-common-common-refmap.json`); NeoForge resolves Mojang-mapped names at runtime
- ModelViewStack depth is tracked and restored to prevent crashes from unbalanced push/pop in ModernUI's internal methods