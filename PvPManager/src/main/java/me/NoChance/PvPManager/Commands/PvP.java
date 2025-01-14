package me.NoChance.PvPManager.Commands;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import com.google.common.collect.Lists;

import me.NoChance.PvPManager.PvPlayer;
import me.NoChance.PvPManager.Managers.PlayerHandler;
import me.NoChance.PvPManager.Settings.Messages;
import me.NoChance.PvPManager.Utils.ChatUtils;
import me.NoChance.PvPManager.Utils.CombatUtils;

public class PvP implements TabExecutor {

	private final PlayerHandler ph;

	public PvP(final PlayerHandler playerHandler) {
		this.ph = playerHandler;
	}

	@Override
	public final boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
		if (!sender.hasPermission("pvpmanager.pvpstatus.change")) {
			sender.sendMessage(Messages.getErrorPermission());
			return true;
		}

		if (sender instanceof Player && args.length == 0) {
			final Player player = (Player) sender;
			final PvPlayer pvpPlayer = ph.get(player);
			togglePvP(pvpPlayer, !pvpPlayer.hasPvPEnabled());
			return true;
		}

		if (args.length == 1) {
			if (sender instanceof Player && (args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("on"))) {
				final PvPlayer pvpPlayer = ph.get((Player) sender);
				final boolean state = args[0].equalsIgnoreCase("on");
				togglePvP(pvpPlayer, state);
				return true;
			} else if (sender.hasPermission("pvpmanager.admin")) {
				togglePvPAdmin(sender, args[0], false, true);
				return true;
			}
			return false;
		}

		if (args.length == 2 && sender.hasPermission("pvpmanager.admin") && (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("on"))) {
			togglePvPAdmin(sender, args[0], args[1].equalsIgnoreCase("on"), false);
			return true;
		}

		sender.sendMessage(Messages.getErrorCommand());
		return true;
	}

	private void togglePvP(final PvPlayer player, final boolean state) {
		if (!player.hasToggleCooldownPassed())
			return;

		if (player.hasPvPEnabled() == state) {
			player.message(state ? Messages.getAlreadyEnabled() : Messages.getAlreadyDisabled());
			return;
		}

		// temporary since some people like to add the * permission on their servers for some reason
		if (!player.getPlayer().hasPermission("*")) {
			if (state && player.getPlayer().hasPermission("pvpmanager.nopvp")) {
				player.message(Messages.getErrorPvPToggleNoPvP());
				return;
			} else if (!state && player.getPlayer().hasPermission("pvpmanager.forcepvp")) {
				player.message(Messages.getErrorPvPToggleForcePvP());
				return;
			}
		}

		player.setPvP(state);
	}

	private void togglePvPAdmin(final CommandSender sender, final String playerName, final boolean state, final boolean toggle) {
		if (!CombatUtils.isOnline(playerName)) {
			sender.sendMessage(Messages.getErrorPlayerNotFound().replace("%p", playerName));
			return;
		}
		final PvPlayer specifiedPlayer = ph.get(Bukkit.getPlayer(playerName));
		specifiedPlayer.setPvP(toggle ? !specifiedPlayer.hasPvPEnabled() : state);
		final String stateMessage = specifiedPlayer.hasPvPEnabled() ? Messages.getEnabled() : Messages.getDisabled();
		sender.sendMessage(Messages.getPvPToggleAdminChanged().replace("%p", playerName).replace("%state", stateMessage));
	}

	@Override
	public List<String> onTabComplete(final CommandSender sender, final Command command, final String label, final String[] args) {
		if (args.length == 1) {
			if (!sender.hasPermission("pvpmanager.admin"))
				return ChatUtils.getMatchingEntries(args[0], Lists.newArrayList("ON", "OFF"));
			final List<String> list = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
			list.addAll(Arrays.asList("ON", "OFF"));
			return ChatUtils.getMatchingEntries(args[0], list);
		}
		if (args.length == 2 && sender.hasPermission("pvpmanager.admin"))
			return ChatUtils.getMatchingEntries(args[1], Lists.newArrayList("ON", "OFF"));

		return Collections.emptyList();
	}
}
