# DynamicDoors

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

The plugin jar is produced at `target/DynamicDoors-0.1.0.jar`. Drop it in your server's `plugins/`
folder and restart.

## How it works

- A door is an explicit **set of blocks** you pick — not a box. Only the blocks you selected move, so a
  wall, floor or roof that happens to share the door's bounding box is never dragged along. Each door
  has a **type**: `swing` or `portcullis`.
  - **Swing** doors rotate 90° around a **hinge** — the vertical column at one (x, z), since the turn is
    around the Y axis, so any block of that column names the same hinge. It is usually a block of the door
    itself, but it doesn't have to be: a hinge outside the door just sweeps a wider arc. The client
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
3. `/door attach` — optional: pulls in the small stuff stuck to the door (see below).
4. `/door finish` — **preview**: the exact blocks that will move light up. Wrong ones in there? Left-click
   them and run it again.
5. `/door create mygate` — create the door from the previewed selection (warns if it holds filled containers).

**For a swing door (default):**

5. `/door hinge mygate` — look at the block the door should pivot around (up to 8 blocks away), then run
   it. The hinge column is marked in gold so you can see exactly where it landed. `/door hinge mygate here`
   uses the block you're standing in, `/door hinge mygate <x> <z>` takes coordinates, and
   `/door hinge mygate show` re-marks it later.
6. `/door direction mygate cw` — set swing direction (`cw` or `ccw`; flip it if it swings the wrong way).
7. `/door preview mygate` — watch a **ghost** of the door swing, without touching a single real block.
8. `/door toggle mygate` — do it for real.

**For a portcullis:**

5. `/door type mygate portcullis` — switch it to vertical-slide motion (defaults to sliding up by its own height).
6. `/door slide mygate 5` — optional: set the distance (`+` up, `−` down). Setting a slide also makes it a portcullis.
7. `/door preview mygate` — ghost-run the slide first.
8. `/door toggle mygate` — do it for real.

### Attached blocks

A torch, button, lever, sign or ladder stuck to the door only moves if it is *in the selection* — the
plugin has no idea it "belongs" to the gate otherwise. Clicking each one gets tedious, so **`/door attach`**
finds them for you: it works out what each neighbouring block is held up by, and pulls in the ones resting
on — or stuck to — blocks you already picked. It runs a few passes, so a lantern hanging off a sign hanging
off the door comes along too, and stops at `selection.attach-limit` blocks.

It handles buttons and levers (floor, wall or ceiling), wall and standing torches, signs and hanging signs,
banners, heads, ladders, lanterns, vines and glow lichen, carpets, pressure plates, rails, redstone dust,
repeaters and comparators, flowers and candles, and both halves of two-block things like doors and beds.
`/door finish` also *tells* you when it spots attached blocks you haven't included, so you find out before
the first swing rather than after.

It's a best-effort guess, and everything it adds is previewed — anything it grabbed that isn't part of the
door, left-click to drop. To fix a door that's already built: `/door edit mygate`, `/door attach`,
`/door update mygate`.

Item frames, paintings and armour stands are entities rather than blocks, so they never move with a door
no matter what is selected.

### Previews

Previews are drawn with `BlockDisplay` ghosts and are visible **only to you**; no real block is ever moved
by one.

- `/door finish` — outline the blocks currently selected. This is the "is my selection actually the door?"
  check.
- `/door preview <name> [open|close]` — ghost-run an existing door's move. Also reports how many blocks
  sit where the door would land and would be overwritten by a real toggle.
- The outline also refreshes automatically after each wand click (turn it off with `preview.enabled`).

### Working on one door

Typing the door's name into every command gets old fast. `/door select mygate` picks a door to work on,
and from then on you can leave the name off: `/door hinge`, `/door direction cw`, `/door preview`,
`/door toggle`, and so on all apply to it. Creating a door with `/door create` selects it automatically,
and passing a name to any command switches the selection to that door.

`/door select` on its own reports what's selected, `/door select none` clears it. Two details worth
knowing: `/door remove` always wants the name spelled out, since deleting the wrong door by accident is
no fun; and a name always wins over a value, so if you ever name a door `cw`, `/door direction cw` will
be read as "switch to the door called cw".

### Fixing an existing door

`/door edit <name>` loads a door's blocks back into your selection (in block mode, outlined), so you can
left-click the strays out and right-click missing blocks in. `/door update <name>` writes the selection
back to the door. Both require the door to be closed.

### Triggers

- **Power block:** the door's dedicated control block. `/door wand` hands you one along with the wand.
  Place it, then either look at it and run `/door powerblock mygate`, or run
  `/door powerblock mygate wand` and **right-click it with the wand** (left-click cancels; clicking a
  block that isn't valid keeps the wand armed so you can try again). Powering it — lever, button, plate, repeater, dust running into it —
  toggles the door on the rising edge, and right-clicking it toggles it too. Power blocks are protected
  from being broken, burned or blown up; an admin sneak-breaks one to unbind it. `/door powerblock mygate
  show` makes it glow and tells you whether it's currently powered, and `... clear` unbinds it.
- **Redstone:** look at a block and run `/door trigger mygate redstone`. Powering that block (lever,
  button, redstone signal) toggles the door on the rising edge. Same idea as a power block, without the
  material requirement, the protection or the click-to-toggle.
- **Floating click-trigger:** look at a spot and run `/door trigger mygate float`. This places an
  invisible, floating clickable zone there — right-click it to toggle. Handy for a doorknob-style hotspot
  in mid-air.
- **Click the door itself:** right-clicking any block of the door toggles it (toggle with
  `triggers.click-door-to-toggle` in the config).
- `/door trigger mygate clear` — remove this door's triggers.

## Commands

| Command | Description |
| --- | --- |
| `/door select <name>` | Work on this door; other commands can then omit the name |
| `/door wand` | Get the selection wand |
| `/door mode <block\|region>` | Pick blocks one by one, or use a two-corner box |
| `/door add` | Add the wand's corner box to your picks (skips air) |
| `/door sub` | Subtract the wand's corner box from your picks |
| `/door attach` | Pull in torches, buttons, signs … stuck to your selection |
| `/door finish` | Preview exactly which blocks will move |
| `/door clear` | Clear your selection |
| `/door create <name>` | Create a door from your selection |
| `/door edit <name>` | Load a door's blocks back into your selection |
| `/door update <name>` | Replace a door's blocks with your selection |
| `/door preview <name> [open\|close]` | Ghost-run the move without touching blocks |
| `/door type <name> <swing\|portcullis>` | Choose swing or vertical-slide motion |
| `/door hinge <name> [here\|show\|<x> <z>]` | (swing) Set or show the column the door pivots around |
| `/door direction <name> <cw\|ccw>` | (swing) Set the opening direction |
| `/door slide <name> <blocks>` | (portcullis) Set vertical distance (+up / −down) |
| `/door powerblock <name> [wand\|clear\|show]` | Bind (by look or wand), unbind, or locate the power block |
| `/door trigger <name> <redstone\|float\|clear>` | Manage triggers |
| `/door toggle <name>` | Open/close a door |
| `/door info <name>` | Show a door's details |
| `/door list` | List all doors |
| `/door remove <name>` | Delete a door |
| `/door reload` | Reload the config |

Aliases: `/ddoor`, `/dynamicdoor`.

### Storage

`doors.yml` is save data, not configuration. Each door is written as one gzipped, Base64-encoded string,
with its blocks packed as a bitmask over the door's bounding box — a 10×10×10 door costs about 125 bytes
of mask instead of a thousand lines of coordinates. The encoding round-trips exactly (it is an encoding,
not a hash); if you ever need to read or hand-edit a door, set `storage.compact: false` and the next save
writes the old readable layout. Both layouts load, and old readable files are migrated automatically on
the first save.

## Upgrading from AnimatedDoors

The plugin was renamed, so a few names moved with it:

- Data folder: `plugins/AnimatedDoors/` → `plugins/DynamicDoors/`. Copy your old `doors.yml` and
  `config.yml` across (the door format is unchanged).
- Permissions: `animateddoors.*` → `dynamicdoors.*`. Update your permission plugin's groups.
- Command aliases: `/adoor` and `/animateddoor` are now `/ddoor` and `/dynamicdoor`. `/door` is unchanged.

## Permissions

| Permission | Default | Grants |
| --- | --- | --- |
| `dynamicdoors.admin` | op | Create/edit/remove doors (includes `use`) |
| `dynamicdoors.use` | op | Commands like `list` and `info` |
| `dynamicdoors.toggle` | everyone | Toggle doors via triggers / clicking |

## Configuration (`config.yml`)

```yaml
animation:
  duration-ticks: 40   # total swing time (20 ticks = 1s)
  step-ticks: 2        # ticks between animation keyframes
triggers:
  click-door-to-toggle: true
  cooldown-ticks: 10   # min ticks between toggles of the same door
storage:
  compact: true            # doors.yml holds one opaque string per door (false = readable layout)
power-block:
  material: GOLD_BLOCK
  give-with-wand: true     # /door wand also hands you a power block to place
  require-material: true   # only a block of that material can drive the door
  protect: true            # can't be broken/burned/exploded; sneak-break to unbind
  click-to-toggle: true    # right-clicking the power block toggles the door
restrictions:
  block-filled-containers: true
  obstruction: block             # block = refuse to move, overwrite = destroy what's in the way
  suppress-block-updates: true   # don't let the world react to a door while it moves
  update-guard-grace-ticks: 2
selection:
  wand-material: BLAZE_ROD
  default-mode: block  # block = pick blocks individually, region = two-corner box
  attach-limit: 256    # cap on what one /door attach scan pulls in
preview:
  enabled: true
  duration-ticks: 200        # how long /door finish stays up
  live-duration-ticks: 100   # outline shown after each wand click
  hold-ticks: 40             # how long a ghost lingers at its destination
  max-blocks: 2000           # selections bigger than this aren't previewed
```

### Block updates

A moving door does not update its surroundings. Its block writes are made with physics disabled, so
removing and placing the door's blocks never notifies neighbouring blocks: torches, ladders, signs,
fences, rails and redstone next to the door are left exactly as they are, sand and gravel above it stay
put, and nothing cascades.

On top of that, the cells the door passes through are frozen for the length of the move (plus
`update-guard-grace-ticks`), so updates coming from the *other* direction — fluids running into the
doorway, falling blocks landing in it, vanilla scheduled ticks, other plugins reacting — are cancelled
instead of rewriting the door mid-animation. Overlapping doors are reference-counted, and when no door is
moving the check is a single boolean read per event.

The trade-off is the point: because nothing is notified, a block that *relied* on a door block for support
— a torch stuck to the gate, a carpet on top of it — stays floating in place rather than popping off, until
some unrelated update touches it. Set `restrictions.suppress-block-updates: false` if you'd rather vanilla
behave normally around the door.

## Known limitations (tier-two scope)

- Two motion types are supported: swing (90° around a vertical hinge) and portcullis (vertical slide).
  Drawbridges, revolving doors, and other structure types are out of scope here.
- Tile-entity **contents** are not preserved by a move. To protect them, a door refuses to move while
  any container inside it (chest, barrel, furnace, hopper, shulker box, …) still holds items — empty it
  first, or disable the guard with `restrictions.block-filled-containers: false`. Empty containers move
  freely. Sign text is still not preserved.
- **Blocks in the way:** by default a door checks its destination cells before moving. If anything solid
  is standing there, the door **refuses to move**, the offending blocks glow red for whoever triggered it,
  and the trigger reports why. Air, water and replaceable growth (grass, snow layers, fire) don't count —
  the door sweeps those aside. Only destination cells matter: the animation is drawn with display
  entities, so a block partway along a swing arc is passed straight through and is not an obstruction.
  Set `restrictions.obstruction: overwrite` for the old behaviour, where the door moves anyway and
  destroys whatever occupied its landing cells. `/door preview <name>` reports the count either way,
  before you find out the hard way.
- Doors created before per-block selection were stored as a box; they load as every block in that box and
  are rewritten in the new per-block format on the next save. Run `/door edit`/`/door update` on them to
  trim anything that was never part of the door.
