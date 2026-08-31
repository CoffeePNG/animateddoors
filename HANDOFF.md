# DynamicDoors — Handoff

Two features, a **sliding door type** and a **custom-named selection wand**, merged into the
block-selection work and landed on the default branch. This is the state you would be picking up.

| | |
| --- | --- |
| Head | `32b4b5c` |
| Build | passing (`mvn clean package`) |
| Target | Paper / Purpur 1.21.11, Java 21 |
| Artifact | `target/DynamicDoors-0.1.0.jar` |

## Where things stand

Three branches exist. There is **no `main`** — the repository's default branch is the long-named
one, a leftover from an earlier session.

| Branch | Head | Status | Notes |
| --- | --- | --- | --- |
| `claude/animated-architecture-feasibility-hhn1p4` | `32b4b5c` | **default** | Carries everything. This is the trunk. |
| `feature/block-select-preview` | `32b4b5c` | merged | Identical to trunk. Safe to delete. |
| `claude/sliding-door-custom-wand-8fz8p8` | `5beaa68` | superseded | The sliding/wand work before the merge. Safe to delete. |

Three commits landed, in this order:

- `5beaa68` — sliding door type and the named wand, written against the pre-rename
  `animateddoors` package.
- `789392f` — the merge with the block-selection branch, which renamed the plugin and rewrote both
  files that work lived in.
- `32b4b5c` — the port, wiring both features onto the new command surface.

## Work on a separate branch from here

> **Standing rule: do not commit to the default branch again.** Cut a branch, push it, and merge
> from there — even for a one-line fix.

Both pushes in the session that produced this work went straight to trunk because they were asked
for explicitly, which meant nothing was reviewed before it landed and there was no point at which
the merge could be inspected as a diff. That was a deliberate call at the time; it is not the
pattern to continue.

```bash
# start any follow-up like this
git checkout claude/animated-architecture-feasibility-hhn1p4
git pull origin claude/animated-architecture-feasibility-hhn1p4
git checkout -b feature/<short-name>

# ...work, commit...
git push -u origin feature/<short-name>
```

Then open a pull request against the default branch rather than fast-forwarding it by hand. The
repository has PRs enabled and no branch protection, so nothing enforces this — it holds only if
you keep to it.

## What shipped

### Sliding doors

A third motion type beside swing and portcullis. The door translates horizontally along a cardinal
direction by a set distance — a pocket door retracting into the wall beside it, or a blast door
sliding aside.

`DoorGeometry.openShift()` returns the whole-block displacement a translating door applies when it
opens, and both the animator and the preview drive their displays from that vector. Portcullis
motion is the y-only case of the same code path. Nothing rotates, so block facing is left alone.

`/door type <name> sliding` defaults to retracting along the door's longest horizontal axis by its
width on that axis, so a wall-shaped selection disappears into the wall next to it. `/door
direction` is now type-aware — `cw|ccw` for swing, `up|down` for a portcullis (new),
`north|south|east|west` for sliding — and tab completion offers only the tokens that fit the named
door.

### The named wand

`selection.wand-name` and `selection.wand-lore` are MiniMessage-formatted, and `/door wand` stamps
the item with a persistent tag. That tag is what identifies a wand now, so an ordinary blaze rod no
longer selects blocks. The same check gates the armed `/door powerblock <name> wand` click.

`selection.strict-wand: false` restores material-only matching, for servers whose players already
carry untagged wands.

## Merge decisions worth knowing

The two lines diverged at `97bda7c`. The block-selection branch had renamed the plugin and rewritten
both files the sliding/wand work lived in, so git resolved those as deletions — **the merge compiled
cleanly while silently containing neither feature.** That is why there is a separate port commit. If
you merge these lines again in some other order, expect the same trap.

**`DoorTransform.keyframe`** took a vertical scalar; now takes a displacement vector. This helper is
shared by the real animation and the ghost preview, which is what makes `/door preview` render a
sliding door moving instead of standing still.

**`DoorCodec` format 2.** The branch's default storage is packed gzip/Base64, not the readable YAML
the new fields were originally added to — so sliding doors would not have persisted at all.
`slideDistance` and the slide face now ride along, the face written **by name rather than ordinal**,
since `BlockFace`'s ordering is not ours to depend on. The format marker went to 2; version 1
records still load, with the face defaulted.

Two judgement calls made during the merge:

- The wand lore said "Left-click: corner 1 / Right-click: corner 2" — wrong once block mode became
  the default, where right-click adds and left-click removes. Reworded to be mode-neutral.
- A README line promising that a move overwrites whatever is in its way was dropped, because the
  merged behaviour is the opposite: doors refuse to move and glow the obstruction red.

## Deploying over an existing install

The plugin was renamed from AnimatedDoors on the block-selection branch, so this is **not** a
drop-in jar swap. Stop the server first; leaving the old jar in place loads both plugins and they
fight over the same doors.

```bash
git pull origin claude/animated-architecture-feasibility-hhn1p4
mvn clean package

# server stopped
rm plugins/AnimatedDoors-0.1.0.jar
cp target/DynamicDoors-0.1.0.jar plugins/
mv plugins/AnimatedDoors plugins/DynamicDoors
```

> **One-way migration.** `doors.yml` is read in the readable format and rewritten packed on the
> first save. Back it up before starting — once rewritten, an older jar cannot read it back.

Two more things bite on this upgrade:

- **Permissions changed.** `animateddoors.*` became `dynamicdoors.*`; update permission groups or
  staff lose access. Aliases moved too: `/adoor` → `/ddoor`. `/door` itself is unchanged.
- **Existing configs gain no new keys.** Bukkit does not merge them, so `wand-name`, `strict-wand`,
  `default-mode`, the `preview` block and the power-block settings all fall back to code defaults.
  Two of those defaults change behaviour: existing blaze-rod wands stop working, and doors now
  refuse to move when something occupies the landing cells instead of destroying it. Cleanest fix is
  to move the old config aside, let a fresh one generate, and re-apply customisations.

## What was verified, and what wasn't

- [x] `mvn clean package` green on the merged tree, no unused imports.
- [x] Codec round-trips a sliding door and a portcullis, and a hand-built version 1 record still
      loads with the face defaulted.
- [ ] **Never run on an actual server.** No door has been toggled, no wand clicked, no preview seen.
      Everything about in-world behaviour is inference from the code.
- [ ] **The codec test is not in this repository.** There is no test infrastructure — no `src/test`,
      no JUnit dependency — so it was run as a throwaway against `target/classes` and is gone.

## Open follow-ups

Roughly in the order they matter, none of them started:

1. **Smoke-test on a real 1.21.11 server.** The highest-value gap by far. Build a sliding door,
   preview it, toggle it, check the blocks land where the ghost said they would, and confirm a fresh
   wand selects while a plain blaze rod does not.
2. **Startup migration for the rename.** Copy `plugins/AnimatedDoors` to `plugins/DynamicDoors` on
   first enable when the new folder is absent, so operators skip the manual dance above. Offered
   during the session, never built.
3. **Test infrastructure.** Add JUnit and commit the codec round-trip, including the version 1
   fixture. It is the piece most likely to silently destroy someone's saved doors.
4. **Rename the trunk.** The default branch is still
   `claude/animated-architecture-feasibility-hhn1p4`. GitHub retargets open PRs automatically on a
   branch rename; it is a settings change, not a code one.
5. **Delete the two dead branches** listed in the table above.
