package me.NoChance.PvPManager.Listeners;

import java.util.concurrent.TimeUnit;

import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockIgniteEvent.IgniteCause;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.weather.LightningStrikeEvent.Cause;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import me.NoChance.PvPManager.PvPlayer;
import me.NoChance.PvPManager.Dependencies.Hook;
import me.NoChance.PvPManager.Dependencies.WorldGuardHook;
import me.NoChance.PvPManager.Managers.PlayerHandler;
import me.NoChance.PvPManager.Player.CancelResult;
import me.NoChance.PvPManager.Settings.Messages;
import me.NoChance.PvPManager.Settings.Settings;
import me.NoChance.PvPManager.Utils.CombatUtils;

public class EntityListener implements Listener {

	private final PlayerHandler ph;
	private final WorldGuardHook wg;
	private final Cache<LightningStrike, Location> lightningCache = CacheBuilder.newBuilder().expireAfterWrite(2, TimeUnit.SECONDS).build();

	public EntityListener(final PlayerHandler ph) {
		this.ph = ph;
		this.wg = (WorldGuardHook) ph.getPlugin().getDependencyManager().getDependency(Hook.WORLDGUARD);
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public final void onPlayerDamage(final EntityDamageByEntityEvent event) {
		if (CombatUtils.isWorldExcluded(event.getEntity().getWorld().getName()))
			return;
		if (!CombatUtils.isPvP(event)) {
			if (!(event.getEntity() instanceof Player))
				return;

			final PvPlayer attacked = ph.get((Player) event.getEntity());
			if (attacked.isNewbie() && Settings.isNewbieGodMode()) {
				event.setCancelled(true);
			} else if (event.getDamager() instanceof LightningStrike) {
				final LightningStrike lightning = (LightningStrike) event.getDamager();
				if (!lightningCache.asMap().containsKey(lightning))
					return;
				if (!attacked.hasPvPEnabled() || attacked.isNewbie() || attacked.hasRespawnProtection()) {
					event.setCancelled(true);
				}
			}
			return;
		}

		final Player attacker = getAttacker(event.getDamager());
		final Player attacked = (Player) event.getEntity();
		final CancelResult result = ph.tryCancel(attacker, attacked);

		if (result != CancelResult.FAIL && result != CancelResult.FAIL_OVERRIDE) {
			event.setCancelled(true);
			Messages.messageProtection(result, attacker, attacked);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public final void onPlayerDamageOverride(final EntityDamageByEntityEvent event) {
		if (!CombatUtils.isPvP(event) || CombatUtils.isWorldExcluded(event.getEntity().getWorld().getName()) || !event.isCancelled())
			return;

		if (ph.tryCancel(getAttacker(event.getDamager()), (Player) event.getEntity()).equals(CancelResult.FAIL_OVERRIDE)) {
			event.setCancelled(false);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public final void onPlayerDamageMonitor(final EntityDamageByEntityEvent event) {
		if (!CombatUtils.isPvP(event) || CombatUtils.isWorldExcluded(event.getEntity().getWorld().getName()))
			return;
		final Player attacker = getAttacker(event.getDamager());
		final Player attacked = (Player) event.getEntity();

		onDamageActions(attacker, attacked);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public final void onEntityCombust(final EntityCombustByEntityEvent event) {
		if (CombatUtils.isWorldExcluded(event.getEntity().getWorld().getName()))
			return;
		if (!CombatUtils.isPvP(event)) {
			if (event.getEntity() instanceof Player && ph.get((Player) event.getEntity()).isNewbie() && Settings.isNewbieGodMode()) {
				event.setCancelled(true);
			}
			return;
		}

		final Player attacker = getAttacker(event.getCombuster());
		final Player attacked = (Player) event.getEntity();

		if (!ph.canAttack(attacker, attacked)) {
			event.setCancelled(true);
		}
	}

	public void onDamageActions(final Player attacker, final Player defender) {
		final PvPlayer pvpAttacker = ph.get(attacker);
		final PvPlayer pvpDefender = ph.get(defender);

		if (Settings.isPvpBlood()) {
			defender.getWorld().playEffect(defender.getLocation(), Effect.STEP_SOUND, Material.REDSTONE_BLOCK);
		}
		if (!attacker.hasPermission("pvpmanager.nodisable")) {
			if (Settings.isDisableFly()) {
				if (CombatUtils.canFly(attacker)) {
					pvpAttacker.disableFly();
				}
				if (!defender.hasPermission("pvpmanager.nodisable") && CombatUtils.canFly(defender)) {
					pvpDefender.disableFly();
				}
			}
			if (Settings.isDisableGamemode() && !attacker.getGameMode().equals(GameMode.SURVIVAL)) {
				attacker.setGameMode(GameMode.SURVIVAL);
			}
			if (Settings.isDisableDisguise()) {
				ph.getPlugin().getDependencyManager().disableDisguise(attacker);
			}
			if (Settings.isDisableInvisibility() && attacker.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
				attacker.removePotionEffect(PotionEffectType.INVISIBILITY);
			}
			if (Settings.isDisableGodMode()) {
				ph.getPlugin().getDependencyManager().disableGodMode(attacker);
			}
		}
		if (Settings.isInCombatEnabled()) {
			if (Settings.borderHoppingVulnerable() && wg != null && !Settings.borderHoppingResetCombatTag()) {
				if (wg.hasDenyPvPFlag(attacker) && wg.hasDenyPvPFlag(defender))
					return;
			}
			pvpAttacker.setTagged(true, pvpDefender);
			pvpDefender.setTagged(false, pvpAttacker);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public final void onPotionSplash(final PotionSplashEvent event) {
		if (CombatUtils.isWorldExcluded(event.getEntity().getWorld().getName()))
			return;

		final ThrownPotion potion = event.getPotion();
		if (event.getAffectedEntities().isEmpty() || potion.getEffects().isEmpty() || !(potion.getShooter() instanceof Player))
			return;

		for (final PotionEffect effect : potion.getEffects()) {
			if (!CombatUtils.isHarmfulPotion(effect.getType()))
				return;
		}

		final Player player = (Player) potion.getShooter();
		for (final LivingEntity e : event.getAffectedEntities()) {
			if (e.getType() != EntityType.PLAYER || e.equals(player)) {
				continue;
			}
			final Player attacked = (Player) e;
			final CancelResult result = ph.tryCancel(player, attacked);

			if (result != CancelResult.FAIL && result != CancelResult.FAIL_OVERRIDE) {
				event.setIntensity(attacked, 0);
				Messages.messageProtection(result, player, attacked);
			} else {
				onDamageActions(player, attacked);
			}
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onLightningStrike(final LightningStrikeEvent event) {
		if (CombatUtils.isWorldExcluded(event.getLightning().getWorld().getName()))
			return;
		if (!CombatUtils.isVersionAtLeast(Settings.getMinecraftVersion(), "1.13.1"))
			return;
		if (event.getCause() != Cause.TRIDENT)
			return;

		lightningCache.put(event.getLightning(), event.getLightning().getLocation());
	}

	@EventHandler(ignoreCancelled = true)
	public void onBlockIgnite(final BlockIgniteEvent event) {
		if (event.getCause() != IgniteCause.LIGHTNING)
			return;
		if (CombatUtils.isWorldExcluded(event.getBlock().getWorld().getName()))
			return;

		final Entity ignitingEntity = event.getIgnitingEntity();
		if (ignitingEntity instanceof LightningStrike && lightningCache.asMap().containsKey(ignitingEntity)) {
			final LightningStrike lightningStrike = (LightningStrike) ignitingEntity;
			for (final Entity entity : lightningStrike.getNearbyEntities(2, 2, 2)) {
				if (entity instanceof Player) {
					final PvPlayer attacked = ph.get((Player) entity);
					if (!attacked.hasPvPEnabled() || attacked.isNewbie() || attacked.hasRespawnProtection()) {
						event.setCancelled(true);
						return;
					}
				}
			}
		}
	}

	private Player getAttacker(final Entity damager) {
		if (damager instanceof Player)
			return (Player) damager;
		if (damager instanceof Projectile)
			return (Player) ((Projectile) damager).getShooter();
		return (Player) ((AreaEffectCloud) damager).getSource();
	}

}
