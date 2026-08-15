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

- A door is a box selection of blocks with a **type**: `swing` or `portcullis`.
  - **Swing** doors rotate 90° around a **hinge** (a vertical column) in a chosen direction. The client
    interpolates a true arc, and block facing (stairs, logs, fences, signs, …) is rotated to match,
    best-effort.
  - **Portcullis** doors slide straight up or down by a set number of blocks.
- During a move the real blocks are removed and rendered by per-block `BlockDisplay` entities; the real
  blocks are placed at their destinations when the animation finishes.

## Quick start

1. `/door wand` — grab the selection wand (a blaze rod by default).
2. **Left-click** one corner of the door, **right-click** the opposite corner.
3. `/door create mygate` — create the door from the selection (warns if the selection holds filled containers).

**For a swing door (default):**

4. `/door hinge mygate` — look at the block the door should pivot around, then run it.
5. `/door direction mygate cw` — set swing direction (`cw` or `ccw`; flip it if it swings the wrong way).
6. `/door toggle mygate` — test the swing.

**For a portcullis:**

4. `/door type mygate portcullis` — switch it to vertical-slide motion (defaults to sliding up by its own height).
5. `/door slide mygate 5` — optional: set the distance (`+` up, `−` down). Setting a slide also makes it a portcullis.
6. `/door toggle mygate` — test the slide.

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
| `/door create <name>` | Create a door from your selection |
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
```

## Known limitations (tier-two scope)

- Two motion types are supported: swing (90° around a vertical hinge) and portcullis (vertical slide).
  Drawbridges, revolving doors, and other structure types are out of scope here.
- Tile-entity **contents** are not preserved by a move. To protect them, a door refuses to move while
  any container inside it (chest, barrel, furnace, hopper, shulker box, …) still holds items — empty it
  first, or disable the guard with `restrictions.block-filled-containers: false`. Empty containers move
  freely. Sign text is still not preserved.
- A move overwrites whatever occupies the destination cells, so leave the door's path (swing arc or slide
  column) clear.
