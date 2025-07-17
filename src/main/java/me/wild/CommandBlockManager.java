package me.wild;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.CommandBlock;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandException;
import org.bukkit.craftbukkit.v1_21_R5.block.CraftBlock;
import org.bukkit.craftbukkit.v1_21_R5.block.CraftBlockEntityState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
        		processCommandBlock(block, event.getCommand()).thenAccept(result -> {
            		if (!result.getStatus()) {
            			repeatingBlocks.remove(block);
            			task.cancel();
            			return;
            		}
            		if (repeatingBlocks.get(block) == null) {
                		processChainCommandBlock(block, new ArrayList<>(), result);
                		repeatingBlocks.put(block, task);
                	}
        		});
            	
            }, 1L, 1L);
        }
        else if (block.getType() == Material.COMMAND_BLOCK ) {
        	
        	processCommandBlock(block, event.getCommand()).thenAccept(result -> {
        		if (!result.getStatus()) return;
        		processChainCommandBlock(block, new ArrayList<>(), result);
    		});
        }
    }

    // Processes command block. 
    // This handles firing the command and tracking the failures.
	// True -> completed
	// False -> Command failed
	// This contains a race condition
	public CompletableFuture<CommandBlockOutput> processCommandBlock(Block block, String command) {
	    CompletableFuture<CommandBlockOutput> future = new CompletableFuture<>();

	    // Run the command on the global scheduler
	    Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
	        String error = "";
	        try {
	            boolean status = Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), command);
	            if (!status) {
	                error = "Unknown or Invalid command!";
	            }
	        } catch (CommandException e) {
	            error = e.getLocalizedMessage();
	        }
	        String finalError = error;

	        // Update the block on the block's region thread
	        Bukkit.getRegionScheduler().execute(plugin, block.getLocation(), () -> {
	            if (!(block.getState() instanceof CommandBlock cb)) {
	                future.complete(new CommandBlockOutput(false, "Not a command block!"));
	                return;
	            }

	            if (!cb.isPlaced()) {
	                future.complete(new CommandBlockOutput(false, "Command block is not placed!"));
	                return;
	            }

	            if (finalError.isEmpty()) {
	                cb.setSuccessCount(cb.getSuccessCount() + 1);
	            }

	            Component output = LegacyComponentSerializer.legacySection().deserialize(finalError.isEmpty() ? "" : finalError);
	            cb.lastOutput(output);
	            cb.update();

	            future.complete(new CommandBlockOutput(finalError.isEmpty(), finalError));
	        });
	    });
	    return future;
	}
    
    private void processChainCommandBlock(Block lastBlock, List<Block> history, CommandBlockOutput output) {
    	Block current =  getNextChainCommandBlock(lastBlock);
    	
    	if (current == null || current.getType() != Material.CHAIN_COMMAND_BLOCK || history.contains(current)) return;
    	CommandBlock cb = (CommandBlock)current.getState();
    	 if (!cb.isPlaced()) return;
    	org.bukkit.block.data.type.CommandBlock data = (org.bukkit.block.data.type.CommandBlock) cb.getBlockData();
    	String command = (cb.getCommand().isEmpty() ? "" : cb.getCommand().replace("/", ""));
		if (command.isEmpty()) {
			return;
		}
        if (data.isConditional()) {
        	CommandBlock lastcb = (CommandBlock)lastBlock.getState();
        	TextComponent text = (TextComponent)lastcb.lastOutput();
        	if (!lastcb.isPlaced() || !text.content().isEmpty()) {
        		Bukkit.getLogger().info("Placed: " + lastcb.isPlaced());
        		Bukkit.getLogger().info("Output: " + text.content());
        		return;
        	}
        }
        
		history.add(current);
		processCommandBlock(current, command).thenAccept(result -> {
		    processChainCommandBlock(current, history, result);
		});
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
    			block.isBlockIndirectlyPowered() || 
    			isCommandBlockAlwaysActive(block)) return true;
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
    
    private class CommandBlockOutput {
    	boolean status;
    	String error;
    	public CommandBlockOutput(boolean status, String error) {
    		this.error = error;
    		this.status = status;
    	}
    	
    	public boolean getStatus() {
    		return this.status;
    	}
    	public String getError() {
    		return this.error;
    	}
    }
}
