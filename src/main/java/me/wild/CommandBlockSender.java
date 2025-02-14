package me.wild;

import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.CommandBlock;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

public class CommandBlockSender implements BlockCommandSender {
    private final Block block;
    private final CommandBlock commandBlock;
    private TextComponent name = Component.text("@");

    public CommandBlockSender(Block block) {
        this.block = block;
        this.commandBlock = (CommandBlock) block.getState();
    }

	@Override
	public void sendMessage(@NotNull String message) {
		commandBlock.lastOutput(Component.text(message));
	}

	@Override
	public void sendMessage(@NotNull String... messages) {
		
	}

	@Override
	public void sendMessage(@Nullable UUID sender, @NotNull String message) {
	}

	@Override
	public void sendMessage(@Nullable UUID sender, @NotNull String... messages) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public @NotNull Server getServer() {
		// TODO Auto-generated method stub
		return Bukkit.getServer();
	}

	@Override
	public @NotNull String getName() {
		// TODO Auto-generated method stub
		return name.content();
	}

	@Override
	public @NotNull Spigot spigot() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public @NotNull Component name() {
		// TODO Auto-generated method stub
		return name;
	}

	@Override
	public boolean isPermissionSet(@NotNull String name) {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public boolean isPermissionSet(@NotNull Permission perm) {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public boolean hasPermission(@NotNull String name) {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public boolean hasPermission(@NotNull Permission perm) {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value,
			int ticks) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, int ticks) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void removeAttachment(@NotNull PermissionAttachment attachment) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void recalculatePermissions() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public @NotNull Set<PermissionAttachmentInfo> getEffectivePermissions() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isOp() {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public void setOp(boolean value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public @NotNull Block getBlock() {
		// TODO Auto-generated method stub
		return block;
	}
}
