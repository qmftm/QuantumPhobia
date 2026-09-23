package me.qmftm.quantumPhobia.medicine;

import me.qmftm.quantumPhobia.insanity.InsanityManager;
import me.qmftm.quantumPhobia.insanity.StatChange;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * What an overdosed player hears and sees: a warden heartbeat at irregular intervals, and
 * hallucinated phantoms only they can see, each vanishing once they get close to it.
 *
 * <p>Overdose also drives the hidden insanity value up every second (see
 * {@link InsanityManager#raise}). Each time it reaches 100 it drops back to 0 and one more phantom joins the
 * hallucination. How fast the heart beats and insanity rises depend on the overdose stage.
 *
 * <p>The nausea comes from the QuantumAdders effect itself; this covers what YAML actions can't.
 */
public final class OverdoseSymptoms implements Listener {

    private static final long TICK_PERIOD = 2L;
    /** Getting this close to a phantom makes it vanish. */
    private static final double VANISH_DISTANCE = 10.0;
    /** Wandering this far from a phantom makes it vanish too, so a fresh one appears nearby. */
    private static final double LOST_DISTANCE = 64.0;
    private static final long INSANITY_PERIOD_TICKS = 20L;

    /** One place a phantom can occupy; empty between one vanishing and the next appearing. */
    private static final class Slot {
        Phantom phantom;
        long nextSpawnTick;
    }

    private static final class State {
        int level;
        OverdoseLevel settings;
        long endsAtTick;
        long nextBeatTick;
        long nextInsanityTick;
        final List<Slot> slots = new ArrayList<>();
    }

    private final JavaPlugin plugin;
    private final InsanityManager insanity;
    /** What each hallucination costs the player who sees it appear. */
    private final StatChange hallucination;
    private final Map<UUID, State> states = new HashMap<>();
    /** Every hallucinated phantom, so their targeting and damage can be suppressed. */
    private final Set<UUID> phantoms = new HashSet<>();
    private BukkitTask task;
    private long tick;

    public OverdoseSymptoms(JavaPlugin plugin, InsanityManager insanity) {
        this.plugin = plugin;
        this.insanity = insanity;
        this.hallucination = StatChange.from(plugin.getConfig().getConfigurationSection("overdose.hallucination"),
                new StatChange(0, -5));
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (State state : states.values()) {
            removeAll(state);
        }
        states.clear();
    }

    /** The player's current overdose stage, or 0 if they aren't overdosed. */
    public int level(Player player) {
        State state = states.get(player.getUniqueId());
        return state == null ? 0 : state.level;
    }

    /** Seconds left in the player's overdose, or 0 if they aren't overdosed. */
    public long remainingSeconds(Player player) {
        State state = states.get(player.getUniqueId());
        return state == null ? 0 : Math.max(0, (state.endsAtTick - tick) / 20);
    }

    /** Whether this entity is one of the hallucinated phantoms rather than a real mob. */
    public boolean isHallucination(Entity entity) {
        return phantoms.contains(entity.getUniqueId());
    }

    /** Starts the symptoms at the given stage, restarting the clock for that stage's duration. */
    public void begin(Player player, int level, OverdoseLevel settings) {
        State state = states.computeIfAbsent(player.getUniqueId(), k -> {
            State fresh = new State();
            fresh.nextBeatTick = tick;
            fresh.nextInsanityTick = tick + INSANITY_PERIOD_TICKS;
            return fresh;
        });
        state.level = level;
        state.settings = settings;
        state.endsAtTick = tick + settings.durationTicks();
    }

    public void end(Player player) {
        State state = states.remove(player.getUniqueId());
        if (state != null) {
            removeAll(state);
        }
    }

    private void tick() {
        tick += TICK_PERIOD;
        for (Iterator<Map.Entry<UUID, State>> it = states.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, State> entry = it.next();
            State state = entry.getValue();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || tick >= state.endsAtTick) {
                removeAll(state);
                it.remove();
                continue;
            }
            heartbeat(player, state);
            raiseInsanity(player, state);
            for (Slot slot : state.slots) {
                hallucinate(player, slot);
            }
        }
    }

    private void heartbeat(Player player, State state) {
        if (tick < state.nextBeatTick) {
            return;
        }
        player.playSound(player, Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        // Mostly an uneven pulse at the stage's pace, now and then a sudden quick double beat.
        OverdoseLevel settings = state.settings;
        state.nextBeatTick = tick + (ThreadLocalRandom.current().nextInt(4) == 0
                ? random(Math.max(2, settings.heartbeatMinTicks() / 2), settings.heartbeatMinTicks())
                : random(settings.heartbeatMinTicks(), settings.heartbeatMaxTicks()));
    }

    private void raiseInsanity(Player player, State state) {
        if (tick < state.nextInsanityTick) {
            return;
        }
        state.nextInsanityTick = tick + INSANITY_PERIOD_TICKS;

        if (insanity.raise(player, state.settings.insanityPerSecond()) >= InsanityManager.MAX) {
            insanity.set(player, InsanityManager.MIN);
            Slot slot = new Slot();
            slot.nextSpawnTick = tick + random(10, 40);
            state.slots.add(slot);
        }
    }

    private void hallucinate(Player player, Slot slot) {
        Phantom phantom = slot.phantom;
        if (phantom == null || !phantom.isValid()) {
            slot.phantom = null;
            if (tick >= slot.nextSpawnTick) {
                slot.phantom = spawnPhantom(player);
                insanity.apply(player, hallucination);
            }
            return;
        }

        boolean sameWorld = phantom.getWorld().equals(player.getWorld());
        double distance = sameWorld ? phantom.getLocation().distance(player.getEyeLocation()) : Double.MAX_VALUE;
        if (distance <= VANISH_DISTANCE) {
            player.spawnParticle(Particle.SMOKE, phantom.getLocation(), 30, 0.6, 0.3, 0.6, 0.02);
            removePhantom(slot);
            slot.nextSpawnTick = tick + random(100, 300);
        } else if (distance > LOST_DISTANCE) {
            removePhantom(slot);
            slot.nextSpawnTick = tick + random(20, 60);
        }
    }

    /** Spawns a phantom circling a fixed point some way off, visible to this player alone. */
    private Phantom spawnPhantom(Player player) {
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        double radius = ThreadLocalRandom.current().nextDouble(16, 26);
        Location anchor = player.getLocation().add(
                Math.cos(angle) * radius, random(6, 10), Math.sin(angle) * radius);
        // Don't start it inside a hill.
        for (int i = 0; i < 20 && !anchor.getBlock().isPassable(); i++) {
            anchor.add(0, 1, 0);
        }

        Phantom phantom = player.getWorld().spawn(anchor, Phantom.class, p -> {
            // Hidden from everyone before it exists, so no one else ever sees it flicker in.
            p.setVisibleByDefault(false);
            p.setInvulnerable(true);
            p.setSilent(true);
            p.setPersistent(false);
            p.setRemoveWhenFarAway(false);
            p.setCollidable(false);
            p.setShouldBurnInDay(false);
            p.setAnchorLocation(anchor.clone());
        });
        phantoms.add(phantom.getUniqueId());
        player.showEntity(plugin, phantom);
        return phantom;
    }

    private void removeAll(State state) {
        for (Slot slot : state.slots) {
            removePhantom(slot);
        }
    }

    private void removePhantom(Slot slot) {
        if (slot.phantom != null) {
            phantoms.remove(slot.phantom.getUniqueId());
            slot.phantom.remove();
            slot.phantom = null;
        }
    }

    /** A hallucination doesn't hunt anyone, least of all the players who can't see it. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (phantoms.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (phantoms.contains(event.getDamager().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        end(event.getPlayer());
    }

    private static int random(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
