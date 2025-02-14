package me.wild;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.CommandBlock;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class CommandBlockManager implements Listener {
	private Plugin plugin;
	private HashMap<Block, ScheduledTask> repeatingBlocks = new HashMap<>();
	private List<Block> failedCondition = new ArrayList<>();
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
        	ScheduledTask taskId = Bukkit.getRegionScheduler().runAtFixedRate(
        			plugin, cb.getLocation(), (task) -> {
                        if (!cb.isPlaced() || !isBlockPowered(block) || block.isEmpty()) {
                            repeatingBlocks.remove(block);
                            task.cancel();
                            return;
                        }
                        processCommandBlock(block, sender);
                    }, 1L, 1L);
            repeatingBlocks.put(block, taskId);
            processChainCommandBlock(block, sender, new ArrayList<>());
        }
        else if (block.getType() == Material.COMMAND_BLOCK ) {
        	processCommandBlock(block, sender);
        	processChainCommandBlock(block, sender, new ArrayList<>());
        }
    }

    // Processes command block. 
    // This handles firing the command and tracking the failures.
    private void processCommandBlock(Block block, BlockCommandSender  sender) {
    	CommandBlock cb = (CommandBlock) block.getState();
    	if (!cb.getCommand().isEmpty()) {
    		Bukkit.getRegionScheduler().runDelayed(plugin, cb.getLocation(), (command) -> {
    			Bukkit.getGlobalRegionScheduler().run(plugin, (task) -> {
                    boolean status = Bukkit.dispatchCommand(sender.getServer().getConsoleSender(), cb.getCommand());
                    if (status == false) failedCondition.add(block);
                });
    		}, 1L);
    	}
	}
    
    private void processChainCommandBlock(Block block, BlockCommandSender sender, List<Block> history) {
    	CommandBlock cb = (CommandBlock) block.getState();
    	Bukkit.getRegionScheduler().runDelayed(plugin, cb.getLocation(), (task) -> {
    		Block nextBlock =  getNextChainBlock(block, sender);
    		if (nextBlock == null || history.contains(nextBlock)) {
    			task.cancel();
    			return;
    		}
    		history.add(nextBlock);
    		processCommandBlock(nextBlock, sender);
    		processChainCommandBlock(nextBlock, sender, history);
        }, 1L);
    }

	private Block getNextChainBlock(Block block, BlockCommandSender  sender) {
		Block current = block;
        Block nextBlock = getNextChainCommandBlock(block);
        if (nextBlock == null) return null;
        
        CommandBlock cb = (CommandBlock) nextBlock.getState();
        org.bukkit.block.data.type.CommandBlock data = (org.bukkit.block.data.type.CommandBlock) cb.getBlockData();
        if (!cb.isPlaced()) return null;
        
        if (failedCondition.contains(current)) cb.setSuccessCount(0); else  cb.setSuccessCount(1);
        
        // If List contains current block and is conditional then fail.
        if (failedCondition.contains(current) && data.isConditional()) {
        	failedCondition.remove(current);
        	return null;
        }
        return nextBlock;
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
            	ServerCommandEvent commandEvent = new ServerCommandEvent(new CommandBlockSender(block), cb.getCommand());
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
    			block.isBlockIndirectlyPowered()) return true;
    	return false;
    }
}
