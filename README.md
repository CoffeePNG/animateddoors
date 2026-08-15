# AnimatedDoors

Usable animated doors for **Paper / Purpur 1.21.11** (Java 21). Doors swing open and closed with
smooth `BlockDisplay` animation and can be triggered by **redstone** or by **floating click-triggers**.

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

- A door is a box selection of blocks plus a **hinge** (a vertical column) and an **opening direction**.
- Toggling rotates the selection 90° around the hinge. During the swing the real blocks are removed and
  rendered by per-block `BlockDisplay` entities, which the client interpolates along a true arc; the real
  blocks are placed at their rotated destinations when the swing finishes.
- Block facing (stairs, logs, fences, signs, etc.) is rotated to match, best-effort.

## Quick start

1. `/door wand` — grab the selection wand (a blaze rod by default).
2. **Left-click** one corner of the door, **right-click** the opposite corner.
3. `/door create mygate` — create the door from the selection.
4. `/door hinge mygate` — look at the block the door should pivot around, then run it.
5. `/door direction mygate cw` — set swing direction (`cw` or `ccw`; flip it if it swings the wrong way).
6. `/door toggle mygate` — test the swing.

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
| `/door hinge <name>` | Set the hinge to the block you're looking at |
| `/door direction <name> <cw\|ccw>` | Set the opening direction |
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

- Doors swing in 90° steps around a vertical hinge (classic gate/door motion). Drawbridges, portcullises,
  and other structure types are out of scope here.
- Tile-entity **contents** are not preserved by a swing. To protect them, a door refuses to move while
  any container inside it (chest, barrel, furnace, hopper, shulker box, …) still holds items — empty it
  first, or disable the guard with `restrictions.block-filled-containers: false`. Empty containers move
  freely. Sign text is still not preserved.
- A swing overwrites whatever occupies the destination cells, so leave the door's opening arc clear.
