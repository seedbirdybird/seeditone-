package seed.seeditone;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Modes (Config.mode): 0 None, 1 Build Assist (places what's in reach while YOU move),
 * 2 Semi Build (walks + places, you supply materials), 3 Full Auto (Semi + buys missing items via Config.buyCommand).
 */
public class Builder {
    public static final String[] MODES = {"None", "Assist", "Semi", "Full Auto"};
    private static final double REACH = 4.2;
    private static final int PLACES_PER_TICK = 2;
    private static final int MAX_BUYS_PER_ITEM = 3;

    private static class Target {
        final BlockPos pos; final BlockState state; int tries = 0;
        Target(BlockPos p, BlockState s) { pos = p; state = s; }
    }

    private final List<Target> queue = new ArrayList<>();
    private final Map<Block, Integer> buys = new HashMap<>();
    private boolean active = false;
    private int total = 0, buyCooldown = 0;
    private Block lastMissing = null;

    public boolean isActive() { return active; }
    public int remaining() { return queue.size(); }
    public int total() { return total; }

    /** Loads the schematic into the build queue and returns a materials summary. */
    public String start(Schem s, BlockPos origin) {
        queue.clear(); buys.clear(); lastMissing = null; buyCooldown = 0;
        for (int y = 0; y < s.h; y++)
            for (int z = 0; z < s.l; z++)
                for (int x = 0; x < s.w; x++) {
                    BlockState st = s.states[(y * s.l + z) * s.w + x];
                    if (st == null || st.isAir() || st.getBlock().asItem() == Items.AIR) continue;
                    queue.add(new Target(origin.add(x, y, z), st));
                }
        queue.sort(Comparator.<Target>comparingInt(t -> t.pos.getY())
            .thenComparingInt(t -> t.pos.getZ()).thenComparingInt(t -> t.pos.getX()));
        total = queue.size();
        active = true;
        return materials();
    }

    public String materials() {
        Map<Block, Integer> m = new HashMap<>();
        for (Target t : queue) m.merge(t.state.getBlock(), 1, Integer::sum);
        StringBuilder sb = new StringBuilder();
        m.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(8).forEach(e ->
            sb.append(e.getValue()).append("x ").append(Registries.BLOCK.getId(e.getKey()).getPath()).append(", "));
        if (sb.length() > 2) sb.setLength(sb.length() - 2);
        return "Materials: " + sb + (m.size() > 8 ? " (+" + (m.size() - 8) + " more types)" : "");
    }

    public void stop() {
        active = false;
        queue.clear();
        releaseKeys(MinecraftClient.getInstance());
    }

    public void tick(MinecraftClient mc) {
        if (!active || Config.mode == 0) return;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) { stop(); return; }

        queue.removeIf(t -> mc.world.getBlockState(t.pos).getBlock() == t.state.getBlock());
        if (queue.isEmpty()) {
            releaseKeys(mc);
            active = false;
            msg(p, "Build finished.");
            return;
        }
        if (buyCooldown > 0) { buyCooldown--; return; }

        if (Config.mode == 1) { placeInReach(mc, p, false); return; } // assist: no walking, no missing-item handling

        Target first = queue.get(0);
        if (p.getEyePos().distanceTo(Vec3d.ofCenter(first.pos)) > REACH) {
            walkToward(mc, p, first.pos);
            return;
        }
        releaseKeys(mc);
        placeInReach(mc, p, true);
    }

    private void placeInReach(MinecraftClient mc, ClientPlayerEntity p, boolean handleMissing) {
        int placed = 0;
        for (int i = 0; i < queue.size() && placed < PLACES_PER_TICK; i++) {
            Target t = queue.get(i);
            if (p.getEyePos().distanceTo(Vec3d.ofCenter(t.pos)) > REACH) continue;
            if (!selectItem(mc, p, t.state.getBlock())) {
                if (handleMissing && onMissing(p, t.state.getBlock())) return;
                continue;
            }
            if (place(mc, p, t.pos)) placed++;
            else if (++t.tries > 40) queue.remove(i--);
        }
    }

    /** Returns true if it started a purchase (caller should stop this tick). */
    private boolean onMissing(ClientPlayerEntity p, Block b) {
        String id = Registries.BLOCK.getId(b).getPath();
        int need = 0;
        for (Target t : queue) if (t.state.getBlock() == b) need++;
        if (lastMissing != b) { lastMissing = b; msg(p, "Missing " + id + " (need " + need + ")"); }

        if (Config.mode == 3 && !Config.buyCommand.isBlank()) {
            int n = buys.merge(b, 1, Integer::sum);
            if (n <= MAX_BUYS_PER_ITEM) {
                String cmd = Config.buyCommand.replace("{item}", id).replace("{amount}", String.valueOf(Math.min(need, 2304)));
                p.networkHandler.sendChatCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
                msg(p, "Buying " + Math.min(need, 2304) + "x " + id + " (attempt " + n + "/" + MAX_BUYS_PER_ITEM + ")");
                buyCooldown = 100;
                return true;
            }
        }
        return false; // semi / out of buy attempts: skip for now, keep building what we can
    }

    private boolean place(MinecraftClient mc, ClientPlayerEntity p, BlockPos target) {
        for (Direction d : Direction.values()) {
            BlockPos n = target.offset(d);
            BlockState ns = mc.world.getBlockState(n);
            if (ns.isAir() || !ns.getFluidState().isEmpty()) continue;
            Direction face = d.getOpposite();
            Vec3d hitPos = Vec3d.ofCenter(n).add(new Vec3d(face.getOffsetX(), face.getOffsetY(), face.getOffsetZ()).multiply(0.5));
            BlockHitResult hit = new BlockHitResult(hitPos, face, n, false);
            ActionResult r = mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
            if (r.isAccepted()) { p.swingHand(Hand.MAIN_HAND); return true; }
        }
        return false;
    }

    private boolean selectItem(MinecraftClient mc, ClientPlayerEntity p, Block block) {
        Item item = block.asItem();
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 9; i++)
            if (inv.getStack(i).isOf(item)) { inv.setSelectedSlot(i); return true; }
        for (int i = 9; i < 36; i++)
            if (inv.getStack(i).isOf(item)) {
                mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, i, inv.getSelectedSlot(),
                    SlotActionType.SWAP, p);
                return true;
            }
        if (p.isCreative()) {
            mc.interactionManager.clickCreativeStack(new ItemStack(item), 36 + inv.getSelectedSlot());
            return true;
        }
        return false;
    }

    private void walkToward(MinecraftClient mc, ClientPlayerEntity p, BlockPos goal) {
        Vec3d d = Vec3d.ofCenter(goal).subtract(p.getEyePos());
        p.setYaw((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        mc.options.forwardKey.setPressed(true);
        mc.options.jumpKey.setPressed(p.horizontalCollision || d.y > 1.5);
    }

    private void releaseKeys(MinecraftClient mc) {
        if (mc == null || mc.options == null) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
    }

    private static void msg(ClientPlayerEntity p, String s) { p.sendMessage(Text.literal("[Seeditone] " + s), false); }
}
