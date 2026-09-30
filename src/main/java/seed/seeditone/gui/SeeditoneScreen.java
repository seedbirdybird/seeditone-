package seed.seeditone.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import seed.seeditone.Config;
import seed.seeditone.SeeditoneClient;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class SeeditoneScreen extends Screen {
    private static final String[] TABS = {"Builder", "Visuals", "Settings"};
    private static final String[] SUBS = {"Schem Builder", "Show HUD", "Theme Customizer"};

    private int tab = 0, sel = Config.HIGHLIGHT, drag = 0, scroll = 0;
    private float hue, sat, val, alpha;
    private boolean prevDown;
    private TextFieldWidget search;
    private final List<Path> files = new ArrayList<>();

    public SeeditoneScreen() { super(Text.literal("Seeditone")); }

    @Override
    protected void init() {
        search = new TextFieldWidget(textRenderer, 0, 0, 150, 12, Text.literal("Search"));
        search.setDrawsBackground(false);
        search.setPlaceholder(Text.literal("Search modules..."));
        addDrawableChild(search);
        refreshFiles();
        loadPicker();
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float dt) {
        ctx.fill(0, 0, width, height, 0x88000000);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        scroll = Math.max(0, scroll - (int) Math.signum(v));
        return true;
    }

    private void refreshFiles() {
        files.clear();
        try {
            Path dir = Config.schematicsDir();
            Files.createDirectories(dir);
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".schem")).sorted().forEach(files::add);
            }
        } catch (Exception ignored) {}
    }

    private int c(int i) { return Config.COLORS[i]; }

    // ---------- drawing helpers ----------
    private void rrect(DrawContext g, int x1, int y1, int x2, int y2, int r, int color) {
        r = Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        if (r <= 0) { g.fill(x1, y1, x2, y2, color); return; }
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            g.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
            g.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
        }
        g.fill(x1, y1 + r, x2, y2 - r, color);
    }

    private void bordered(DrawContext g, int x1, int y1, int x2, int y2, int r, int fill, int border) {
        rrect(g, x1, y1, x2, y2, r, border);
        rrect(g, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(0, r - 1), fill);
    }

    private void text(DrawContext g, String s, int x, int y, int color) {
        g.drawText(textRenderer, s, x, y, color, false);
    }

    private void textA(DrawContext g, String s, int x, int y, int argb) {
        g.drawText(textRenderer, s, x, y, argb, false);
    }

    private void toggle(DrawContext g, int x, int y, boolean on) {
        rrect(g, x, y, x + 24, y + 12, 6, on ? c(Config.HIGHLIGHT) : 0xFF2A2F2B);
        int kx = on ? x + 13 : x + 1;
        rrect(g, kx, y + 1, kx + 10, y + 11, 5, on ? 0xFF031008 : 0xFF8A8A8A);
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------- main render ----------
    @Override
    public void render(DrawContext g, int mx, int my, float dt) {
        long win = client.getWindow().getHandle();
        boolean down = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean click = down && !prevDown;
        prevDown = down;
        if (!down && drag != 0) { drag = 0; Config.save(); }

        int W = Math.min(width - 16, 620), H = Math.min(height - 16, 340);
        int x0 = (width - W) / 2, y0 = (height - H) / 2;

        // window
        bordered(g, x0, y0, x0 + W, y0 + H, 10, c(Config.BG), c(Config.BORDER) & 0x60FFFFFF | 0x60000000);

        // ----- sidebar -----
        int sx = x0 + 8, sw = 140;
        rrect(g, sx, y0 + 8, sx + sw, y0 + H - 8, 8, c(Config.PANEL));

        rrect(g, sx + 10, y0 + 16, sx + 40, y0 + 46, 8, c(Config.HIGHLIGHT));
        textA(g, "S", sx + 23, y0 + 27, 0xFF031008);
        textA(g, "S", sx + 24, y0 + 27, 0xFF031008);
        textA(g, "Seeditone", sx + 48, y0 + 20, c(Config.TEXT));
        textA(g, "v1.0.0", sx + 48, y0 + 32, c(Config.SECONDARY));
        g.fill(sx + 10, y0 + 54, sx + sw - 10, y0 + 56, c(Config.HIGHLIGHT));

        for (int i = 0; i < TABS.length; i++) {
            int ty = y0 + 64 + i * 40;
            boolean sel_ = tab == i, hov = in(mx, my, sx + 4, ty, sw - 8, 36);
            if (sel_) bordered(g, sx + 4, ty, sx + sw - 4, ty + 36, 8, 0xFF07240F, c(Config.HIGHLIGHT));
            else if (hov) rrect(g, sx + 4, ty, sx + sw - 4, ty + 36, 8, 0x30FFFFFF);
            textA(g, TABS[i], sx + 16, ty + 7, sel_ ? c(Config.PRIMARY) : c(Config.TEXT));
            textA(g, textRenderer.trimToWidth(SUBS[i], sw - 30), sx + 16, ty + 20, c(Config.SECONDARY));
            if (click && hov && tab != i) { tab = i; scroll = 0; }
        }

        String name = client.player != null ? client.player.getName().getString() : "Player";
        rrect(g, sx + 4, y0 + H - 54, sx + sw - 4, y0 + H - 12, 8, c(Config.SUB));
        textA(g, name, sx + 14, y0 + H - 46, c(Config.TEXT));
        textA(g, "1.21.11 | Fabric", sx + 14, y0 + H - 32, c(Config.SECONDARY));
        rrect(g, sx + sw - 22, y0 + H - 42, sx + sw - 12, y0 + H - 32, 5, 0xFF7CFF8A);

        // ----- header -----
        int hx = sx + sw + 12, hr = x0 + W - 8, hy = y0 + 12;
        rrect(g, hx, hy - 2, hx + 3, hy + 12, 1, c(Config.HIGHLIGHT));
        String welcome = "Welcome Back, ";
        textA(g, welcome, hx + 10, hy + 1, c(Config.TEXT));
        int nx = hx + 10 + textRenderer.getWidth(welcome);
        String cursor = (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : " ";
        textA(g, name + cursor, nx, hy + 1, c(Config.PRIMARY));
        rrect(g, hr - 176, hy - 4, hr - 8, hy + 14, 7, c(Config.SUB));
        search.setX(hr - 168); search.setY(hy + 1); search.setWidth(154);
        search.render(g, mx, my, dt);
        rrect(g, hr - 3, hy - 2, hr, hy + 12, 1, c(Config.HIGHLIGHT));
        g.fill(hx, y0 + 30, hr, y0 + 32, c(Config.HIGHLIGHT));

        // ----- content -----
        int cx = hx, cy = y0 + 42, cr = hr, cb = y0 + H - 10;
        String q = search.getText().trim().toLowerCase();

        switch (tab) {
            case 0 -> drawBuilder(g, mx, my, click, cx, cy, cr, cb, q);
            case 1 -> drawVisuals(g, mx, my, click, cx, cy, cr, q);
            default -> drawSettings(g, mx, my, click, down, cx, cy, cr, cb);
        }
    }

    private void drawBuilder(DrawContext g, int mx, int my, boolean click, int cx, int cy, int cr, int cb, String q) {
        // module row
        rrect(g, cx, cy, cr, cy + 26, 6, c(Config.SUB));
        textA(g, "Schem Builder", cx + 10, cy + 5, c(Config.TEXT));
        textA(g, "Builds .schem files from your position", cx + 10, cy + 15, c(Config.SECONDARY));
        toggle(g, cr - 34, cy + 7, Config.builderEnabled);
        if (click && in(mx, my, cr - 40, cy, 40, 26)) { Config.builderEnabled = !Config.builderEnabled; Config.save(); }

        // mode selector
        int my0 = cy + 32, bw = (cr - cx - 12) / 4;
        for (int i = 0; i < 4; i++) {
            int bx = cx + i * (bw + 4);
            boolean on = Config.mode == i, hov = in(mx, my, bx, my0, bw, 22);
            if (on) rrect(g, bx, my0, bx + bw, my0 + 22, 6, c(Config.HIGHLIGHT));
            else bordered(g, bx, my0, bx + bw, my0 + 22, 6, hov ? 0xFF16201A : c(Config.SUB), 0xFF1E2A22);
            String lab = seed.seeditone.Builder.MODES[i];
            textA(g, lab, bx + (bw - textRenderer.getWidth(lab)) / 2, my0 + 7, on ? 0xFF031008 : c(Config.TEXT));
            if (click && hov) { Config.mode = i; Config.save(); }
        }

        // status row
        int sy = cy + 60;
        rrect(g, cx, sy, cr, sy + 22, 6, c(Config.SUB));
        var b = SeeditoneClient.BUILDER;
        String st = b.isActive() ? "Building: " + b.remaining() + " blocks left" : "Idle";
        textA(g, st, cx + 10, sy + 7, c(Config.TEXT));
        boolean hovStop = in(mx, my, cr - 56, sy + 3, 50, 16);
        rrect(g, cr - 56, sy + 3, cr - 6, sy + 19, 5, hovStop ? c(Config.GLOW_BRIGHT) : c(Config.GLOW));
        textA(g, "Stop", cr - 40, sy + 7, 0xFFFFFFFF);
        if (click && hovStop) b.stop();

        // file list
        int ly = sy + 32;
        textA(g, "Schematics", cx + 2, ly, c(Config.SECONDARY));
        boolean hovRef = in(mx, my, cr - 52, ly - 3, 50, 14);
        textA(g, "Refresh", cr - 44, ly, hovRef ? c(Config.PRIMARY) : c(Config.TEXT));
        if (click && hovRef) refreshFiles();

        List<Path> shown = new ArrayList<>();
        for (Path p : files) if (q.isEmpty() || p.getFileName().toString().toLowerCase().contains(q)) shown.add(p);

        int rowsTop = ly + 14, rowH = 20;
        int maxRows = Math.max(1, (cb - rowsTop) / rowH);
        scroll = Math.min(scroll, Math.max(0, shown.size() - maxRows));

        if (shown.isEmpty()) {
            textA(g, "Drop .schem files into .minecraft/schematics", cx + 2, rowsTop + 4, c(Config.SECONDARY));
            return;
        }
        for (int i = 0; i < maxRows && i + scroll < shown.size(); i++) {
            Path p = shown.get(i + scroll);
            int ry = rowsTop + i * rowH;
            boolean hov = in(mx, my, cx, ry, cr - cx, rowH - 2);
            bordered(g, cx, ry, cr, ry + rowH - 2, 5, c(Config.KEYBIND), hov ? c(Config.HIGHLIGHT) : 0xFF1A211C);
            textA(g, textRenderer.trimToWidth(p.getFileName().toString(), cr - cx - 70), cx + 8, ry + 5, c(Config.TEXT));
            textA(g, "Build", cr - 36, ry + 5, hov ? c(Config.PRIMARY) : c(Config.SECONDARY));
            if (click && hov) {
                String msg = SeeditoneClient.startBuild(client, p);
                if (client.player != null) client.player.sendMessage(Text.literal(msg), false);
                if (SeeditoneClient.BUILDER.isActive()) close();
            }
        }
    }

    private void drawVisuals(DrawContext g, int mx, int my, boolean click, int cx, int cy, int cr, String q) {
        if (!q.isEmpty() && !"show hud".contains(q)) return;
        rrect(g, cx, cy, cr, cy + 26, 6, c(Config.SUB));
        textA(g, "Show HUD", cx + 10, cy + 5, c(Config.TEXT));
        textA(g, "Build progress on screen", cx + 10, cy + 15, c(Config.SECONDARY));
        toggle(g, cr - 34, cy + 7, Config.hudEnabled);
        if (click && in(mx, my, cr - 40, cy, 40, 26)) { Config.hudEnabled = !Config.hudEnabled; Config.save(); }
    }

    private void drawSettings(DrawContext g, int mx, int my, boolean click, boolean down,
                              int cx, int cy, int cr, int cb) {
        // color list
        int listW = 144, rowH = 20;
        for (int i = 0; i < Config.NAMES.length; i++) {
            int ry = cy + i * rowH;
            boolean hov = in(mx, my, cx, ry, listW, rowH);
            if (i == sel) rrect(g, cx, ry, cx + listW, ry + rowH - 2, 3, 0xFF2E3630);
            else if (hov) rrect(g, cx, ry, cx + listW, ry + rowH - 2, 3, 0x20FFFFFF);
            rrect(g, cx + 4, ry + 1, cx + 20, ry + 17, 2, 0xFF262626);
            rrect(g, cx + 4, ry + 1, cx + 20, ry + 17, 2, c(i));
            textA(g, Config.NAMES[i], cx + 26, ry + 5, 0xFFFFFFFF);
            if (click && hov) { sel = i; loadPicker(); }
        }

        // picker
        int px = cx + listW + 12, pw = cr - px, py = cy, ph = cb - cy - 36;
        if (pw < 40 || ph < 40) return;

        if (click) {
            if (in(mx, my, px, py, pw, ph)) drag = 1;
            else if (in(mx, my, px, py + ph + 6, pw, 12)) drag = 2;
            else if (in(mx, my, px, py + ph + 22, pw, 12)) drag = 3;
        }
        if (down && drag != 0) {
            if (drag == 1) { sat = clamp((mx - px) / (float) pw); val = 1f - clamp((my - py) / (float) ph); }
            else if (drag == 2) hue = Math.min(0.999f, clamp((mx - px) / (float) pw));
            else alpha = clamp((mx - px) / (float) pw);
            Config.COLORS[sel] = (Math.round(alpha * 255) << 24) | (Color.HSBtoRGB(hue, sat, val) & 0xFFFFFF);
        }

        // border + SV square
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF555555);
        int cell = 4;
        for (int xx = 0; xx < pw; xx += cell) {
            for (int yy = 0; yy < ph; yy += cell) {
                float s = xx / (float) pw, v = 1f - yy / (float) ph;
                g.fill(px + xx, py + yy, Math.min(px + xx + cell, px + pw), Math.min(py + yy + cell, py + ph),
                    0xFF000000 | (Color.HSBtoRGB(hue, s, v) & 0xFFFFFF));
            }
        }
        int mxk = px + Math.round(sat * (pw - 1)), myk = py + Math.round((1f - val) * (ph - 1));
        g.fill(mxk - 3, myk - 3, mxk + 4, myk + 4, 0xFFFFFFFF);
        g.fill(mxk - 2, myk - 2, mxk + 3, myk + 3, 0xFF000000 | (Color.HSBtoRGB(hue, sat, val) & 0xFFFFFF));

        // hue bar
        int hy = py + ph + 6;
        for (int xx = 0; xx < pw; xx += 3)
            g.fill(px + xx, hy, Math.min(px + xx + 3, px + pw), hy + 12,
                0xFF000000 | (Color.HSBtoRGB(xx / (float) pw, 1f, 1f) & 0xFFFFFF));
        int hk = px + Math.round(hue * (pw - 1));
        g.fill(hk - 2, hy - 1, hk + 3, hy + 13, 0xFFFFFFFF);

        // alpha bar (checker + ramp)
        int ay = py + ph + 22;
        for (int xx = 0; xx < pw; xx += 6)
            for (int yy = 0; yy < 12; yy += 6)
                g.fill(px + xx, ay + yy, Math.min(px + xx + 6, px + pw), ay + yy + 6,
                    (((xx / 6) + (yy / 6)) % 2 == 0) ? 0xFF888888 : 0xFF444444);
        int rgb = Color.HSBtoRGB(hue, sat, val) & 0xFFFFFF;
        for (int xx = 0; xx < pw; xx += 3) {
            int a = Math.round(255f * xx / pw);
            g.fill(px + xx, ay, Math.min(px + xx + 3, px + pw), ay + 12, (a << 24) | rgb);
        }
        int ak = px + Math.round(alpha * (pw - 1));
        g.fill(ak - 2, ay - 1, ak + 3, ay + 13, 0xFFFFFFFF);
    }

    private void loadPicker() {
        int col = Config.COLORS[sel];
        alpha = ((col >>> 24) & 0xFF) / 255f;
        float[] hsb = Color.RGBtoHSB((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, null);
        hue = hsb[0]; sat = hsb[1]; val = hsb[2];
    }

    private static float clamp(float f) { return Math.max(0f, Math.min(1f, f)); }
}
