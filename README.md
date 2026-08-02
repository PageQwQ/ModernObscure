# ModernObscure

A Fabric mod compatibility layer for Minecraft 1.21.1 that bridges ModernUI's rounded SDF tooltip backgrounds with obscure-tooltips' feature-rich tooltip system.

## Features

- **ModernUI Rounded Backgrounds** — Replaces obscure-tooltips' default panel/frame rendering with ModernUI's SDF (Signed Distance Field) rounded background, including shadow, border, and rainbow color cycling support
- **Full obscure-tooltips Compatibility** — Retains all obscure-tooltips features: armor preview, tool preview, particles, effects (rim light, glow, shimmer), and scrollable tooltips
- **Item Slot Texture** — Custom item slot border rendered on top of the SDF background for clear visual separation
- **AppleSkin Integration** — Correctly renders AppleSkin food hunger/saturation bars at the bottom of tooltips
- **Graceful Fallback** — When ModernUI is not installed, tooltips fall back to obscure-tooltips' default rendering

## Requirements

- **Fabric Loader** >= 0.16.9
- **Minecraft** 1.21.1
- **Java** >= 21

### Optional Dependencies

- [ModernUI-MC](https://github.com/CyclopsMC/ModernUI) (>= 3.13) — Enables rounded SDF tooltip backgrounds
- [obscure-tooltips](https://github.com/Obscuria/obscure-tooltips) (>= 4.2.2) — Required for tooltip features
- [fragmentum](https://github.com/Obscuria/fragmentum) (>= 2.1.0) — Required by obscure-tooltips
- [AppleSkin](https://github.com/squeek502/AppleSkin) — Food bar integration in tooltips

## Building from Source

```bash
git clone https://github.com/PageQwQ/ModernObscure.git
cd ModernObscure

# Place required dependencies in libs/ directory:
# - obscure_tooltips-fabric-1.21.1-4.2.2.jar
# - fragmentum-fabric-1.21.1-2.1.0.jar

JAVA_HOME=/path/to/jdk-21 ./gradlew build
```

The built JAR will be in `build/libs/modernobscure-compat-1.0.0.jar`.

## Technical Notes

- All ModernUI access is through Java reflection, so there is no compile-time dependency on ModernUI
- ThreadLocal guards prevent double-rendering when both mods try to render the same tooltip
- The SDF background uses `GL_ALWAYS` depth function to prevent AppleSkin bars from being rejected by the depth buffer
- ModelViewStack depth is tracked and restored to prevent crashes from unbalanced push/pop in ModernUI's internal methods

## License

Apache-2.0