package me.wild;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.CommandBlock;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.craftbukkit.v1_21_R5.block.CraftBlock;
import org.bukkit.craftbukkit.v1_21_R5.block.CraftBlockEntityState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.minecraft.nbt.NBTTagCompound;

public class CommandBlockManager implements Listener {
	private Plugin plugin;
	private HashMap<Block, ScheduledTask> repeatingBlocks = new HashMap<>();
	private List<Block> poweredBlocks = new ArrayList<>();
	
	public CommandBlockManager(Plugin plugin) {
		this.plugin = plugin;
	}
	
	@EventHandler
    public void onServerCommandEvent(ServerCommandEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getSender() instanceof BlockCommandSender)) return;

        BlockCommandSender  sender = (BlockCommandSender) event.getSender();
        Block block = sender.getBlock();
        BlockState state = block.getState();

        if (!(state instanceof CommandBlock)) return;
        CommandBlock cb = (CommandBlock) state;
        if (!cb.isPlaced()) return;

        if (block.getType() == Material.REPEATING_COMMAND_BLOCK) {
        	Bukkit.getRegionScheduler().runAtFixedRate(plugin, cb.getLocation(), (task) -> {
        		if (isCommandBlockAlwaysActive(block)) {
        			if (!cb.isPlaced() || block.isEmpty()) {
            			repeatingBlocks.remove(block);
            			task.cancel();
            			return;
                	}
        		} else {
        			if (!cb.isPlaced() || !isBlockPowered(block) || block.isEmpty()) {
            			repeatingBlocks.remove(block);
            			task.cancel();
            			return;
                	}
        		}
            	// check if command failed and stop firing if so.
            	boolean status = processCommandBlock(block, event.getCommand());
            	if (repeatingBlocks.get(block) == null) {
            		processChainCommandBlock(block, new ArrayList<>(), status);
            		repeatingBlocks.put(block, task);
            	}
            }, 1L, 1L);
        }
        else if (block.getType() == Material.COMMAND_BLOCK ) {
        	boolean status = processCommandBlock(block, event.getCommand());
        	processChainCommandBlock(block, new ArrayList<>(), status);
        }
    }

    // Processes command block. 
    // This handles firing the command and tracking the failures.
	// True -> completed
	// False -> Command failed
    private boolean processCommandBlock(Block block, String command) {
    	List<Block> failedCondition = new ArrayList<>();
    	Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            boolean status = Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), command);
            if (status == false) failedCondition.add(block);
        });
    	return failedCondition.contains(block);
	}
    
    private void processChainCommandBlock(Block lastBlock, List<Block> history, boolean previousFailed) {
    	Block current =  getNextChainCommandBlock(lastBlock);
    	
    	if (current == null || current.getType() != Material.CHAIN_COMMAND_BLOCK || history.contains(current)) return;
    	CommandBlock cb = (CommandBlock)current.getState();
    	 if (!cb.isPlaced()) return;
    	org.bukkit.block.data.type.CommandBlock data = (org.bukkit.block.data.type.CommandBlock) cb.getBlockData();
    	String command = (cb.getCommand().isEmpty() ? "" : cb.getCommand().replace("/", ""));
		if (command.isEmpty()) {
			return;
		}
        
        if (previousFailed && data.isConditional()) {
        	return;
        }
        
		history.add(current);
		boolean status = processCommandBlock(current, command);
		processChainCommandBlock(current, history, status);
    }

    private Block getNextChainCommandBlock(Block block) {
        if (!(block.getState() instanceof CommandBlock)) return null;
        BlockFace facing = ((org.bukkit.block.data.type.CommandBlock) block.getBlockData()).getFacing();
        Block nextBlock = block.getRelative(facing);
        return nextBlock.getType() == Material.CHAIN_COMMAND_BLOCK ? nextBlock : null;
    }
    
    @EventHandler
    public void redstoneChanges(BlockRedstoneEvent e) {
        Block block = e.getBlock();
        BlockState state = block.getState();
        
        if (state instanceof CommandBlock) Bukkit.getLogger().warning("Power: " + isCommandBlockAlwaysActive(block));

        // If powered for the first time, handle normally
        if (e.getNewCurrent() > 0 && isBlockPowered(block) && !poweredBlocks.contains(block)) {
            if (state instanceof CommandBlock && block.getType() != Material.CHAIN_COMMAND_BLOCK)  {
            	poweredBlocks.add(block);
            	CommandBlock cb = (CommandBlock) state;
            	String command = (cb.getCommand().isEmpty() ? "" : cb.getCommand().replace("/", ""));
            	if (command.isEmpty()) return;
            	ServerCommandEvent commandEvent = new ServerCommandEvent(new CommandBlockSender(block), command);
            	Bukkit.getPluginManager().callEvent(commandEvent);
            }
        }

        if (state instanceof CommandBlock && e.getNewCurrent() == 0) {
            poweredBlocks.remove(block);
            if (repeatingBlocks.containsKey(block)) {
            	repeatingBlocks.get(block).cancel();
                repeatingBlocks.remove(block);
            }
        }
    }
    
    public boolean isBlockPowered(Block block) {
    	if (block.isBlockPowered() || 
    			block.isBlockIndirectlyPowered() || isCommandBlockAlwaysActive(block)) return true;
    	return false;
    }
    
    public boolean isCommandBlockAlwaysActive(Block block) {
        if (!(block.getState() instanceof CommandBlock)) return false;

        try {
            // Access the Command Block's NBT Data
            CraftBlockEntityState<?> craftState = (CraftBlockEntityState<?>) block.getState();
            NBTTagCompound nbt = craftState.getSnapshotNBT();

            // Read the "auto" tag (1b = Always Active, 0b = Needs Redstone)\
            Optional<Byte> auto = nbt.c("auto");
            return auto.get() == 1;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }
}
