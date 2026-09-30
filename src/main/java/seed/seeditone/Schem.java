package seed.seeditone;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Sponge .schem (v2 + v3) loader. Index = (y * l + z) * w + x */
public class Schem {
    public final int w, h, l;
    public final BlockState[] states;

    private Schem(int w, int h, int l, BlockState[] states) {
        this.w = w; this.h = h; this.l = l; this.states = states;
    }

    public static Schem load(Path path) throws Exception {
        NbtCompound root = NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());
        if (root.contains("Schematic")) root = root.getCompound("Schematic").orElseThrow();

        int w = root.getShort("Width").orElse((short) 0) & 0xFFFF;
        int h = root.getShort("Height").orElse((short) 0) & 0xFFFF;
        int l = root.getShort("Length").orElse((short) 0) & 0xFFFF;

        NbtCompound blocks = root.contains("Blocks") ? root.getCompound("Blocks").orElseThrow() : root; // v3 : v2
        NbtCompound pal = blocks.getCompound("Palette").orElseThrow();
        final NbtCompound b = blocks;
        byte[] data = b.getByteArray("Data").or(() -> b.getByteArray("BlockData")).orElseThrow();

        Map<Integer, BlockState> palette = new HashMap<>();
        for (String key : pal.getKeys()) palette.put(pal.getInt(key).orElse(0), parse(key));

        BlockState[] out = new BlockState[w * h * l];
        int idx = 0, i = 0;
        while (i < data.length && idx < out.length) {
            int value = 0, shift = 0;
            while (true) {
                byte by = data[i++];
                value |= (by & 0x7F) << shift;
                if ((by & 0x80) == 0) break;
                shift += 7;
            }
            out[idx++] = palette.get(value);
        }
        return new Schem(w, h, l, out);
    }

    private static BlockState parse(String s) {
        String id = s, props = "";
        int br = s.indexOf('[');
        if (br >= 0) { id = s.substring(0, br); props = s.substring(br + 1, s.length() - 1); }
        Identifier ident = Identifier.tryParse(id);
        Block block = ident == null ? Blocks.AIR : Registries.BLOCK.get(ident);
        BlockState st = block.getDefaultState();
        if (!props.isEmpty()) {
            for (String kv : props.split(",")) {
                String[] p = kv.split("=", 2);
                if (p.length < 2) continue;
                Property<?> prop = block.getStateManager().getProperty(p[0]);
                if (prop != null) st = with(st, prop, p[1]);
            }
        }
        return st;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> p, String v) {
        return p.parse(v).map(val -> s.with(p, val)).orElse(s);
    }
}
