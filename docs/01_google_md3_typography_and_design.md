# Material 3 design system

> Status: Proposed; implemented parts are called out explicitly. Last verified: 2026-09-20.

Production targets Material 3 `1.4.0` and Material 3 Adaptive `1.3.0`; alpha APIs are excluded. Material 3 is active. Adaptive is pinned but awaits compileSdk 37/AGP 9.1 because the current compileSdk 35/AGP 8.7.3 toolchain cannot consume it. Dynamic system color is global. Artwork-derived HCT color is local to Player/MiniPlayer and must not recolor navigation.

Roboto Flex must ship as a licensed local variable-font resource. Until its binary is added, use a platform sans-serif fallback. Open Sans is Open Sans and must never be labelled Google Sans.

| Family | Source | Rule |
|---|---|---|
| Type | all 15 `MaterialTheme.typography` roles | no raw `sp`, except documented karaoke/timecode tokens |
| Color | `MaterialTheme.colorScheme` | no feature hardcodes; player accent stays local |
| Shape | 4, 8, 12, 16, 28 dp roles | components consume semantic roles |
| Space | 4, 8, 12, 16, 24, 32 dp | use the smallest role preserving hierarchy |
| Motion standard | 100/200/300 ms | navigation and ordinary state changes |
| Motion expressive | 220–450 ms | MiniPlayer→Player, artwork, play/pause, favorite, active lyric only |

Reduced-motion/system settings disable non-essential motion. Ordinary rows/tabs do not bounce. Targets are at least 48×48 dp and expose labels/state descriptions.

Current: complete MD3 type scale and dynamic theme exist; provider fonts and hardcoded feature values remain. Target: local Roboto Flex, semantic tokens, scoped expressive motion.

Acceptance: offline first frame uses bundled font; 200% font scale remains operable; TalkBack finds no empty actions or color-only states; track changes do not recompose the whole navigation tree.

Source: [Material 3 releases](https://developer.android.com/jetpack/androidx/releases/compose-material3).
