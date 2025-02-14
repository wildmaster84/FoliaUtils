package me.wild;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.UUID;

public class Main extends JavaPlugin implements Listener {
	public static long TICKS_PER_COMMAND_BLOCK = 3L;  // 3 Game ticks
    // Store the last known location of each player
	private HashMap<UUID, Location> playerLocations = new HashMap<>();
	private HashMap<Chunk, Integer> customSpawns = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new CommandBlockManager(this), this);
    }
    
    public void onDisable() {
    }
   
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.COMMAND) {
            Chunk chunk = event.getLocation().getChunk();
            customSpawns.putIfAbsent(chunk, 0);
            int currentCount = customSpawns.get(chunk);

            if (currentCount >= 300) {
            	event.getEntity().remove();
                event.setCancelled(true);
                return;
            }
            customSpawns.put(chunk, currentCount + 1);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
    	if (event.getEntity().getEntitySpawnReason() == CreatureSpawnEvent.SpawnReason.COMMAND) {
    		Chunk chunk = event.getEntity().getLocation().getChunk();
            if (customSpawns.containsKey(chunk)) {
                customSpawns.put(chunk, Math.max(0, customSpawns.get(chunk) - 1));
            }
    	}
    }
    
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
    	Player player = event.getPlayer();
    	player.getScheduler().runAtFixedRate(this, (newTask) -> {
			Location currentLocation = player.getLocation();
			Location lastLocation = playerLocations.get(player.getUniqueId());
			
			if (lastLocation != null && isInstantTeleport(lastLocation, currentLocation, player)) {
                PlayerTeleportEvent teleportEvent = new PlayerTeleportEvent(player, lastLocation, currentLocation);
                Bukkit.getPluginManager().callEvent(teleportEvent);
            }

            playerLocations.put(player.getUniqueId(), currentLocation);
			
		}, () -> playerLocations.remove(player.getUniqueId()),  1L, 5L);
    }

    /**
     * Determines if a movement is an instant teleport (rather than normal movement).
     * @param vector 
     */
    /**
     * Determines if a movement is an instant teleport (rather than normal movement).
     * Checks velocity to ensure it is not just fast movement.
     */
    private boolean isInstantTeleport(Location from, Location to, Player player) {
    	if (player == null) return false;
        // Ensure worlds are different first
        if (from.getWorld() != to.getWorld()) {
        	PlayerChangedWorldEvent teleportEvent = new PlayerChangedWorldEvent(player, from.getWorld());
            Bukkit.getPluginManager().callEvent(teleportEvent);
            return true;
        }

        // Compute the squared distance moved
        double distanceMoved = from.distanceSquared(to);

        // Compute expected max movement based on velocity
        double velocityMagnitude = player.getVelocity().lengthSquared();

        // Allowable movement variance (higher allows more leeway)
        double allowedMovement = velocityMagnitude * 4 + 78.0; // Elytra is 76 so there is no faster except mods

        boolean movedTooFast = distanceMoved > allowedMovement;
        // If the player moved much more than expected, it's a teleport
        return movedTooFast;
    }
}
