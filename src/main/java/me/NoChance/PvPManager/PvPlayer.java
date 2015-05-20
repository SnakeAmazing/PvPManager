package me.NoChance.PvPManager;

import java.util.HashMap;
import java.util.UUID;

import me.NoChance.PvPManager.Config.Messages;
import me.NoChance.PvPManager.Config.Variables;
import me.NoChance.PvPManager.Tasks.NewbieTask;
import me.NoChance.PvPManager.Utils.CombatUtils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PvPlayer {

	private final Player player;
	private boolean newbie;
	private boolean tagged;
	private boolean pvpState;
	private boolean pvpLogged;
	private boolean override;
	private long toggleTime;
	private long respawnTime;
	private long taggedTime;
	private final NewbieTask newbieTask;
	private final HashMap<String, Integer> victim = new HashMap<String, Integer>();
	private final PvPManager plugin;
	private TeamProfile teamProfile;

	public PvPlayer(final Player player, final PvPManager plugin) {
		this.player = player;
		this.plugin = plugin;
		this.newbieTask = new NewbieTask(this);
		if (Variables.isUseNameTag() || Variables.isToggleNametagsEnabled())
			teamProfile = new TeamProfile(this);
	}

	public final String getName() {
		return player.getName();
	}

	public final UUID getUUID() {
		return player.getUniqueId();
	}

	public final Player getPlayer() {
		return player;
	}

	public final String getWorldName() {
		return getPlayer().getWorld().getName();
	}

	public final long getToggleTime() {
		return this.toggleTime;
	}

	public final void message(final String message) {
		if (isOnline())
			getPlayer().sendMessage(message);
	}

	public final void togglePvP() {
		if (!CombatUtils.hasTimePassed(toggleTime, Variables.getToggleCooldown())) {
			final long secondsLeft = ((toggleTime + Variables.getToggleCooldown() * 1000) - System.currentTimeMillis()) / 1000;
			message(Messages.getErrorPvpCooldown().replace("%m", Long.toString(secondsLeft)));
			return;
		} else {
			toggleTime = System.currentTimeMillis();
			setPvP(!pvpState);
		}
	}

	public final boolean isNewbie() {
		return this.newbie;
	}

	public final boolean isOnline() {
		return getPlayer() != null;
	}

	public final boolean isInCombat() {
		return this.tagged;
	}

	public final boolean hasPvPEnabled() {
		return this.pvpState;
	}

	public final boolean hasPvPLogged() {
		return this.pvpLogged;
	}

	public final boolean hasOverride() {
		return this.override;
	}

	public final void disableFly() {
		final Player player = getPlayer();
		player.setFlying(false);
		player.setAllowFlight(false);
	}

	public final void setNewbie(final boolean newbie) {
		if (newbie) {
			message(Messages.getNewbieProtection().replace("%", Integer.toString(Variables.getNewbieProtectionTime())));
			newbieTask.runTaskLater(plugin, Variables.getNewbieProtectionTime() * 1200);
		} else {
			if (Bukkit.getServer().getScheduler().isCurrentlyRunning(newbieTask.getTaskId()))
				message(Messages.getNewbieProtectionEnd());
			else {
				newbieTask.cancel();
				message("§6[§8PvPManager§6] §eYou Removed Your PvP Protection! Be Careful");
			}
		}
		this.newbie = newbie;
	}

	public final void setTagged(final boolean attacker, final String tagger) {
		if (getPlayer().hasPermission("pvpmanager.nocombat"))
			return;

		taggedTime = System.currentTimeMillis();

		if (tagged)
			return;

		if (Variables.isUseNameTag())
			teamProfile.setInCombat();

		if (!Variables.isInCombatSilent())
			if (attacker)
				message(Messages.getTaggedAttacker().replace("%p", tagger));
			else
				message(Messages.getTaggedDefender().replace("%p", tagger));

		this.tagged = true;
		plugin.getPlayerHandler().tag(this);
	}

	public final void unTag() {
		if (isOnline()) {
			if (Variables.isUseNameTag())
				teamProfile.restoreTeam();

			if (!Variables.isInCombatSilent())
				message(Messages.getOutOfCombat());
		}

		this.tagged = false;
	}

	public final void setPvP(final boolean pvpState) {
		this.pvpState = pvpState;
		if (Variables.isToggleNametagsEnabled())
			teamProfile.setPvP(pvpState);
		if (!pvpState) {
			message(Messages.getPvpDisabled());
			if (Variables.isToggleBroadcast())
				Bukkit.broadcastMessage(Messages.getPvptoggleOffBroadcast().replace("%p", player.getName()));
		} else {
			message(Messages.getPvpEnabled());
			if (Variables.isToggleBroadcast())
				Bukkit.broadcastMessage(Messages.getPvptoggleOnBroadcast().replace("%p", player.getName()));
		}
	}

	public final void addVictim(final String victimName) {
		if (!victim.containsKey(victimName)) {
			victim.put(victimName, 1);
		} else if (victim.containsKey(victimName)) {
			int totalKills = victim.get(victimName);
			if (totalKills < Variables.getKillAbuseMaxKills()) {
				totalKills++;
				victim.put(victimName, totalKills);
			}
			if (totalKills >= Variables.getKillAbuseMaxKills()) {
				for (final String command : Variables.getKillAbuseCommands()) {
					Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("<player>", player.getName()));
				}
			}
		}
	}

	public final void clearVictims() {
		victim.clear();
	}

	public final void setPvpLogged(final boolean pvpLogged) {
		this.pvpLogged = pvpLogged;
	}

	public final boolean hasRespawnProtection() {
		if (respawnTime == 0)
			return false;
		if (CombatUtils.hasTimePassed(respawnTime, Variables.getRespawnProtection())) {
			respawnTime = 0;
			return false;
		}
		return true;
	}

	public final void setRespawnTime(final long respawnTime) {
		this.respawnTime = respawnTime;
	}

	public final boolean toggleOverride() {
		this.override = !override;
		return this.override;
	}

	public final long getTaggedTime() {
		return taggedTime;
	}

	public final void loadPvPState() {
		if (getPlayer().hasPermission("pvpmanager.nopvp"))
			this.pvpState = false;
		else if (!getPlayer().hasPlayedBefore()) {
			this.pvpState = Variables.isDefaultPvp();
			if (Variables.isNewbieProtectionEnabled())
				setNewbie(true);
		} else if (!plugin.getConfigM().getUserFile().getStringList("players").contains(player.getUniqueId().toString()))
			this.pvpState = true;
		if (Variables.isToggleNametagsEnabled())
			teamProfile.setPvP(this.pvpState);
	}
}
