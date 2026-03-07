package freq.ascension.registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleUnaryOperator;

import org.joml.Vector3f;

import freq.ascension.Utils;
import freq.ascension.animation.Dash;
import freq.ascension.animation.Drown;
import freq.ascension.animation.DragonCurve;
import freq.ascension.animation.MagmaBubble;
import freq.ascension.animation.StarStrike;
import freq.ascension.animation.Thorns;
import freq.ascension.managers.ActiveSpell;
import freq.ascension.managers.AscensionData;
import freq.ascension.managers.Spell;
import freq.ascension.managers.SpellCooldownManager;
import freq.ascension.managers.SpellStats;
import freq.ascension.orders.*;
import freq.ascension.Ascension;
import freq.ascension.api.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.HappyGhast;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class SpellRegistry {
    // {id: spell}
    public static final Map<String, Spell> SPELLS = new HashMap<>();
    // {order: {type: [ spell1, spell2 ]}}
    private static final Map<Order, Map<String, List<Spell>>> BY_ORDER_AND_TYPE = new HashMap<>();
    // Rate-limit the "You need solid ground" message to once per 5 seconds per
    // player
    private static final java.util.concurrent.ConcurrentHashMap<UUID, Long> LAST_GROUND_MSG = new java.util.concurrent.ConcurrentHashMap<>();

    public static void register(Spell spell) {
        SPELLS.put(spell.getId(), spell);
        BY_ORDER_AND_TYPE
                .computeIfAbsent(spell.getOrder(), k -> new HashMap<>())
                .computeIfAbsent(spell.getType(), k -> new ArrayList<>())
                .add(spell);
    }

    public static List<Spell> getEquippableSpells(ServerPlayer player) {
        List<Spell> equippable = new ArrayList<>();
        AscensionData data = (AscensionData) player;

        addSpellsIfPresent(equippable, data.getPassive(), "passive");
        addSpellsIfPresent(equippable, data.getUtility(), "utility");
        addSpellsIfPresent(equippable, data.getCombat(), "combat");

        return equippable;
    }

    private static void addSpellsIfPresent(List<Spell> targetList, Order order, String type) {
        if (order == null)
            return;
        Map<String, List<Spell>> orderSpells = BY_ORDER_AND_TYPE.get(order);
        if (orderSpells == null)
            return;
        List<Spell> spells = orderSpells.get(type);
        if (spells != null) {
            targetList.addAll(spells);
        }
    }

    /*
     * EARTH
     */

    public static void magmaBubble(ServerPlayer p, int radius, float damagePercent, boolean launch) {
        BlockPos center = p.getOnPos();

        // Allow a 1-block tolerance (e.g., slightly above the ground)
        Block blockBelow1 = p.level().getBlockState(center.below()).getBlock();
        Block blockBelow2 = p.level().getBlockState(center.below(2)).getBlock();

        boolean standingOnSolid = !blockBelow1.defaultBlockState().isAir() || !blockBelow2.defaultBlockState().isAir();

        boolean submergedInLava = p.level().getFluidState(center).is(net.minecraft.tags.FluidTags.LAVA)
                || (blockBelow1 != null
                        && p.level().getFluidState(center.below()).is(net.minecraft.tags.FluidTags.LAVA));

        if (!standingOnSolid && !submergedInLava) {
            long now = System.currentTimeMillis();
            Long lastSent = LAST_GROUND_MSG.get(p.getUUID());
            if (lastSent == null || now - lastSent >= 5000L) {
                LAST_GROUND_MSG.put(p.getUUID(), now);
                p.sendSystemMessage(Component.literal("You need solid ground or lava to unleash Magma Bubble."));
            }
            return;
        }

        ActiveSpell as = SpellCooldownManager.addToActiveSpells(p, SpellCooldownManager.get("magma_bubble"));

        MagmaBubble.spawnMagmaSpikes(p, 12, radius, launch ? 2.0f : 1.5f);

        if (launch) {
            Vec3 velocity = p.getDeltaMovement();
            p.setDeltaMovement(velocity.x, Math.max(velocity.y, 1.2), velocity.z);
            p.hasImpulse = true;
            p.setIgnoreFallDamageFromCurrentImpulse(true);
        }

        AABB searchBox = new AABB(center).inflate(radius);
        for (LivingEntity target : p.level().getEntitiesOfClass(LivingEntity.class, searchBox)) {
            if (target != p) {
                Utils.spellDmg(target, p, damagePercent);
            }
        }

        Level level = p.level();

        level.playSound(null, center, SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), SoundSource.PLAYERS, 1.0f,
                0.5f);
        level.playSound(null, center, SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_RARE,
                SoundSource.PLAYERS,
                1.0f, 0.625f);
        level.playSound(null, center, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.0f,
                launch ? 0.5f : 0.65f);

        as.setInUse(false);
    }

    // Actual supermine handled in EARTH event listeners
    public static void toggleSupermine(ServerPlayer player) {
        ActiveSpell as = SpellCooldownManager.addToActiveSpells(player, SpellCooldownManager.get("supermine"));
        Earth.toggleSupermine(player);
        as.setInUse(false);
    }

    /*
     * SKY
     */

    // Not really a spell; uses simple cooldown tracking
    public static void double_jump(ServerPlayer player, int jumpHeight, boolean slam) {
        UUID playerId = player.getUUID();
        long currentTime = System.currentTimeMillis();
        Long lastUse = Sky.getDoubleJumpCooldowns().get(playerId);
        final long cooldownMs = 8000L;

        if (lastUse != null) {
            long elapsed = currentTime - lastUse;
            if (elapsed < cooldownMs) {
                long remainingMs = cooldownMs - elapsed;
                long remainingSeconds = (long) Math.ceil(remainingMs / 1000.0);
                player.sendSystemMessage(
                        Component.literal("§cDouble Jump is on cooldown! §7(" + remainingSeconds + "s left)"));
                return; // Still on cooldown
            }
        }

        Sky.putDoubleJumpCooldown(playerId, currentTime);

        Vec3 current = player.getDeltaMovement();
        Vec3 forward = player.getLookAngle();
        // Flatten Y for horizontal boost direction
        forward = new Vec3(forward.x, 0, forward.z);

        if (forward.lengthSqr() > 0.2) {
            forward = forward.normalize().scale(0.3);
            current = current.add(forward);
        }

        // Solve initial Y velocity (verticalBoost) for Minecraft-like per-tick physics:
        // y += v; v = (v - 0.08) * 0.98
        DoubleUnaryOperator maxHeightForV0 = (v0) -> {
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

        player.setDeltaMovement(current.x, verticalBoost, current.z);
        player.hasImpulse = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        player.setIgnoreFallDamageFromCurrentImpulse(true);

        Dash.spawnDashCone(player, player.getDeltaMovement().toVector3f(), 10, 0.5f, 1.5f,
                1.0f);

        if (slam) {
            Ascension.scheduler.schedule(new RepeatedTask(1, 60, (task) -> {
                BlockPos center = player.getOnPos();
                Block blockBelow1 = player.level().getBlockState(center.below()).getBlock();
                boolean onGround = !blockBelow1.defaultBlockState().isAir();
                Level level = player.level();

                if (onGround) {
                    player.setIgnoreFallDamageFromCurrentImpulse(true);
                    level.addParticle(ParticleTypes.POOF, center.getX(), center.getY(), center.getZ(), 0.0, 0.0, 0.0);

                    AABB searchBox = new AABB(center).inflate(2);
                    for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, searchBox)) {
                        if (target != player) {
                            Utils.spellDmg(target, player, 20);
                        }
                    }
                    level.playSound(null, center, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.0f, 1.0f);
                    task.cancel();
                }
            }));
        }
    }

    public static void dash(ServerPlayer player, boolean dashDamage, int distance) {
        Level level = player.level();
        ActiveSpell as = SpellCooldownManager.addToActiveSpells(player,
                SpellCooldownManager.get("dash"));

        // Current motion and look direction (flattened)
        Vec3 current = player.getDeltaMovement();
        Vec3 forward = player.getLookAngle();
        forward = new Vec3(forward.x, 0.0, forward.z);

        // Compute desired horizontal speed based on distance and drag (approximation)
        final double drag = 0.91;
        final int dashTicks = 12;
        double desiredHorizontalSpeed = (distance * (1.0 - drag)) / (1.0 - Math.pow(drag, dashTicks));

        Vec3 horiz = new Vec3(current.x, 0.0, current.z);

        if (forward.lengthSqr() > 0.2) {
            Vec3 boost = forward.normalize().scale(desiredHorizontalSpeed);
            horiz = horiz.add(boost);
        } else {
            // If no meaningful forward input, apply boost in player's facing direction
            // anyway
            Vec3 lookFlat = player.getLookAngle();
            lookFlat = new Vec3(lookFlat.x, 0.0, lookFlat.z);
            if (lookFlat.lengthSqr() > 1.0e-6) {
                Vec3 boost = lookFlat.normalize().scale(desiredHorizontalSpeed);
                horiz = horiz.add(boost);
            }
        }

        // Preserve vertical velocity
        Vec3 newVel = new Vec3(horiz.x, current.y, horiz.z);
        player.setDeltaMovement(newVel);
        player.hasImpulse = true;
        player.setIgnoreFallDamageFromCurrentImpulse(true);
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        Dash.spawnDashCone(player, newVel.toVector3f(), 15, 1.5f, 2.5f,
                2.0f);

        if (dashDamage) {
            Ascension.scheduler.schedule(new RepeatedTask(1, 60, (task) -> {
                if (player.onGround()) {
                    as.setInUse(false);
                    task.cancel();
                    return;
                }

                AABB box = player.getBoundingBox().inflate(0.5);
                level.getEntitiesOfClass(LivingEntity.class, box)
                        .stream()
                        .filter(entity -> !entity.equals(player))
                        .forEach(entity -> {
                            Utils.spellDmg(entity, player, 20);
                        });
            }));
        } else {
            as.setInUse(false);
        }
    }

    public static void starStrike(ServerPlayer player, boolean augmented) {
        ActiveSpell as = SpellCooldownManager.addToActiveSpells(player,
                SpellCooldownManager.get("star_strike"));

        Level level = player.level();
        double range = augmented ? 60.0 : 30.0;

        // Perform raycast to find target or fallback to max range
        Vec3 eyePos = player.getEyePosition();
        Vec3 viewVec = player.getLookAngle();
        Vec3 endVec = eyePos.add(viewVec.scale(range));
        BlockHitResult blockHit = level.clip(new ClipContext(
                eyePos, endVec,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player));

        double blockDist = blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                ? Double.MAX_VALUE
                : blockHit.getLocation().distanceTo(eyePos);

        // check for entity hits along the ray and pick closest hit (entity vs block)
        AABB rayBox = new AABB(eyePos, endVec).inflate(1.0);
        LivingEntity hitEntity = null;
        Vec3 hitEntityVec = null;
        double entityDist = Double.MAX_VALUE;

        for (LivingEntity ent : level.getEntitiesOfClass(LivingEntity.class, rayBox,
                e -> !e.isSpectator() && e != player)) {
            AABB entBB = ent.getBoundingBox().inflate(0.3);
            java.util.Optional<Vec3> opt = entBB.clip(eyePos, endVec);
            if (opt.isPresent()) {
                Vec3 v = opt.get();
                double d = eyePos.distanceTo(v);
                if (d < entityDist) {
                    entityDist = d;
                    hitEntity = ent;
                    hitEntityVec = v;
                }
            }
        }

        net.minecraft.world.phys.HitResult hitResult;
        if (hitEntity != null && entityDist < blockDist) {
            hitResult = new net.minecraft.world.phys.EntityHitResult(hitEntity, hitEntityVec);
        } else {
            hitResult = blockHit;
        }

        Vec3 strikePoint;
        if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY
                && hitResult instanceof net.minecraft.world.phys.EntityHitResult) {
            net.minecraft.world.entity.Entity ent = ((net.minecraft.world.phys.EntityHitResult) hitResult).getEntity();
            AABB bb = ent.getBoundingBox();
            strikePoint = new Vec3(ent.getX(), bb.minY, ent.getZ()); // bottom-center of entity
        } else if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            strikePoint = endVec;
        } else {
            strikePoint = hitResult.getLocation();
        }

        {
            int hex = augmented ? 0x000000 : 0xFFFFFF;
            float r = ((hex >> 16) & 0xFF) / 255.0f;
            float g = ((hex >> 8) & 0xFF) / 255.0f;
            float b = (hex & 0xFF) / 255.0f;
            Vector3f colorVec = new Vector3f(r, g, b);
            StarStrike.spawnGammaRay(player, strikePoint.toVector3f(), 1.5f, colorVec);
        }

        int growTicks = 5;
        int holdTicks = 20;

        Ascension.scheduler.schedule(new RepeatedTask(1, growTicks + holdTicks,
                (task) -> {
                    long ticks = task.getTick();

                    if (ticks >= growTicks + holdTicks) {
                        as.setInUse(false);
                        task.cancel();
                        return;
                    }

                    // Only deal damage after the growth phase
                    if (ticks < growTicks) {
                        return;
                    }

                    BlockPos center = new BlockPos(
                            (int) Math.floor(strikePoint.x),
                            (int) Math.floor(strikePoint.y),
                            (int) Math.floor(strikePoint.z));
                    AABB box = new AABB(center).inflate(2.0, 3.0, 2.0);
                    for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
                        if (target == player)
                            continue;

                        Utils.spellDmg(target, player, 25.0f);

                        // Launch upwards
                        Vec3 currentVel = target.getDeltaMovement();
                        target.setDeltaMovement(currentVel.x, 0.7, currentVel.z);
                        target.hasImpulse = true;

                        // Apply Darkness effect
                        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.DARKNESS, 40, 0, true, true, true));
                    }
                }));
    }

    /*
     * OCEAN
     */

    public static void dolphinsGrace(ServerPlayer player) {
        ActiveSpell currSpell = SpellCooldownManager.addToActiveSpells(player,
                SpellCooldownManager.get("dolphins_grace"));
        net.minecraft.world.effect.MobEffectInstance currentEffect = player
                .getEffect(net.minecraft.world.effect.MobEffects.DOLPHINS_GRACE);
        if (currentEffect == null) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DOLPHINS_GRACE, 200, 0, true, true, true));
        } else if (currentEffect.getAmplifier() == 0) {
            player.removeEffect(net.minecraft.world.effect.MobEffects.DOLPHINS_GRACE);
        }
        currSpell.setInUse(false); // Allow cooldown to start
    }

    public static void drown(ServerPlayer player, int durationSecs, int radius) {
        ActiveSpell as = SpellCooldownManager.addToActiveSpells(player, SpellCooldownManager.get("drown"));
        BlockPos center = player.getOnPos();

        Drown.drownSphere(player, radius, durationSecs * 20);

        Ascension.scheduler.schedule(new RepeatedTask(0, durationSecs * 20, (task) -> {
            long ticks = task.getTick();
            if (ticks >= durationSecs * 20) {
                as.setInUse(false);
                task.cancel();
                return;
            }

            // Set air meter to 0 every second
            if (ticks % 20 == 0) {
                AABB box = new AABB(center).inflate(radius, radius, radius);
                for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
                    if (target == player)
                        continue;

                    if (target instanceof ServerPlayer victim) {
                        victim.setAirSupply(0);
                    }
                    // Apply normal drowning damage (2.0 = 1 heart, same as vanilla drowning)
                    // Use drown damage source to avoid triggering Ocean passive crits
                    Vec3 before = target.getDeltaMovement();
                    target.hurt(target.damageSources().drown(), 2.0f);
                    // Ignore knockback resistance by resetting velocity
                    try {
                        target.setDeltaMovement(before);
                    } catch (Throwable ignored) {
                    }

                }
            }
        }));
    }

    public static void molecularFlux(ServerPlayer player, int range, int durationSecs) {
        ActiveSpell as = SpellCooldownManager.addToActiveSpells(player,
                SpellCooldownManager.get("molecular_flux"));
        Level level = player.level();

        final Set<BlockPos> transformedPositions = new java.util.HashSet<>();

        RepeatedTask fluxTask = new RepeatedTask(0, durationSecs * 20, (task) -> {
            Vec3 eye = player.getEyePosition();
            Vec3 dir = player.getLookAngle().normalize();
            Vec3 end = eye.add(dir.scale(range));

            // Draw particle trail along ray and check blocks along it
            double step = 0.5;
            double total = eye.distanceTo(end);
            int steps = Math.max(1, (int) Math.ceil(total / step));
            boolean transformedThisTick = false;

            for (int i = 0; i <= steps; i++) {
                double t = (i * step);
                if (t > total)
                    t = total;
                Vec3 sample = eye.add(dir.scale(t));

                if (transformedThisTick)
                    continue; // still draw trail but don't transform more than once per tick

                BlockPos pos = new BlockPos((int) Math.floor(sample.x), (int) Math.floor(sample.y),
                        (int) Math.floor(sample.z));

                // skip if this position was already transformed during this activation
                if (transformedPositions.contains(pos))
                    continue;

                Block block = level.getBlockState(pos).getBlock();

                // skip air
                if (block == null || block == Blocks.AIR)
                    continue;

                // WATER SOURCE -> FROSTED_ICE (but only if source does not intersect caster
                // hitbox)
                if (level.getFluidState(pos).is(FluidTags.WATER)
                        && level.getFluidState(pos).isSource()) {
                    AABB blockAABB = new AABB(pos);
                    if (!player.getBoundingBox().intersects(blockAABB)) {
                        level.setBlock(pos, Blocks.FROSTED_ICE.defaultBlockState(), 3);
                        level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0f, 1.4f);
                        ((ServerLevel) level).sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5,
                                pos.getZ() + 0.5,
                                3, 0.0, 0.05, 0.0, 0.0);
                        transformedPositions.add(pos);
                        transformedThisTick = true;
                        continue;
                    } else {
                        // don't transform water that intersects caster
                        continue;
                    }
                }

                // ICE variants -> WATER SOURCE (OVERWORLD and END only). Blue ice unaffected.
                if (block == Blocks.ICE
                        || block == Blocks.PACKED_ICE
                        || block == Blocks.FROSTED_ICE) {
                    if (block != Blocks.BLUE_ICE) {
                        if (level.dimension() == Level.OVERWORLD || level.dimension() == Level.END) {
                            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
                            level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0f, 0.8f);
                            ((ServerLevel) level).sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5,
                                    pos.getY() + 0.5,
                                    pos.getZ() + 0.5, 3, 0.0, 0.05, 0.0, 0.0);
                            transformedPositions.add(pos);
                            transformedThisTick = true;
                            continue;
                        }
                    }
                }

                // Cobweb -> Air
                if (block == Blocks.COBWEB) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.8f, 1.1f);
                    ((ServerLevel) level).sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5,
                            pos.getZ() + 0.5,
                            3, 0.0, 0.05, 0.0, 0.0);
                    transformedPositions.add(pos);
                    transformedThisTick = true;
                    continue;
                }

                // Wet sponge -> Sponge
                if (block == Blocks.WET_SPONGE) {
                    level.setBlock(pos, Blocks.SPONGE.defaultBlockState(), 3);
                    level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.8f, 0.9f);
                    ((ServerLevel) level).sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5,
                            pos.getY() + 0.5,
                            pos.getZ() + 0.5, 3, 0.0, 0.05, 0.0, 0.0);
                    transformedPositions.add(pos);
                    transformedThisTick = true;
                    continue;
                }

                // Water cauldron <-> Powdered snow cauldron
                if (block == Blocks.WATER_CAULDRON) {
                    // Preserve the fill level when transforming
                    var currentState = level.getBlockState(pos);
                    var newState = Blocks.POWDER_SNOW_CAULDRON.defaultBlockState();
                    // Copy the "level" property if it exists in both states
                    if (currentState.hasProperty(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL)) {
                        int fillLevel = currentState
                                .getValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL);
                        newState = newState.setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL,
                                fillLevel);
                    }
                    level.setBlock(pos, newState, 3);
                    level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0f, 1.6f);
                    ((ServerLevel) level).sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 0.5,
                            pos.getZ() + 0.5,
                            3, 0.0, 0.03, 0.0, 0.0);
                    transformedPositions.add(pos);
                    transformedThisTick = true;
                    continue;
                }
                if (block == Blocks.POWDER_SNOW_CAULDRON) {
                    // Preserve the fill level when transforming
                    var currentState = level.getBlockState(pos);
                    var newState = Blocks.WATER_CAULDRON.defaultBlockState();
                    // Copy the "level" property if it exists in both states
                    if (currentState.hasProperty(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL)) {
                        int fillLevel = currentState
                                .getValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL);
                        newState = newState.setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL,
                                fillLevel);
                    }
                    level.setBlock(pos, newState, 3);
                    level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0f, 0.7f);
                    ((ServerLevel) level).sendParticles(ParticleTypes.DRIPPING_WATER, pos.getX() + 0.5,
                            pos.getY() + 0.5,
                            pos.getZ() + 0.5, 3, 0.0, 0.03, 0.0, 0.0);
                    transformedPositions.add(pos);
                    transformedThisTick = true;
                    continue;
                }
            }
        });
        fluxTask.setOnFinish(() -> {
            as.setInUse(false);
        });
        Ascension.scheduler.schedule(fluxTask);
    }

    /*
     * FLORA
     */

    private static final Map<UUID, Long> THORNS_ACTIVE = new HashMap<>();

    public static void thorns(ServerPlayer player) {
        ActiveSpell as = SpellCooldownManager.addToActiveSpells(player, SpellCooldownManager.get("thorns"));

        // Activate targeting mode for 5 seconds
        THORNS_ACTIVE.put(player.getUUID(), System.currentTimeMillis());
        player.sendSystemMessage(Component.literal("§aVines ready! Hit a target within 5 seconds..."));

        // Schedule timeout after 5 seconds
        Ascension.scheduler.schedule(new DelayedTask(100, () -> {
            if (THORNS_ACTIVE.remove(player.getUUID()) != null) {
                player.sendSystemMessage(Component.literal("§cThorns timed out!"));
                as.setInUse(false);
            }
        }));
    }

    public static void executeThorns(ServerPlayer player, LivingEntity target) {
        Long activationTime = THORNS_ACTIVE.remove(player.getUUID());
        if (activationTime == null) {
            return; // Not in targeting mode
        }

        // Check if within 5 second window
        if (System.currentTimeMillis() - activationTime > 5000) {
            return; // Timed out
        }

        Level level = player.level();

        // Spawn thorns animation
        Thorns.spawnThorns(player, target, 8, 60); // 8 vines, 3 second duration

        // Freeze target by preventing AI (if it's a mob)
        if (target instanceof net.minecraft.world.entity.Mob mob) {
            mob.setNoAi(true);
            Ascension.scheduler.schedule(new DelayedTask(60, () -> {
                mob.setNoAi(false);
            }));
        }

        // Apply poison
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.POISON, 200, 0, false, true));

        Utils.spellDmg(target, player, 15);

        // Play sound
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);

        player.sendSystemMessage(Component.literal("§aVines ensnare your target!"));

        // Mark spell as no longer in use
        ActiveSpell as = SpellCooldownManager.getActiveSpell(player, SpellCooldownManager.get("thorns"));
        if (as != null) {
            as.setInUse(false);
        }
    }

    public static boolean isThornsActive(ServerPlayer player) {
        return THORNS_ACTIVE.containsKey(player.getUUID());
    }

    /*
     * NETHER
     */

    private static final Map<UUID, HappyGhast> ACTIVE_GHASTS = new HashMap<>();

    public static void ghast_carry(ServerPlayer player) {
        // Check if player already has an active ghast
        if (ACTIVE_GHASTS.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§cYou already have a ghast summoned!"));
            return;
        }

        ActiveSpell as = SpellCooldownManager.addToActiveSpells(player, SpellCooldownManager.get("ghast_carry"));
        Level level = player.level();

        // Spawn ghast above the player
        HappyGhast ghast = EntityType.HAPPY_GHAST.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        if (ghast == null) {
            player.sendSystemMessage(Component.literal("§cFailed to summon ghast!"));
            as.setInUse(false);
            return;
        }

        Vec3 playerPos = player.position();
        ghast.teleportTo(playerPos.x, playerPos.y + 3, playerPos.z);

        // Make ghast non-aggressive and persistent
        ghast.setPersistenceRequired();
        ghast.setSilent(false); // Allow sounds

        // Add ghast to world
        level.addFreshEntity(ghast);

        // Store the ghast reference
        ACTIVE_GHASTS.put(player.getUUID(), ghast);

        // Spawn soul particles around the ghast
        if (level instanceof ServerLevel serverLevel) {
            for (int i = 0; i < 30; i++) {
                double offsetX = (Math.random() - 0.5) * 2.0;
                double offsetY = (Math.random() - 0.5) * 2.0;
                double offsetZ = (Math.random() - 0.5) * 2.0;
                serverLevel.sendParticles(ParticleTypes.SOUL,
                        ghast.getX() + offsetX, ghast.getY() + offsetY, ghast.getZ() + offsetZ,
                        1, 0, 0, 0, 0.02);
            }
        }

        // Make player ride the ghast
        player.startRiding(ghast);

        // Play summon sound
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GHAST_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.8f);

        // Monitor distance and control ghast
        Ascension.scheduler.schedule(new RepeatedTask(1, 6000, (task) -> { // 5 minutes max
            // Check if ghast is still valid
            if (ghast.isRemoved() || !ghast.isAlive() || !player.isAlive()) {
                cleanupGhast(player, ghast, as);
                task.cancel();
                return;
            }

            // Check distance between player and ghast
            double distance = player.position().distanceTo(ghast.position());
            if (distance > 3.0 && !player.isPassenger()) {
                // Player is too far from ghast and not riding it
                player.sendSystemMessage(Component.literal("§cYour ghast vanished as you strayed too far!"));
                cleanupGhast(player, ghast, as);
                task.cancel();
                return;
            }
        }));
    }

    private static void cleanupGhast(ServerPlayer player, HappyGhast ghast,
            ActiveSpell as) {
        ACTIVE_GHASTS.remove(player.getUUID());

        // Spawn soul particles on despawn
        if (ghast.level() instanceof ServerLevel serverLevel) {
            for (int i = 0; i < 30; i++) {
                double offsetX = (Math.random() - 0.5) * 2.0;
                double offsetY = (Math.random() - 0.5) * 2.0;
                double offsetZ = (Math.random() - 0.5) * 2.0;
                serverLevel.sendParticles(ParticleTypes.SOUL,
                        ghast.getX() + offsetX, ghast.getY() + offsetY, ghast.getZ() + offsetZ,
                        1, 0, 0, 0, 0.02);
            }
        }

        // Remove ghast
        if (!ghast.isRemoved()) {
            ghast.discard();
        }

        // Start cooldown
        if (as != null) {
            as.setInUse(false);
        }
    }

    public static void soul_drain(ServerPlayer player) {
        ActiveSpell as = SpellCooldownManager.addToActiveSpells(player, SpellCooldownManager.get("soul_drain"));

        // Play activation sound
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0f, 0.7f);

        // The actual healing effect is handled in the Nether order's
        // onEntityDamageByEntity method
        // This spell just activates for 10 seconds (200 ticks)
        Ascension.scheduler.schedule(new DelayedTask(200, () -> {
            as.setInUse(false);
        }));
    }

    /*
     * MAGIC — Shapeshift
     */

    public static void shapeshift(ServerPlayer player, int durationTicks) {
        AscensionData data = (AscensionData) player;
        if (data.getShapeshiftHistory().isEmpty()) {
            player.sendSystemMessage(
                    Component.literal("§cNo transformation available. Kill a mob to add it to your history."));
            return;
        }

        EntityType<?> form = data.popShapeshiftForm();
        if (form == null) {
            player.sendSystemMessage(Component.literal("§cNo transformation available."));
            return;
        }

        ActiveSpell as = SpellCooldownManager.addToActiveSpells(player, SpellCooldownManager.get("shapeshift"));

        try {
            xyz.nucleoid.disguiselib.api.EntityDisguise disguise = (xyz.nucleoid.disguiselib.api.EntityDisguise) player;
            disguise.disguiseAs(form);
            disguise.setTrueSight(true);

            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.NIGHT_VISION,
                    durationTicks + 40, 0, true, false, true));

            String formName = form.getDescription().getString();
            player.sendSystemMessage(Component.literal("§dYou have transformed into " + formName + "!"));

            Ascension.scheduler.schedule(new DelayedTask(durationTicks, () -> {
                try {
                    if (!player.isAlive() || player.isRemoved()) {
                        if (as != null)
                            as.setInUse(false);
                        return;
                    }
                    // Re-cast player to EntityDisguise to avoid stale reference issues
                    xyz.nucleoid.disguiselib.api.EntityDisguise freshDisguise = (xyz.nucleoid.disguiselib.api.EntityDisguise) player;
                    freshDisguise.disguiseAs(EntityType.PLAYER);
                    freshDisguise.setTrueSight(false);
                    player.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
                    player.sendSystemMessage(Component.literal("§7You have returned to your normal form."));
                } catch (Exception e) {
                    Ascension.LOGGER.error("Failed to restore player form after shapeshift: " + e.getMessage());
                } finally {
                    if (as != null)
                        as.setInUse(false);
                }
            }));
        } catch (Exception e) {
            Ascension.LOGGER.error("Failed to shapeshift player: " + e.getMessage());
            player.sendSystemMessage(Component.literal("§cShapeshift failed! Please try again."));
            if (as != null)
                as.setInUse(false);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // END — Teleport
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Teleport spell for the End Order.
     *
     * <p><b>Intent:</b> The player fires a ray from their eye position along their
     * look direction, up to 10 blocks. The ray stops after intersecting 2 solid
     * blocks. The player teleports to the <em>farthest valid</em> non-solid block
     * position along the path (a position where they would not suffocate). If no
     * valid destination exists, a chat message is sent and the cooldown still
     * starts. Enderman teleport particles are spawned at both origin and
     * destination on success.
     */
    public static void teleport(ServerPlayer player) {
        ActiveSpell as = SpellCooldownManager.addToActiveSpells(player, SpellCooldownManager.get("teleport"));

        ServerLevel level = (ServerLevel) player.level();
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookDir = player.getLookAngle();

        final int MAX_BLOCKS = 10;
        int solidCount = 0;
        BlockPos lastValid = null;

        for (int i = 1; i <= MAX_BLOCKS; i++) {
            Vec3 checkPos = eyePos.add(lookDir.scale(i));
            BlockPos bp = BlockPos.containing(checkPos);
            boolean solid = level.getBlockState(bp).isSolid();

            if (!solid) {
                // Non-solid: check player would not suffocate (need 2-block tall gap)
                BlockPos head = bp.above();
                if (!level.getBlockState(head).isSolid()) {
                    lastValid = bp;
                }
            } else {
                solidCount++;
                if (solidCount >= 2) break; // Stop checking beyond 2nd solid
            }
        }

        if (lastValid == null) {
            player.sendSystemMessage(Component.literal("§cTeleport failed — no clear destination!"));
            as.setInUse(false);
            return;
        }

        // Spawn enderman particles at origin
        Vec3 origin = player.position();
        level.sendParticles(ParticleTypes.PORTAL,
                origin.x, origin.y + 1, origin.z, 30, 0.3, 0.5, 0.3, 0.1);
        level.playSound(null, BlockPos.containing(origin),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);

        // Teleport
        BlockPos dest = lastValid;
        player.teleportTo(level, dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5,
                java.util.Set.of(), player.getYRot(), player.getXRot(), true);

        // Spawn enderman particles at destination
        level.sendParticles(ParticleTypes.PORTAL,
                dest.getX() + 0.5, dest.getY() + 1, dest.getZ() + 0.5,
                30, 0.3, 0.5, 0.3, 0.1);
        level.playSound(null, dest, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);

        as.setInUse(false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // END — Desolation of Time
    // ─────────────────────────────────────────────────────────────────────────

    /** Track active desolation VFX by caster UUID for cleanup. */
    private static final Map<UUID, DragonCurve> ACTIVE_DESOLATIONS = new ConcurrentHashMap<>();

    /**
     * Desolation of Time combat spell for the End Order.
     *
     * <p><b>Intent:</b> Within a 7-block radius of the caster, all players (except
     * the caster) have their combat abilities disabled for 5 seconds (100 ticks)
     * and receive Weakness I for 10 seconds (200 ticks). New players who enter the
     * radius during the spell are also affected. Players already affected cannot
     * have their timer reset. The caster is always exempt. A DragonCurve VFX is
     * animated during the spell duration.
     *
     * @param player the caster
     * @param stats  the spell's stat block (extra[0]=disable ticks, extra[1]=weakness ticks)
     */
    public static void desolationOfTime(ServerPlayer player, SpellStats stats) {
        ActiveSpell as = SpellCooldownManager.addToActiveSpells(
                player, SpellCooldownManager.get("desolation_of_time"));

        int disableTicks = stats.extra().length > 0 ? stats.getInt(0) : 100;
        int weaknessTicks = stats.extra().length > 1 ? stats.getInt(1) : 200;

        ServerLevel level = (ServerLevel) player.level();
        Vec3 castPos = player.position();

        // Start DragonCurve VFX
        DragonCurve curve = new DragonCurve(level,
                new Vector3f((float) castPos.x, (float) castPos.y, (float) castPos.z), 8);
        ACTIVE_DESOLATIONS.put(player.getUUID(), curve);

        // Track affected players for this activation to prevent double-disable
        Set<UUID> affectedThisActivation = ConcurrentHashMap.newKeySet();

        // Helper to apply desolation to a single target
        java.util.function.Consumer<ServerPlayer> applyDesolation = (target) -> {
            if (affectedThisActivation.contains(target.getUUID())) return;
            affectedThisActivation.add(target.getUUID());
            End.DESOLATED_PLAYERS.add(target.getUUID());
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, weaknessTicks, 0, false, true, true));
            target.sendSystemMessage(Component.literal(
                    "§5Your combat abilities have been disabled for " + (disableTicks / 20) + " seconds!"));

            // Schedule removal of combat disable
            Ascension.scheduler.schedule(new DelayedTask(disableTicks, () ->
                    End.DESOLATED_PLAYERS.remove(target.getUUID())));
        };

        // Initial sweep: affect all nearby players (excluding caster)
        AABB searchBox = new AABB(castPos.x - 7, castPos.y - 7, castPos.z - 7,
                castPos.x + 7, castPos.y + 7, castPos.z + 7);
        for (ServerPlayer nearby : level.getEntitiesOfClass(ServerPlayer.class, searchBox)) {
            if (nearby.getUUID().equals(player.getUUID())) continue;
            if (nearby.position().distanceTo(castPos) <= 7.0) {
                applyDesolation.accept(nearby);
            }
        }

        // Recurring scan for new entrants every 5 ticks
        final int[] elapsed = {0};
        ContinuousTask scanTask = new ContinuousTask(5, () -> {
            elapsed[0] += 5;
            if (elapsed[0] >= disableTicks) return;
            for (ServerPlayer nearby : level.getEntitiesOfClass(ServerPlayer.class, searchBox)) {
                if (nearby.getUUID().equals(player.getUUID())) continue;
                if (nearby.position().distanceTo(castPos) <= 7.0) {
                    applyDesolation.accept(nearby);
                }
            }
        });
        Ascension.scheduler.schedule(scanTask);

        // Teardown: stop scan + VFX after disableTicks
        Ascension.scheduler.schedule(new DelayedTask(disableTicks, () -> {
            scanTask.stop();
            DragonCurve activeCurve = ACTIVE_DESOLATIONS.remove(player.getUUID());
            if (activeCurve != null) activeCurve.teardown();
            as.setInUse(false);
        }));
    }
}
