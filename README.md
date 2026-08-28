# AnimatedDoors

Usable animated doors for **Paper / Purpur 1.21.11** (Java 21). Doors **swing** open around a vertical
hinge, slide up/down as a **portcullis**, or slide sideways as a **sliding** door, animated smoothly with
`BlockDisplay`, and can be triggered by **redstone** or by **floating click-triggers**.

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

- A door is a box selection of blocks with a **type**: `swing`, `portcullis` or `sliding`.
  - **Swing** doors rotate 90° around a **hinge** (a vertical column) in a chosen direction. The client
    interpolates a true arc, and block facing (stairs, logs, fences, signs, …) is rotated to match,
    best-effort.
  - **Portcullis** doors slide straight up or down by a set number of blocks.
  - **Sliding** doors slide sideways along a compass direction by a set number of blocks — a pocket door
    that retracts into the wall beside it, or a blast door that slides aside. Block facing is left alone,
    since nothing rotates.
- During a move the real blocks are removed and rendered by per-block `BlockDisplay` entities; the real
  blocks are placed at their destinations when the animation finishes.

## Quick start

1. `/door wand` — grab the selection wand (a blaze rod named *Door Wand* by default; both the material
   and the name are configurable). Only wands handed out by this command select corners, so an ordinary
   blaze rod stays an ordinary blaze rod.
2. **Left-click** one corner of the door, **right-click** the opposite corner.
3. `/door create mygate` — create the door from the selection (warns if the selection holds filled containers).

**For a swing door (default):**

4. `/door hinge mygate` — look at the block the door should pivot around, then run it.
5. `/door direction mygate cw` — set swing direction (`cw` or `ccw`; flip it if it swings the wrong way).
6. `/door toggle mygate` — test the swing.

**For a portcullis:**

4. `/door type mygate portcullis` — switch it to vertical-slide motion (defaults to sliding up by its own height).
5. `/door slide mygate 5` — optional: set the distance (`+` up, `−` down). Setting a slide on a swing door
   also makes it a portcullis.
6. `/door toggle mygate` — test the slide.

**For a sliding door:**

4. `/door type mygate sliding` — switch it to horizontal-slide motion. It defaults to retracting along its
   own longest horizontal axis by its width on that axis, so a wall-shaped door disappears into the wall
   beside it.
5. `/door direction mygate west` — optional: pick which way it retracts (`north`, `south`, `east`, `west`).
6. `/door slide mygate 4` — optional: set the distance.
7. `/door toggle mygate` — test the slide.

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
| `/door type <name> <swing\|portcullis\|sliding>` | Choose the door's motion |
| `/door hinge <name>` | (swing) Set the hinge to the block you're looking at |
| `/door direction <name> <cw\|ccw>` | (swing) Set the opening direction |
| `/door direction <name> <up\|down>` | (portcullis) Set which way it retracts |
| `/door direction <name> <north\|south\|east\|west>` | (sliding) Set which way it retracts |
| `/door slide <name> <blocks>` | Set the travel distance (portcullis: +up / −down) |
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
  wand-name: "<gold>Door Wand"   # MiniMessage formatting
  wand-lore:
    - "<gray>Left-click: corner 1"
    - "<gray>Right-click: corner 2"
  strict-wand: true    # false = any item of wand-material selects corners
```

The wand's name and lore accept [MiniMessage](https://docs.advntr.dev/minimessage/format.html) tags, so
`"<gradient:#ffcc00:#ff6600><bold>Door Wand"` works as well as a plain string. Wands are stamped with a
hidden tag when `/door wand` hands them out; with `strict-wand: true` (the default) that tag is what makes
an item a wand, so renaming, stacking or storing it is safe and a look-alike item does nothing. Set
`strict-wand: false` to also accept any item of `wand-material`, which is how wands behaved before they
were named.

## Known limitations (tier-two scope)

- Three motion types are supported: swing (90° around a vertical hinge), portcullis (vertical slide) and
  sliding (horizontal slide). Drawbridges, revolving doors, and other structure types are out of scope here.
- Tile-entity **contents** are not preserved by a move. To protect them, a door refuses to move while
  any container inside it (chest, barrel, furnace, hopper, shulker box, …) still holds items — empty it
  first, or disable the guard with `restrictions.block-filled-containers: false`. Empty containers move
  freely. Sign text is still not preserved.
- A move overwrites whatever occupies the destination cells, so leave the door's path (swing arc, slide
  column, or the blocks a sliding door retracts into) clear.
