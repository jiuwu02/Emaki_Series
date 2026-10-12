package emaki.jiuwu.craft.structure.selection;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ParticleSelectionHighlighter implements SelectionHighlighter {

    private static final long PERIOD_TICKS = 2L;
    private static final double STEP = 1.0d;

    private final EmakiStructurePlugin plugin;
    private final ExecutionDispatcher dispatcher;

    private final Map<UUID, TaskToken> tasks = new HashMap<>();

    public ParticleSelectionHighlighter(EmakiStructurePlugin plugin, ExecutionDispatcher dispatcher) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
    }

    @Override
    public void refresh(Player player, SelectionSession session) {
        destroy(player);
        if (!session.hasBothCorners()) {
            return;
        }
        TaskToken token = dispatcher.runGlobalTimer(plugin, () -> paint(player, session),
                PERIOD_TICKS, PERIOD_TICKS);
        if (token != null) {
            tasks.put(player.getUniqueId(), token);
        }
    }

    @Override
    public void destroy(Player player) {
        TaskToken token = tasks.remove(player.getUniqueId());
        if (token != null) {
            token.cancel();
        }
    }

    private void paint(Player player, SelectionSession session) {
        try {
            render(player, session);
        } catch (RuntimeException exception) {
            plugin.debugLogger().log("structure", player, "highlight_degraded",
                    Map.of("reason", String.valueOf(exception.getMessage())));
            destroy(player);
        }
    }

    private void render(Player player, SelectionSession session) {
        if (!player.isOnline()) {
            destroy(player);
            return;
        }
        SelectionBox box = session.box().orElse(null);
        if (box == null || !session.worldName().equals(player.getWorld().getName())) {
            destroy(player);
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0x86, 0xEF, 0xAC), 1.4f);
        Location origin = new Location(player.getWorld(), 0.0d, 0.0d, 0.0d);
        for (Edge edge : edges(box)) {
            double length = edge.length();
            for (double offset = 0.0d; offset <= length + 0.001d; offset += STEP) {
                double ratio = length <= 0.0d ? 0.0d : offset / length;
                Location point = edge.at(origin, ratio);
                player.spawnParticle(Particle.DUST, point, 1, 0.0d, 0.0d, 0.0d, 0.0d, dust);
            }
        }
    }

    private record Edge(int x1, int y1, int z1, int x2, int y2, int z2) {

        double length() {
            double dx = x2 - x1;
            double dy = y2 - y1;
            double dz = z2 - z1;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        Location at(Location origin, double ratio) {
            return origin.clone().add(
                    x1 + (x2 - x1) * ratio,
                    y1 + (y2 - y1) * ratio,
                    z1 + (z2 - z1) * ratio);
        }
    }

    private static Edge[] edges(SelectionBox box) {
        int x1 = box.minX();
        int y1 = box.minY();
        int z1 = box.minZ();
        int x2 = box.maxX() + 1;
        int y2 = box.maxY() + 1;
        int z2 = box.maxZ() + 1;
        return new Edge[] {
                new Edge(x1, y1, z1, x2, y1, z1),
                new Edge(x1, y1, z2, x2, y1, z2),
                new Edge(x1, y2, z1, x2, y2, z1),
                new Edge(x1, y2, z2, x2, y2, z2),
                new Edge(x1, y1, z1, x1, y1, z2),
                new Edge(x2, y1, z1, x2, y1, z2),
                new Edge(x1, y2, z1, x1, y2, z2),
                new Edge(x2, y2, z1, x2, y2, z2),
                new Edge(x1, y1, z1, x1, y2, z1),
                new Edge(x2, y1, z1, x2, y2, z1),
                new Edge(x1, y1, z2, x1, y2, z2),
                new Edge(x2, y1, z2, x2, y2, z2)
        };
    }

    public void close() {
        for (TaskToken token : tasks.values()) {
            token.cancel();
        }
        tasks.clear();
    }
}
