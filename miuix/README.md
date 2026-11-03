# Vendored miuix (glass branch)

Source: compose-miuix-ui/miuix, branch `feat/miuix-glass` (upstream PR #423, draft), Apache-2.0 (see LICENSE).
The glass modules are not on Maven yet and the branch also changes miuix-ui / miuix-blur, so the Android slice
is built from source: core, shader, squircle, blur, ui, preference, icons, glass-icons, glass.
Only `commonMain` + `androidMain` were copied; each module's build script is reduced to an Android-only target.
To update: replace the `src/` folders with the newer branch contents.
