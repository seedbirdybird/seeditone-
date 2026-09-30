package seed.seeditone;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Config {
    public static final String[] NAMES = {
        "Background Main", "Panel Background", "Sub Panel", "Text", "Text Primary", "Text Secondary",
        "Highlight", "Glow", "Glow Bright", "Keybind Background", "Border Glow", "Shadow", "Inner Shadow"
    };
    public static final int BG = 0, PANEL = 1, SUB = 2, TEXT = 3, PRIMARY = 4, SECONDARY = 5,
        HIGHLIGHT = 6, GLOW = 7, GLOW_BRIGHT = 8, KEYBIND = 9, BORDER = 10, SHADOW = 11, INNER = 12;

    private static final int[] DEFAULTS = {
        0xF0040604, 0xC0202620, 0xFF0E130F, 0xFF00E85A, 0xFFB8FFD0, 0xFF8A8A8A,
        0xFF00FF66, 0x8000C853, 0xA000E676, 0xFF0E130F, 0xFF00FF66, 0x60000000, 0x40808080
    };
    public static final int[] COLORS = DEFAULTS.clone();

    public static boolean builderEnabled = true;
    public static boolean hudEnabled = true;
    /** 0 none, 1 build assist, 2 semi build, 3 full auto */
    public static int mode = 2;
    /** command template used by Full Auto to buy missing items, e.g. "buy {item} {amount}". Empty = off. */
    public static String buyCommand = "";

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("seeditone.properties"); }
    public static Path schematicsDir() { return FabricLoader.getInstance().getGameDir().resolve("schematics"); }

    public static int color(int i) { return COLORS[i]; }
    public static void resetTheme() { System.arraycopy(DEFAULTS, 0, COLORS, 0, DEFAULTS.length); }

    public static void load() {
        try {
            if (!Files.exists(file())) return;
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(file())) { p.load(in); }
            for (int i = 0; i < COLORS.length; i++) {
                String v = p.getProperty("theme." + i);
                if (v != null) COLORS[i] = (int) Long.parseLong(v, 16);
            }
            builderEnabled = Boolean.parseBoolean(p.getProperty("builder", "true"));
            hudEnabled = Boolean.parseBoolean(p.getProperty("hud", "true"));
            mode = Integer.parseInt(p.getProperty("mode", "2"));
            buyCommand = p.getProperty("buycmd", "");
        } catch (Exception ignored) {}
    }

    public static void save() {
        try {
            Properties p = new Properties();
            for (int i = 0; i < COLORS.length; i++) p.setProperty("theme." + i, Integer.toHexString(COLORS[i]));
            p.setProperty("builder", String.valueOf(builderEnabled));
            p.setProperty("hud", String.valueOf(hudEnabled));
            p.setProperty("mode", String.valueOf(mode));
            p.setProperty("buycmd", buyCommand);
            try (OutputStream out = Files.newOutputStream(file())) { p.store(out, "Seeditone"); }
        } catch (Exception ignored) {}
    }

    private Config() {}
}
