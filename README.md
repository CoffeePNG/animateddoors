# AnimatedDoors

Usable animated doors for **Paper / Purpur 1.21.11** (Java 21). Doors either **swing** open around a
vertical hinge or slide up/down as a **portcullis**, animated smoothly with `BlockDisplay`, and can be
triggered by **redstone** or by **floating click-triggers**.

This is a focused, from-scratch alternative to the "doors" slice of
[AnimatedArchitecture](https://modrinth.com/plugin/animatedarchitecture) — not a fork and shares no
code with it.

## Requirements

- Java 21+
- A Paper or Purpur server for Minecraft **1.21.11** (any 1.21.x Paper-based server should work; `api-version` is `1.21`)

## Build

```bash
mvn package
```

The plugin jar is produced at `target/AnimatedDoors-0.1.0.jar`. Drop it in your server's `plugins/`
folder and restart.

## How it works

- A door is an explicit **set of blocks** you pick — not a box. Only the blocks you selected move, so a
  wall, floor or roof that happens to share the door's bounding box is never dragged along. Each door
  has a **type**: `swing` or `portcullis`.
  - **Swing** doors rotate 90° around a **hinge** (a vertical column) in a chosen direction. The client
    interpolates a true arc, and block facing (stairs, logs, fences, signs, …) is rotated to match,
    best-effort.
  - **Portcullis** doors slide straight up or down by a set number of blocks.
- During a move the real blocks are removed and rendered by per-block `BlockDisplay` entities; the real
  blocks are placed at their destinations when the animation finishes.

## Quick start

1. `/door wand` — grab the selection wand (a blaze rod by default). It starts in **block mode**.
2. **Right-click** each block that belongs to the door; **left-click** a block to drop it again. Every
   pick refreshes a glowing outline so you can see the door taking shape.
   - Big flat gate? **Shift-left-click** and **shift-right-click** two corners, then `/door add` to take
     the whole box at once (air is skipped), and left-click the few blocks that don't belong.
   - `/door sub` subtracts a corner box; `/door clear` starts over.
   - Prefer the old behaviour? `/door mode region` gives you the plain two-corner box.
3. `/door finish` — **preview**: the exact blocks that will move light up. Wrong ones in there? Left-click
   them and run it again.
4. `/door create mygate` — create the door from the previewed selection (warns if it holds filled containers).

**For a swing door (default):**

5. `/door hinge mygate` — look at the block the door should pivot around, then run it.
6. `/door direction mygate cw` — set swing direction (`cw` or `ccw`; flip it if it swings the wrong way).
7. `/door preview mygate` — watch a **ghost** of the door swing, without touching a single real block.
8. `/door toggle mygate` — do it for real.

**For a portcullis:**

5. `/door type mygate portcullis` — switch it to vertical-slide motion (defaults to sliding up by its own height).
6. `/door slide mygate 5` — optional: set the distance (`+` up, `−` down). Setting a slide also makes it a portcullis.
7. `/door preview mygate` — ghost-run the slide first.
8. `/door toggle mygate` — do it for real.

### Previews

Previews are drawn with `BlockDisplay` ghosts and are visible **only to you**; no real block is ever moved
by one.

- `/door finish` — outline the blocks currently selected. This is the "is my selection actually the door?"
  check.
- `/door preview <name> [open|close]` — ghost-run an existing door's move. Also reports how many blocks
  sit where the door would land and would be overwritten by a real toggle.
- The outline also refreshes automatically after each wand click (turn it off with `preview.enabled`).

### Fixing an existing door

`/door edit <name>` loads a door's blocks back into your selection (in block mode, outlined), so you can
left-click the strays out and right-click missing blocks in. `/door update <name>` writes the selection
back to the door. Both require the door to be closed.

### Triggers

- **Redstone:** look at a block and run `/door trigger mygate redstone`. Powering that block (lever,
  button, redstone signal) toggles the door on the rising edge.
- **Floating click-trigger:** look at a spot and run `/door trigger mygate float`. This places an
  invisible, floating clickable zone there — right-click it to toggle. Handy for a doorknob-style hotspot
  in mid-air.
- **Click the door itself:** right-clicking any block of the door toggles it (toggle with
  `triggers.click-door-to-toggle` in the config).
- `/door trigger mygate clear` — remove this door's triggers.

## Commands

| Command | Description |
| --- | --- |
| `/door wand` | Get the selection wand |
| `/door mode <block\|region>` | Pick blocks one by one, or use a two-corner box |
| `/door add` | Add the wand's corner box to your picks (skips air) |
| `/door sub` | Subtract the wand's corner box from your picks |
| `/door finish` | Preview exactly which blocks will move |
| `/door clear` | Clear your selection |
| `/door create <name>` | Create a door from your selection |
| `/door edit <name>` | Load a door's blocks back into your selection |
| `/door update <name>` | Replace a door's blocks with your selection |
| `/door preview <name> [open\|close]` | Ghost-run the move without touching blocks |
| `/door type <name> <swing\|portcullis>` | Choose swing or vertical-slide motion |
| `/door hinge <name>` | (swing) Set the hinge to the block you're looking at |
| `/door direction <name> <cw\|ccw>` | (swing) Set the opening direction |
| `/door slide <name> <blocks>` | (portcullis) Set vertical distance (+up / −down) |
| `/door trigger <name> <redstone\|float\|clear>` | Manage triggers |
| `/door toggle <name>` | Open/close a door |
| `/door info <name>` | Show a door's details |
| `/door list` | List all doors |
| `/door remove <name>` | Delete a door |
| `/door reload` | Reload the config |

Aliases: `/adoor`, `/animateddoor`.

## Permissions

| Permission | Default | Grants |
| --- | --- | --- |
| `animateddoors.admin` | op | Create/edit/remove doors (includes `use`) |
| `animateddoors.use` | op | Commands like `list` and `info` |
| `animateddoors.toggle` | everyone | Toggle doors via triggers / clicking |

## Configuration (`config.yml`)

```yaml
animation:
  duration-ticks: 40   # total swing time (20 ticks = 1s)
  step-ticks: 2        # ticks between animation keyframes
triggers:
  click-door-to-toggle: true
  cooldown-ticks: 10   # min ticks between toggles of the same door
selection:
  wand-material: BLAZE_ROD
  default-mode: block  # block = pick blocks individually, region = two-corner box
preview:
  enabled: true
  duration-ticks: 200        # how long /door finish stays up
  live-duration-ticks: 100   # outline shown after each wand click
  hold-ticks: 40             # how long a ghost lingers at its destination
  max-blocks: 2000           # selections bigger than this aren't previewed
```

## Known limitations (tier-two scope)

- Two motion types are supported: swing (90° around a vertical hinge) and portcullis (vertical slide).
  Drawbridges, revolving doors, and other structure types are out of scope here.
- Tile-entity **contents** are not preserved by a move. To protect them, a door refuses to move while
  any container inside it (chest, barrel, furnace, hopper, shulker box, …) still holds items — empty it
  first, or disable the guard with `restrictions.block-filled-containers: false`. Empty containers move
  freely. Sign text is still not preserved.
- A move overwrites whatever occupies the destination cells, so leave the door's path (swing arc or slide
  column) clear. `/door preview` counts the blocks in the way before you find out the hard way.
- Doors created before per-block selection were stored as a box; they load as every block in that box and
  are rewritten in the new per-block format on the next save. Run `/door edit`/`/door update` on them to
  trim anything that was never part of the door.
