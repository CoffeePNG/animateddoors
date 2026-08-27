package com.coffeepng.dynamicdoors.door;

import com.coffeepng.dynamicdoors.model.BlockVector3;
import com.coffeepng.dynamicdoors.model.Door;
import com.coffeepng.dynamicdoors.model.DoorType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Packs a whole door into one opaque string for storage.
 *
 * <p>A door's block set is the bulky part — a modest gate is hundreds of coordinates, and writing
 * them as readable YAML makes {@code doors.yml} enormous and slow to parse. Instead the blocks are
 * encoded as a bitmask over the door's bounding box (one bit per cell, so a 10x10x10 door costs 125
 * bytes instead of ~1000 lines), the whole record is gzipped, and the result is Base64'd into a
 * single scalar.</p>
 *
 * <p>This is an encoding, not a hash: it round-trips exactly, which is what storage needs.</p>
 */
public final class DoorCodec {

    /** Format marker, bumped whenever the binary layout changes. */
    private static final byte VERSION = 1;

    /** Bounding boxes bigger than this fall back to an explicit coordinate list. */
    private static final int MAX_BITMASK_CELLS = 1 << 16;

    private static final byte BLOCKS_BITMASK = 0;
    private static final byte BLOCKS_LIST = 1;

    private DoorCodec() {
    }

    public static String encode(Door door) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(VERSION);
            out.writeUTF(door.getName());
            out.writeUTF(door.getWorld());
            writeBlocks(out, door);
            out.writeInt(door.getHingeX());
            out.writeInt(door.getHingeZ());
            out.writeByte(door.getType().ordinal());
            out.writeInt(door.getQuarterTurns());
            out.writeInt(door.getSlide());
            out.writeBoolean(door.isOpen());
            writeOptionalVec(out, door.getPowerBlock());
            writeOptionalVec(out, door.getRedstoneTrigger());
            writeOptionalVec(out, door.getFloatingTrigger());
            writeOptionalUuid(out, door.getFloatingEntityId());
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to encode door '" + door.getName() + "'", ex);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    public static Door decode(UUID id, String encoded) {
        byte[] raw = Base64.getDecoder().decode(encoded);
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(raw)))) {
            byte version = in.readByte();
            if (version != VERSION) {
                throw new IllegalArgumentException("unsupported door format version " + version);
            }
            String name = in.readUTF();
            String world = in.readUTF();
            Door door = new Door(id, name, world, readBlocks(in));
            door.setHinge(in.readInt(), in.readInt());
            int typeOrdinal = in.readByte();
            DoorType[] types = DoorType.values();
            door.setType(typeOrdinal >= 0 && typeOrdinal < types.length ? types[typeOrdinal] : DoorType.SWING);
            door.setQuarterTurns(in.readInt());
            door.setSlide(in.readInt());
            door.setOpen(in.readBoolean());
            door.setPowerBlock(readOptionalVec(in));
            door.setRedstoneTrigger(readOptionalVec(in));
            door.setFloatingTrigger(readOptionalVec(in));
            door.setFloatingEntityId(readOptionalUuid(in));
            return door;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to decode door " + id, ex);
        }
    }

    // ---- blocks ------------------------------------------------------------

    private static void writeBlocks(DataOutputStream out, Door door) throws IOException {
        BlockVector3 min = door.getMin();
        BlockVector3 max = door.getMax();
        out.writeInt(min.x());
        out.writeInt(min.y());
        out.writeInt(min.z());
        out.writeInt(max.x());
        out.writeInt(max.y());
        out.writeInt(max.z());

        long cells = (long) (max.x() - min.x() + 1) * (max.y() - min.y() + 1) * (max.z() - min.z() + 1);
        List<BlockVector3> blocks = door.closedPositions();
        // A door spread thinly across a huge box would waste more on empty bits than on coordinates.
        if (cells > MAX_BITMASK_CELLS) {
            out.writeByte(BLOCKS_LIST);
            out.writeInt(blocks.size());
            for (BlockVector3 pos : blocks) {
                out.writeInt(pos.x());
                out.writeInt(pos.y());
                out.writeInt(pos.z());
            }
            return;
        }

        out.writeByte(BLOCKS_BITMASK);
        byte[] mask = new byte[(int) ((cells + 7) / 8)];
        for (BlockVector3 pos : blocks) {
            int index = cellIndex(min, max, pos);
            mask[index >> 3] |= (byte) (1 << (index & 7));
        }
        out.writeInt(mask.length);
        out.write(mask);
    }

    private static List<BlockVector3> readBlocks(DataInputStream in) throws IOException {
        BlockVector3 min = new BlockVector3(in.readInt(), in.readInt(), in.readInt());
        BlockVector3 max = new BlockVector3(in.readInt(), in.readInt(), in.readInt());
        byte encoding = in.readByte();
        List<BlockVector3> out = new ArrayList<>();
        if (encoding == BLOCKS_LIST) {
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                out.add(new BlockVector3(in.readInt(), in.readInt(), in.readInt()));
            }
            return out;
        }
        if (encoding != BLOCKS_BITMASK) {
            throw new IllegalArgumentException("unknown block encoding " + encoding);
        }
        byte[] mask = new byte[in.readInt()];
        in.readFully(mask);
        int index = 0;
        for (int x = min.x(); x <= max.x(); x++) {
            for (int y = min.y(); y <= max.y(); y++) {
                for (int z = min.z(); z <= max.z(); z++) {
                    if ((mask[index >> 3] & (1 << (index & 7))) != 0) {
                        out.add(new BlockVector3(x, y, z));
                    }
                    index++;
                }
            }
        }
        return out;
    }

    /** Index of a cell inside the bounding box, in the same x,y,z order the reader walks. */
    private static int cellIndex(BlockVector3 min, BlockVector3 max, BlockVector3 pos) {
        int sizeY = max.y() - min.y() + 1;
        int sizeZ = max.z() - min.z() + 1;
        return ((pos.x() - min.x()) * sizeY + (pos.y() - min.y())) * sizeZ + (pos.z() - min.z());
    }

    // ---- optional fields ---------------------------------------------------

    private static void writeOptionalVec(DataOutputStream out, BlockVector3 vec) throws IOException {
        out.writeBoolean(vec != null);
        if (vec != null) {
            out.writeInt(vec.x());
            out.writeInt(vec.y());
            out.writeInt(vec.z());
        }
    }

    private static BlockVector3 readOptionalVec(DataInputStream in) throws IOException {
        if (!in.readBoolean()) {
            return null;
        }
        return new BlockVector3(in.readInt(), in.readInt(), in.readInt());
    }

    private static void writeOptionalUuid(DataOutputStream out, UUID id) throws IOException {
        out.writeBoolean(id != null);
        if (id != null) {
            out.writeLong(id.getMostSignificantBits());
            out.writeLong(id.getLeastSignificantBits());
        }
    }

    private static UUID readOptionalUuid(DataInputStream in) throws IOException {
        if (!in.readBoolean()) {
            return null;
        }
        return new UUID(in.readLong(), in.readLong());
    }
}
