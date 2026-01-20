package com.ascension.managers;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.ascension.Utils;
import com.ascension.animation.dash.Dash;
import com.ascension.animation.magma_bubble.MagmaSpike;
import com.ascension.animation.star_strike.GammaRay;
import com.ascension.orders.Earth;
import com.ascension.orders.Sky;

public class SpellRegistry {

    public static void dolphinsGrace(Player player) {
        ActiveSpell currSpell = SpellCooldownManager.addToActiveSpells(player,
                SpellCooldownManager.get("dolphins_grace"));
        PotionEffect currentEffect = player.getPotionEffect(PotionEffectType.DOLPHINS_GRACE);
        if (currentEffect == null) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    PotionEffectType.DOLPHINS_GRACE, 200, 0));
        } else if (currentEffect.getAmplifier() == 0) {
            player.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
        }
        currSpell.setInUse(false); // Allow cooldown to start
    }

    // Needs visuals
    public static void wave(Player player, int durationSecs, int radius, boolean strikeLightning) {
        ActiveSpell currSpell = SpellCooldownManager.addToActiveSpells(player, SpellCooldownManager.get("wave"));
        Location center = player.getLocation();

        // Debug logging
        if (strikeLightning) {
            player.sendMessage("§6[Debug] Wave: strikeLightning is TRUE, should strike lightning");
        } else {
            player.sendMessage("§6[Debug] Wave: strikeLightning is FALSE, no lightning");
        }

        SpellEffectManager.addEffect(new GenericEffect(player, (ticks) -> {
            if (ticks >= durationSecs * 20) {
                currSpell.setInUse(false); // Allow cooldown to start after the runnable finishes
                return false;
            }

            if (ticks % 20 == 0) {
                // remove actual cobweb blocks in the cubic radius
                int cx = center.getBlockX();
                int cy = center.getBlockY();
                int cz = center.getBlockZ();
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            Block b = player.getWorld().getBlockAt(cx + dx, cy + dy, cz + dz);
                            if (b.getType() == Material.COBWEB) {
                                b.setType(Material.AIR);
                            }
                        }
                    }
                }
            }

            return true;
        }));

        if (strikeLightning) {
            player.getWorld().playSound(center, org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
            for (LivingEntity target : Utils.withinDistance(player, center, radius)) {
                // Only affect hostile mobs; ignore passive mobs and the caster
                if (target.equals(player) || !(target instanceof org.bukkit.entity.Enemy)) {
                    continue;
                }
                // Visual-only lightning, then apply spell damage (20% max HP)
                target.getWorld().strikeLightningEffect(target.getLocation());
                Spell.spellDamage(target, 20.0, player);
            }
        }
    }

    // Needs visuals
    public static void drown(Player player, int durationSecs, int radius) {
        ActiveSpell currSpell = SpellCooldownManager.addToActiveSpells(player, SpellCooldownManager.get("drown"));
        Location center = player.getLocation();
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AscensionSMP");

        SpellEffectManager.addEffect(new GenericEffect(player, (ticks) -> {
            if (ticks >= durationSecs * 20) {
                currSpell.setInUse(false); // Allow cooldown to start after the runnable finishes
                return false;
            }
            // Apply a periodic drowning-like effect without placing blocks.
            // Every second, drain air and deal 5% max HP as spell damage.

            if (ticks % 20 == 0) {
                for (LivingEntity target : Utils.withinDistance(player, center, radius)) {
                    if (target == null || target.equals(player)) {
                        continue;
                    }

                    // Set air to 0 for players (draining effect)
                    if (target instanceof Player victim) {
                        victim.setRemainingAir(0);
                    }

                    // Reduce knockback by restoring velocity after damage.
                    Vector before = target.getVelocity();
                    Spell.spellDamage(target, 5.0, player);
                    try {
                        target.setVelocity(before);
                    } catch (Throwable ignored) {
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            target.setVelocity(before);
                        } catch (Throwable ignored) {
                        }
                    });
                }
            }
            return true;
        }));
    }

    // Not really a spell - uses simple cooldown tracking
    public static void double_jump(Player player, int jumpHeight, boolean slam) {
        // Check simple cooldown
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastUse = Sky.getDoubleJumpCooldowns().get(playerId);

        final long cooldownMs = 8000L;
        if (lastUse != null) {
            long elapsed = currentTime - lastUse;
            if (elapsed < cooldownMs) {
                long remainingMs = cooldownMs - elapsed;
                long remainingSeconds = (long) Math.ceil(remainingMs / 1000.0);
                player.sendMessage("§cDouble Jump is on cooldown! §7(" + remainingSeconds + "s left)");
                return; // Still on cooldown
            }
        }

        // Set cooldown
        Sky.putDoubleJumpCooldown(playerId, currentTime);

        Vector current = player.getVelocity().clone();
        Vector forward = player.getLocation().getDirection().clone();
        forward.setY(0);
        if (forward.lengthSquared() > 0.2) {
            System.out
                    .println("Forward vector: " + forward + ", LengthSquared: " + forward.lengthSquared());
            forward.normalize().multiply(0.3);
            current.add(forward);
        }
        // Solve initial Y velocity (verticalBoost) for Minecraft-like per-tick physics:
        // y += v; v = (v - 0.08) * 0.98
        java.util.function.DoubleUnaryOperator maxHeightForV0 = (v0) -> {
            double y = 0.0;
            double v = v0;
            double maxY = 0.0;
            for (int i = 0; i < 200; i++) {
                y += v;
                if (y > maxY)
                    maxY = y;
                v = (v - 0.08) * 0.98;
                if (v <= 0.0 && y < maxY)
                    break; // past apex and descending
            }
            return maxY;
        };

        double lo = 0.0;
        double hi = 1.0;
        while (maxHeightForV0.applyAsDouble(hi) < jumpHeight && hi < 5.0) {
            hi *= 1.35;
        }
        for (int i = 0; i < 22; i++) {
            double mid = (lo + hi) * 0.5;
            if (maxHeightForV0.applyAsDouble(mid) >= jumpHeight)
                hi = mid;
            else
                lo = mid;
        }

        double verticalBoost = hi;
        if (slam) {
            // We use the Manager to wait for the landing
            SpellEffectManager.addEffect(new GenericEffect(player, (ticks) -> {

                // 1. If they land, trigger the slam and STOP the effect
                if (Utils.onGround(player)) {
                    Location impact = player.getLocation();
                    World world = impact.getWorld();
                    if (world == null)
                        return false;
                    player.setFallDistance(0f);
                    world.spawnParticle(Particle.POOF, impact, 40, 0.8, 0.2, 0.8, 0.05);

                    boolean hitSomething = false;
                    for (Entity entity : world.getNearbyEntities(impact, 2.0, 2.0, 2.0)) {
                        if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                            continue;
                        }
                        Spell.spellDamage(living, 10.0, player);
                        hitSomething = true;
                    }

                    if (hitSomething)
                        world.playSound(impact, Sound.ITEM_MACE_SMASH_GROUND, 1.0f, 1.1f);

                    return false;
                }

                // Safety Timeout: If they don't land within 3 seconds, stop looking
                if (ticks >= 60) {
                    return false;
                }

                // Keep checking every tick
                return true;
            }));
        }

        current.setY(Math.max(current.getY(), verticalBoost));
        player.setVelocity(current);
        Dash.spawnDoubleJumpBurst(player, slam);
        player.setFlying(false);
    }

    public static void dash(Player player, boolean dashDamage, int distance) {
        World world = player.getWorld(); // Define and initialize the world variable
        // Create an ActiveSpell entry for cooldown tracking
        ActiveSpell as = SpellCooldownManager.addToActiveSpells(player,
                SpellCooldownManager.get("dash"));

        Vector dir = player.getLocation().getDirection().clone().normalize();
        // Convert desired travel distance (blocks) into an initial horizontal speed.
        // Approximation: player horizontal velocity decays by ~0.91 each tick while
        // airborne.
        // Distance over N ticks ~= v0 * (1 - drag^N) / (1 - drag)
        final double drag = 0.91;
        final int dashTicks = 10; // how long the dash should "meaningfully" last
        double desiredHorizontalSpeed = (distance * (1.0 - drag)) / (1.0 - Math.pow(drag, dashTicks));

        // Apply the computed speed to the horizontal components while keeping the
        // capped Y.
        Vector horiz = new Vector(dir.getX(), 0.0, dir.getZ());
        if (horiz.lengthSquared() > 1.0e-6) {
            horiz.normalize().multiply(desiredHorizontalSpeed);
            dir.setX(horiz.getX());
            dir.setZ(horiz.getZ());
        }

        // Apply the velocity impulse
        player.setVelocity(dir);
        Dash.emitDashCone(player, player.getVelocity().clone(), dashDamage ? 20 : 12, dashDamage ? 1.4 : 1.0);
        world.playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.5f,
                dashDamage ? 1.0f : 1.35f);

        if (dashDamage) {
            SpellEffectManager.addEffect(new GenericEffect(player, (ticks) -> {
                if (Utils.onGround(player) || ticks >= 60) {
                    as.setInUse(false);
                    return false;
                }

                player.getWorld().getNearbyEntities(player.getBoundingBox().expand(0.5))
                        .stream()
                        .filter(entity -> entity instanceof LivingEntity && !entity.equals(player))
                        .forEach(entity -> {
                            LivingEntity living = (LivingEntity) entity;
                            Spell.spellDamage(living, 20, player);
                        });
                return true;
            }));
        } else
            as.setInUse(false);
    }

    public static void starStrike(Player player, boolean augmented) {
        ActiveSpell as = SpellCooldownManager.addToActiveSpells(player,
                SpellCooldownManager.get("star_strike"));
        Block tb = player.getTargetBlockExact(augmented ? 60 : 30);
        Location strikePoint;
        Location particleCenter;
        if (tb != null) {
            strikePoint = tb.getLocation().toCenterLocation();
            particleCenter = strikePoint.clone().add(0.0, -0.5, 0.0);
        } else {
            Vector look = player.getLocation().getDirection().clone().normalize();
            strikePoint = player.getEyeLocation().clone().add(look.multiply(30.0));
            particleCenter = strikePoint.clone();
        }

        World world = player.getWorld();
        final int growTicks = 10;
        final int holdTicks = 10;
        final int fadeTicks = 12;

        // Pre-impact rumble: play immediately on cast (lower pitch thunder).
        world.playSound(strikePoint, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.9f, 0.55f);

        GammaRay.strike(strikePoint.clone(), 1.5, growTicks, holdTicks, fadeTicks,
                augmented ? Color.BLACK : Color.WHITE);

        final int durationTicks = 40; // total duration
        // percent-based damage used via Spell.spellDamage
        final double launchVY = 0.7;

        SpellEffectManager.addEffect(new GenericEffect(player, (ticks) -> {
            if (ticks >= durationTicks) {
                as.setInUse(false);
                return false;
            }

            // visual particles for the 2x2 area
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    org.bukkit.Location p = particleCenter.clone().add(dx - 0.5, 0.5, dz - 0.5);
                    world.spawnParticle(org.bukkit.Particle.END_ROD, p, 10, 0.2, 0.5, 0.2, 0.01);
                    world.spawnParticle(org.bukkit.Particle.FLASH, p, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }

            if (ticks < growTicks + holdTicks)
                return true; // only deal damage after the growth phase

            for (Entity e : world.getNearbyEntities(strikePoint, 2.0, 3.0, 2.0)) {
                // protect the caster from their own strike
                if (e.getUniqueId().equals(player.getUniqueId()))
                    continue;
                if (!(e instanceof LivingEntity))
                    continue;
                LivingEntity le = (LivingEntity) e;
                Spell.spellDamage(le, 25.0, player);

                // launch upwards
                Vector v = le.getVelocity();
                v.setY(launchVY);
                le.setVelocity(v);

                le.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, true, true, true));
            }

            return true;
        }));
    }

    public static void magmaBubble(Player player, int radius, float damagePercent, boolean launch) {
        Player p = player;
        Location center = p.getLocation();

        // Allow a 1-block tolerance (e.g., slightly above the ground)
        Block blockBelow1 = center.getBlock().getRelative(0, -1, 0);
        Block blockBelow2 = center.getBlock().getRelative(0, -2, 0);

        boolean standingOnSolid = (blockBelow1 != null && !blockBelow1.isPassable())
                || (blockBelow2 != null && !blockBelow2.isPassable());

        boolean submergedInLava = center.getBlock().getType() == Material.LAVA
                || (blockBelow1 != null && blockBelow1.getType() == Material.LAVA);

        if (!standingOnSolid && !submergedInLava) {
            player.sendMessage("You need solid ground or lava to unleash Magma Bubble.");
            return;
        }

        ActiveSpell as = SpellCooldownManager.addToActiveSpells(p, SpellCooldownManager.get("magma_bubble"));
        float heightScale = launch ? 1.0f : 0.6f;
        MagmaSpike.spawnMagmaSpikes(center, heightScale);

        if (launch) {
            Vector launchVector = p.getVelocity().clone();
            launchVector.setY(Math.max(launchVector.getY(), 1.2));
            p.setVelocity(launchVector);
            p.setFallDistance(0f);
        }

        for (org.bukkit.entity.Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (e instanceof LivingEntity && !e.equals(p)) {
                Spell.spellDamage(player, damagePercent, p);
            }
        }

        center.getWorld().playSound(center, Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 1.0f, 0.5f);
        center.getWorld().playSound(center, Sound.AMBIENT_UNDERWATER_LOOP_ADDITIONS_RARE, 1.0f, 0.625f);
        center.getWorld().playSound(center, Sound.ITEM_MACE_SMASH_GROUND, 1.0f, launch ? 0.5f : 0.65f);
        as.setInUse(false);
    }

    // Actual supermine handled in EARTH event listeners
    public static void toggleSupermine(Player player) {
        ActiveSpell as = SpellCooldownManager.addToActiveSpells(player, SpellCooldownManager.get("supermine"));
        Earth.toggleSupermine(player);
        as.setInUse(false);
    }
}
