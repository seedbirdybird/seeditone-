package seed.seeditone;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import seed.seeditone.gui.SeeditoneScreen;

import java.nio.file.Files;
import java.nio.file.Path;

public class SeeditoneClient implements ClientModInitializer {
    public static final Builder BUILDER = new Builder();
    private boolean keyWasDown = false;

    @Override
    public void onInitializeClient() {
        Config.load();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("seed")
                .executes(ctx -> { openGui(); return 1; })
                .then(ClientCommandManager.literal("gui").executes(ctx -> { openGui(); return 1; }))
                .then(ClientCommandManager.literal("stop").executes(ctx -> {
                    BUILDER.stop();
                    ctx.getSource().sendFeedback(Text.literal("[Seeditone] Stopped."));
                    return 1;
                }))
                .then(ClientCommandManager.literal("mode")
                    .then(ClientCommandManager.argument("m", StringArgumentType.word()).executes(ctx -> {
                        int v = switch (StringArgumentType.getString(ctx, "m").toLowerCase()) {
                            case "none" -> 0; case "assist" -> 1; case "semi" -> 2; case "full", "auto" -> 3; default -> -1;
                        };
                        if (v < 0) { ctx.getSource().sendFeedback(Text.literal("[Seeditone] /seed mode none|assist|semi|full")); return 0; }
                        Config.mode = v; Config.save();
                        ctx.getSource().sendFeedback(Text.literal("[Seeditone] Mode: " + Builder.MODES[v]));
                        return 1;
                    })))
                .then(ClientCommandManager.literal("buycmd")
                    .then(ClientCommandManager.argument("cmd", StringArgumentType.greedyString()).executes(ctx -> {
                        Config.buyCommand = StringArgumentType.getString(ctx, "cmd").trim(); Config.save();
                        ctx.getSource().sendFeedback(Text.literal("[Seeditone] Buy command: /" + Config.buyCommand.replaceFirst("^/", "")));
                        return 1;
                    })))
                .then(ClientCommandManager.literal("build")
                    .then(ClientCommandManager.argument("file", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "file").trim();
                            if (!name.contains(".")) name += ".schem";
                            String msg = startBuild(MinecraftClient.getInstance(), Config.schematicsDir().resolve(name));
                            ctx.getSource().sendFeedback(Text.literal(msg));
                            return 1;
                        })))));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (Config.builderEnabled) BUILDER.tick(mc);
            if (mc.getWindow() != null) {
                boolean down = GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                if (down && !keyWasDown && mc.currentScreen == null && mc.player != null) mc.setScreen(new SeeditoneScreen());
                keyWasDown = down;
            }
        });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of("seeditone", "hud"), (ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!Config.hudEnabled || mc.player == null) return;
            String s = "Seeditone";
            if (BUILDER.isActive()) s += "  |  " + (BUILDER.total() - BUILDER.remaining()) + "/" + BUILDER.total();
            ctx.drawText(mc.textRenderer, s, 6, 6, Config.color(Config.HIGHLIGHT), true);
        });
    }

    public static void openGui() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.send(() -> mc.setScreen(new SeeditoneScreen()));
    }

    public static String startBuild(MinecraftClient mc, Path file) {
        if (!Config.builderEnabled) return "[Seeditone] Schem Builder is turned off.";
        if (mc.player == null) return "[Seeditone] Not in a world.";
        if (!Files.exists(file)) return "[Seeditone] Not found: " + file;
        try {
            Schem s = Schem.load(file);
            String mats = BUILDER.start(s, mc.player.getBlockPos());
            return "[Seeditone] " + Builder.MODES[Config.mode] + " | " + s.w + "x" + s.h + "x" + s.l
                + " (" + BUILDER.total() + " blocks) | " + mats;
        } catch (Exception e) {
            return "[Seeditone] Load failed: " + e;
        }
    }
}
