# Particle Muter — Design

## 1. What it is

The user, 2026-11-23: "I want to make a mod simliar to the popular sound control mod that let's you disable specific
minecraft/mod sounds inside your inventory UI by pressing the button. But this mod will be for particles instead, so
that the user can disable any specific particles from rendering, minecraft or modded. And the mod will have the same
sort of UI button that the sound one has, in the same location, or shifted down one if the sound mod is also
isntalled."

The sound mod is **Extreme Sound Muffler** (ESM, `extremesoundmuffler`, about 94 million downloads; research in
`docs/research/sound_mod_ui.md`). Particle Muter is its counterpart for particles: a client-side mod, nothing on the
server, nothing added to the game.

The gap it fills (`docs/research/existing_particle_mods.md`): Sodium Extra and Embeddium Extra have per-type switches
but need Sodium or Embeddium and live in a video-settings page; Particle Core takes ids typed into a config; the
other particle filters are tiny and on one version each. No mod opens a particle list from the inventory, shows what
is spawning around you, or covers 1.20.1, 1.21.1 and 26.x together.

## 2. Versions

| Branch | Minecraft | Loader | Java | Test instance |
|---|---|---|---|---|
| `main` | 26.3 | NeoForge 26.3.0.115 | 25 | ParticleMuter26.3 |
| `26.1.2` | 26.1.2 | NeoForge 26.1.2.129 | 25 | ParticleMuter26.1.2 |
| `1.21.1` | 1.21.1 | NeoForge 21.1.267 | 21 | ParticleMuter1.21.1 |
| `1.20.1` | 1.20.1 | Forge 47.4.28 | 17 | ParticleMuter1.20.1 |

The instances are the user's (CurseForge group "Particle Muter"). Each build deploys to its own one only
(`particlemuter_instance_dir`, `_26_1_2`, `_1_21_1`, `_1_20_1` in `~/.gradle/gradle.properties`).

## 3. The inventory button

- 11x11, the sound mod's size and look (a small bevelled vanilla-style button; ours carries a spark of particles
  crossed by a red slash). Its name, "Particles", shows just below it under the mouse, as ESM shows "Muffler".
- **Survival inventory**: ESM's place, leftPos+75, topPos+7 (the top right corner of the player's box).
  **Creative inventory**: only on its own tab (Survival Inventory), at leftPos+2, topPos+2.
- **With ESM installed**: ours goes 12 px below ESM's own widget (its 11 px and a 1 px gap), found among the screen's
  widgets by its class's package (`com.leobeliik.extremesoundmuffler`), so it follows ESM's button wherever the player
  dragged it, on both ESM lines (3.x `InvButton`, 4.x `ESMInv`).
- Its place is worked out every frame from the inventory's own place (before the screen draws), so it moves with the
  recipe book; on a narrow window with the book open the game itself hides and disables it, as it does ESM's.
- A left click opens the list (and closing the list goes back to the inventory); a right-button drag moves it, the
  place kept in the settings (as ESM's). 26.3's right button is 3 (SDL3), the others' 1: `InputConstants.MOUSE_BUTTON_RIGHT`.
- Also: a key (unbound by default, as ESM's), and the mod list's Config button.

## 4. The screen

Modelled on ESM's (4.x): a panel in the middle, the game going on behind it (it does not pause), so a change shows in
the world at once.

- **Lists** along the top: **Recent** (particles made since the game started, busiest first, re-sorted each
  second), **All** (every particle, under a heading per mod; a heading's switch mutes the whole mod), **Muted**,
  **Mods** (one row per mod: mute or unmute all of its particles). The list chosen is green, as ESM's.
- **Rows**: the particle's picture (the first sprite of its sprite set, as it looks in the world; an item standing in
  for the few drawn some other way), its name, how many are being made around you each second (averaged over five
  seconds), and the toggle (the button: slashed when muted). A click anywhere on the row toggles it. Muted rows: name
  green and picture dimmed, as ESM marks muffled sounds. A tooltip gives the id and the mod.
- **Bottom**: the search (name, id or mod; a right click empties it, as ESM's), the master switch (**On**: muted
  particles are hidden; **Off**: everything shows, the list kept, as ESM's start/stop muffling), and **Unmute all**
  (a second click to be sure).
- Names: our own for every vanilla particle (`particlemuter.particle.<ns>.<path>`), then the names mods give theirs
  for Sodium Extra (`options.particles.<ns>.<path>`), then the id made readable.
- Three rows have no particle type: **Block breaking**, **Block mining** (the bits flying off a block being mined) and
  **Item pickup** (items flying to whoever picks them up), as Sodium Extra offers the first two.

## 5. How muting works (`docs/research/particle_tech.md`)

- Every typed particle passes `ParticleEngine#createParticle`, the same on all four versions: the mod wraps its call
  to `add(particle)` (MixinExtras `@WrapWithCondition`), so a muted particle is made but never added, never ticked or
  drawn. `createParticle` still returns it: fireworks use their result with no null check (Sodium Extra's null return
  needs a guard for that).
- Each `ParticleType` carries a muted flag and its index (a mixin), so the check is two field reads; no lookups, no
  allocation.
- Block breaking: the level's `addDestroyBlockEffect` cancelled at its head (26.x; `ParticleEngine#destroy` on
  1.21.1 and 1.20.1), which also skips a mod block's own effect. Block mining: the call to the block's `addHitEffects`
  answered "handled" (26.3; its hit sound kept), or `addBreakingBlockEffect` (26.1.2), `addBlockHitEffects` and `crack`
  (1.21.1, 1.20.1). Item pickup: the `add` in the take-item packet handler.
- The counts: a ring of one-second buckets of ints per entry, filled in the same hooks (muted particles are counted
  too, so the list shows what they would be doing), moved on by the client tick.
- When the screen closes after something was newly muted, the particles already flying are cleared, so a muted one
  goes at once rather than as it fades.
- Not covered (no mod can, short of each mod's own help): effects that mods draw outside the particle engine, and
  untyped particles a mod adds straight to the engine.

## 6. Settings

`config/particlemuter.json` (Gson; the same file on every version): the muted ids (kept even when their mod is not
installed), the three untyped rows, the master switch, the button's shown state and dragged places. Written through
a temporary file; a broken file is kept as `.bak` and the defaults used.

## 7. Testing

The prepared world **Particle Lab** (`./gradlew runLab`, `devworld/Lab`, left out of the jar): a row of particle
sources with a sign at each (campfires and signal fires, torches, soul torches, candles, redstone, lava with a drip,
water and crying obsidian drips, a nether portal, an enchanting table among bookshelves, a spawner, a bubble column,
cherry leaves and a spore blossom, end rods) and signs at the spawn saying where the button is. The run checks the
muting (the catalog, the names, the counts, the smoke gone from the world once muted, the block hook) and writes
`Lab check:` lines; it photographs the inventories and each list into `run/screenshots/lab_*.png`. `-PwithESM` runs it
with Extreme Sound Muffler, to see the two buttons together.

## Decision log

| Date | Decision |
|---|---|
| 2026-11-23 | The mod, in the user's words (§1). The sound mod is Extreme Sound Muffler; its button's size, place, tooltip, drag and screen are the model. |
| 2026-11-23 | Named **Particle Muter** by the user ("Particle Muter, and the instances are created in the particle Muter group"); zero conflicts on CurseForge and Modrinth. Mod id `particlemuter`, package `dev.particlemuter`, MIT, Lawman-afk. |
| 2026-11-23 | One hook for typed particles (`createParticle`'s `add`), not a null return (fireworks); flags on `ParticleType`; three extra rows for the untyped block-break, block-mining and item-pickup particles. |
| 2026-11-23 | With ESM installed the button follows ESM's own widget, 12 px below it; without, it takes ESM's default place. |
| 2026-11-23 | Settings in a Gson JSON (the same code on all four versions) rather than each loader's config. |
| 2026-11-24 | Ports: 26.1.2 and 1.21.1 from main, 1.20.1 (Forge) from the 1.21.1 branch, each by its port.py; 1.21.1 and 1.20.1 draw main's screen through stand-ins for 26.x's GUI and input; their block hooks are the engine's. MixinExtras packed in the 1.20.1 jar. |
| 2026-11-24 | The test world checks itself (6 checks: catalog, names, counts, smoke gone, muted still counted, block hook) and photographs the inventories (with Extreme Sound Muffler: its button, ours below) and every list. |
| 2026-11-24 | Logo: four examples in the clean utility style with the stripe (`docs/branding/examples/`), waiting for the user's pick. |

