package freq.ascension.weapons;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import freq.ascension.Ascension;
import freq.ascension.Utils;
import freq.ascension.api.ContinuousTask;
import freq.ascension.api.DelayedTask;
import freq.ascension.orders.Ocean;
import freq.ascension.orders.Order;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import freq.ascension.mixin.EntitySharedFlagInvoker;

/**
 * Tempest Trident — mythical weapon for the Ocean order.
 *
 * <p>Two modes toggled via shift+left-click:
 * <ul>
 *   <li><b>Loyalty mode</b> (default): Loyalty 3, tracks hits. Every 3rd projectile hit
 *       triggers a cosmetic lightning strike + 20% max-HP spell damage.</li>
 *   <li><b>Riptide mode</b>: Riptide 3, disables hit counter.</li>
 * </ul>
 *
 * <p>The custom model data strings are {@code "tempest_trident_loyalty"} and
 * {@code "tempest_trident_riptide"}, both sharing the {@code "tempest_trident"} prefix.
 * {@link #isItem(ItemStack)} is overridden to use prefix matching rather than exact equality.
 *
 * <p>VFX: When thrown in loyalty mode, the ThrownTrident entity is made invisible and an
 * {@code ItemDisplay} entity tracks its position every tick.
 *
 * <p>Pickup restriction: only the player who threw the trident may retrieve it.
 * Enforced via {@code ThrownTridentPickupMixin}.
 *
 * <p>Recovery: if the thrown trident falls into the void or crosses dimensions, it is discarded
 * and the owner receives a fresh Tempest Trident.
 */
public class TempestTrident implements MythicWeapon {

    public static final TempestTrident INSTANCE = new TempestTrident();

    /** Custom model data string for loyalty mode (default). */
    public static final String MODEL_LOYALTY = "tempest_trident_loyalty";
    /** Custom model data string for riptide mode. */
    public static final String MODEL_RIPTIDE  = "tempest_trident_riptide";

    // ─── Tracking maps ────────────────────────────────────────────────────────

    /** Per-target hit counter (keyed by victim UUID). Resets to 0 after every 3rd trigger. */
    public static final ConcurrentHashMap<UUID, Integer> hitCounters = new ConcurrentHashMap<>();

    /** Timestamp (game tick) of the last hit on each target UUID. Used to expire stale counters after 60 ticks. */
    public static final ConcurrentHashMap<UUID, Long> hitTimestamps = new ConcurrentHashMap<>();

    /** Per-player last-toggle-tick for mode switch deduplication (prevents double-fire from Swing + hit events). */
    public static final ConcurrentHashMap<UUID, Long> lastToggleTick = new ConcurrentHashMap<>();

    /**
     * Per-player last-mode-switch tick enforcing a minimum 10-tick gap between toggles.
     * Supersedes {@link #lastToggleTick} for the actual gate check, but both are kept for
     * test backward-compatibility.
     */
    public static final ConcurrentHashMap<UUID, Long> lastModeSwitchTick = new ConcurrentHashMap<>();

    /** Maps thrown ThrownTrident entity UUID → thrower (player) UUID. */
    public static final ConcurrentHashMap<UUID, UUID> tridentToThrower = new ConcurrentHashMap<>();

    /** Maps thrown ThrownTrident entity UUID → ItemDisplay entity UUID for VFX. */
    public static final ConcurrentHashMap<UUID, UUID> tridentToDisplay = new ConcurrentHashMap<>();

    /** Maps thrown ThrownTrident entity UUID → the running ContinuousTask so it can be stopped. */
    public static final ConcurrentHashMap<UUID, ContinuousTask> tridentTrackingTasks = new ConcurrentHashMap<>();

    private static volatile boolean cleanupRegistered = false;

    // ─── Identity ─────────────────────────────────────────────────────────────

    @Override
    public String getWeaponId() {
        return "tempest_trident";
    }

    @Override
    public Item getBaseItem() {
        return Items.TRIDENT;
    }

    @Override
    public Order getParentOrder() {
        return Ocean.INSTANCE;
    }

    /**
     * Overridden because the actual model strings are {@code "tempest_trident_loyalty"} and
     * {@code "tempest_trident_riptide"} — both start with the weapon ID prefix but neither
     * equals it exactly. Checks any string in the CustomModelData list that starts with
     * {@code "tempest_trident"}.
     */
    @Override
    public boolean isItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null) return false;
        return cmd.strings().stream().anyMatch(s -> s.startsWith("tempest_trident"));
    }

    // ─── Item creation ────────────────────────────────────────────────────────

    @Override
    public ItemStack createItem() {
        ItemStack stack = new ItemStack(Items.TRIDENT);

        // Custom model data — loyalty mode is the default
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of(MODEL_LOYALTY), List.of()));

        // Unbreakable
        stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);

        // Display name: Ocean colour, bold, non-italic
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("Tempest Trident")
                        .withStyle(style -> style
                                .withColor(Ocean.INSTANCE.getOrderColor())
                                .withItalic(false)
                                .withBold(true)));

        // Override attribute modifiers to match a Sharpness 5 diamond sword:
        //   +10.0 attack damage, -2.4 attack speed.
        // Setting DataComponents.ATTRIBUTE_MODIFIERS fully replaces the trident's defaults.
        ItemAttributeModifiers modifiers = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(
                                ResourceLocation.fromNamespaceAndPath("ascension", "tempest_trident_damage"),
                                10.0,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(
                                ResourceLocation.fromNamespaceAndPath("ascension", "tempest_trident_speed"),
                                -2.4,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);

        // Enchantments require a live server registry
        if (Ascension.getServer() != null) {
            applyDefaultEnchantments(stack);
        }

        return stack;
    }

    /** Applies Loyalty 3, Impaling 5, Sharpness 5, and Curse of Vanishing. */
    static void applyDefaultEnchantments(ItemStack stack) {
        var enchReg = Ascension.getServer().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT);
        stack.enchant(enchReg.getOrThrow(Enchantments.LOYALTY), 3);
        stack.enchant(enchReg.getOrThrow(Enchantments.IMPALING), 5);
        stack.enchant(enchReg.getOrThrow(Enchantments.SHARPNESS), 5);
        stack.enchant(enchReg.getOrThrow(Enchantments.VANISHING_CURSE), 1);
    }

    // ─── Mode toggle ──────────────────────────────────────────────────────────

    /**
     * Shift+left-click: toggle between Loyalty 3 and Riptide 3 modes.
     * Updates the custom model data string and plays the appropriate sound.
     */
    @Override
    public void onShiftLeftClick(ServerPlayer player) {
        if (Ascension.getServer() == null) return;
        UUID pid = player.getUUID();
        long currentTick = Ascension.getServer().getTickCount();

        // Enforce a minimum 10-tick gap between mode switches to avoid double-fire from
        // Swing mixin + hit callbacks both dispatching onShiftLeftClick in the same window.
        if (currentTick - lastModeSwitchTick.getOrDefault(pid, -100L) < 10) return;
        lastModeSwitchTick.put(pid, currentTick);

        // Also keep the old exact-tick map updated for backward-compat with existing tests.
        lastToggleTick.put(pid, currentTick);

        ItemStack held = findTempestTrident(player);
        if (held == null) return;

        CustomModelData cmd = held.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null) return;

        boolean currentlyLoyalty = cmd.strings().stream().anyMatch(s -> s.equals(MODEL_LOYALTY));

        var enchReg = Ascension.getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var loyaltyHolder = enchReg.getOrThrow(Enchantments.LOYALTY);
        var riptideHolder  = enchReg.getOrThrow(Enchantments.RIPTIDE);

        if (currentlyLoyalty) {
            // Switch to riptide
            held.set(DataComponents.CUSTOM_MODEL_DATA,
                    new CustomModelData(List.of(), List.of(), List.of(MODEL_RIPTIDE), List.of()));
            swapEnchantment(held, loyaltyHolder, 0, riptideHolder, 3);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 1.0f, 1.2f);
        } else {
            // Switch to loyalty
            held.set(DataComponents.CUSTOM_MODEL_DATA,
                    new CustomModelData(List.of(), List.of(), List.of(MODEL_LOYALTY), List.of()));
            swapEnchantment(held, riptideHolder, 0, loyaltyHolder, 3);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 1.0f, 1.0f);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 1.0f, 1.2f);
        }

        // Splash particle burst on every mode switch
        if (player.level() instanceof ServerLevel sl) {
            Vec3 pos = player.position();
            sl.sendParticles(ParticleTypes.SPLASH,    pos.x, pos.y + 1, pos.z, 15, 0.3, 0.3, 0.3, 0.5);
            sl.sendParticles(ParticleTypes.BUBBLE_POP, pos.x, pos.y + 1, pos.z,  8, 0.2, 0.2, 0.2, 0.2);
        }
    }

    /** Removes {@code removeEnch} and sets {@code addEnch} at the specified level on the stack. */
    private static void swapEnchantment(
            ItemStack stack,
            net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> removeEnch, int removeLevel,
            net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> addEnch,   int addLevel) {
        ItemEnchantments current = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(current);
        mutable.set(removeEnch, removeLevel); // level 0 removes the enchantment
        mutable.set(addEnch, addLevel);
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
    }

    // ─── Hit counter ──────────────────────────────────────────────────────────

    /**
     * Called when a projectile owned by this player hits a living entity.
     * Only increments the counter in loyalty mode. Every 3rd hit triggers a cosmetic lightning
     * strike and 20% max-HP spell damage.
     */
    @Override
    public void onProjectileHit(ServerPlayer owner, LivingEntity victim, Order.DamageContext ctx) {
        ItemStack weapon = findTempestTrident(owner);
        if (weapon == null || !isLoyaltyModeStack(weapon)) return;

        // Track hit timestamp per target for 60-tick expiry and count hits against this target.
        UUID targetId = victim.getUUID();
        hitTimestamps.put(targetId, ((ServerLevel) owner.level()).getGameTime());

        boolean triggered = processHitCounter(targetId);
        if (triggered) {
            // Play high-pitch charge sound before the thunder so players get clear feedback.
            victim.level().playSound(null, victim.blockPosition(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0f, 1.4f);
            triggerLightningStrike(owner, victim);
        } else {
            // Charging sound: pitch rises from 0.8 → 1.0 as the counter approaches 3.
            int count = hitCounters.getOrDefault(targetId, 0);
            float pitch = count == 1 ? 0.8f : 1.0f;
            victim.level().playSound(null, victim.blockPosition(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0f, pitch);
        }
    }

    /**
     * Increments the hit counter for the given attacker and fires on every 3rd hit.
     * Resets the counter to 0 after triggering. Not cumulative.
     *
     * @return {@code true} if the 3rd-hit threshold was reached and the counter was reset.
     */
    public static boolean processHitCounter(UUID attackerId) {
        int count = hitCounters.merge(attackerId, 1, Integer::sum);
        if (count % 3 == 0) {
            hitCounters.put(attackerId, 0);
            return true;
        }
        return false;
    }

    /** Spawns cosmetic lightning + plays thunder + applies 20% max-HP spell damage. */
    private static void triggerLightningStrike(ServerPlayer attacker, LivingEntity victim) {
        if (victim.level() instanceof ServerLevel serverLevel) {
            // Cosmetic lightning bolt: effect=true means no fire or damage
            var bolt = EntityType.LIGHTNING_BOLT.create(serverLevel, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.setPos(Vec3.atCenterOf(victim.blockPosition()));
                bolt.setVisualOnly(true);
                serverLevel.addFreshEntity(bolt);
            }
            serverLevel.playSound(null, victim.blockPosition(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
        Utils.spellDmg(victim, attacker, 20.0f);
    }

    // ─── VFX: thrown trident visual ───────────────────────────────────────────

    /**
     * Called when a ThrownTrident created from a TempestTrident is detected in the world.
     * Registers the thrower, makes the trident invisible, and spawns an {@code ItemDisplay}
     * entity that follows the trident every tick.
     *
     * <p>Invoked from {@code ThrownTridentPickupMixin} when the entity is added to the world.
     */
    public static void onTridentThrown(ThrownTrident tridentEntity, ServerPlayer thrower) {
        UUID tridentId = tridentEntity.getUUID();
        tridentToThrower.put(tridentId, thrower.getUUID());

        if (!(thrower.level() instanceof ServerLevel serverLevel)) return;

        // Only show VFX in loyalty mode
        ItemStack tridentStack = tridentEntity.getWeaponItem();
        if (!isLoyaltyModeStack(tridentStack)) return;

        // Make the vanilla entity invisible so only our ItemDisplay is visible
        Ascension.LOGGER.info("[TridentVis] Trident thrown, UUID={}, setting invisible", tridentEntity.getUUID());
        tridentEntity.setInvisible(true);
        // Re-apply after 1 tick to ensure invisibility is sent after entity tracking initializes
        Ascension.scheduler.schedule(new DelayedTask(1, () -> {
            if (!tridentEntity.isRemoved()) tridentEntity.setInvisible(true);
        }));

        // Spawn ItemDisplay at the trident's initial position
        Display.ItemDisplay display = (Display.ItemDisplay) EntityType.ITEM_DISPLAY.create(
                serverLevel, EntitySpawnReason.TRIGGERED);
        if (display == null) return;

        ItemStack displayStack = new ItemStack(Items.TRIDENT);
        displayStack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of(MODEL_LOYALTY), List.of()));
        display.setItemStack(displayStack);
        display.setItemTransform(net.minecraft.world.item.ItemDisplayContext.FIXED);
        display.setPos(tridentEntity.getX(), tridentEntity.getY(), tridentEntity.getZ());

        // Apply initial rotation based on throw velocity so the model faces the right way from frame 0.
        Vec3 initialVel = tridentEntity.getDeltaMovement();
        if (initialVel.lengthSqr() > 0.0001) {
            Vec3 initDir = initialVel.normalize();
            org.joml.Vector3f fromInit = new org.joml.Vector3f(0, 1, 0);
            org.joml.Vector3f toInit = new org.joml.Vector3f((float) initDir.x, (float) initDir.y, (float) initDir.z);
            org.joml.Quaternionf initRot = new org.joml.Quaternionf().rotationTo(fromInit, toInit);
            display.setTransformation(new com.mojang.math.Transformation(
                    new org.joml.Vector3f(0, 0, 0), initRot,
                    new org.joml.Vector3f(1, 1, 1), new org.joml.Quaternionf()));
        }

        display.setTransformationInterpolationDuration(1);
        display.setTransformationInterpolationDelay(-1); // start interpolation immediately
        serverLevel.addFreshEntity(display);
        tridentToDisplay.put(tridentId, display.getUUID());

        // Re-apply invisibility after 1 tick (entity tracking init) and 2 ticks (chunk-load delay).
        Ascension.scheduler.schedule(new DelayedTask(1, () -> {
            if (!tridentEntity.isRemoved()) tridentEntity.setInvisible(true);
        }));
        // Remove the vanilla trident entity client-side for all players (it stays server-side)
        Ascension.scheduler.schedule(new DelayedTask(2, () -> {
            if (!tridentEntity.isRemoved()) {
                ServerLevel level = (ServerLevel) tridentEntity.level();
                net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket removePacket =
                    new net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket(tridentEntity.getId());
                for (ServerPlayer p : level.players()) {
                    p.connection.send(removePacket);
                }
                Ascension.LOGGER.info("[TridentVis] Sent remove packet for trident ID={}", tridentEntity.getId());
            }
        }));
        Ascension.scheduler.schedule(new DelayedTask(3, () -> {
            if (!tridentEntity.isRemoved()) {
                tridentEntity.setInvisible(true);
            }
        }));

        // Tick task: sync display position/rotation to the trident every tick
        int[] tickCount = {0};
        ContinuousTask trackingTask = new ContinuousTask(1, () -> {
            tickCount[0]++;
            if (tridentEntity.isRemoved()) {
                // Trident is gone — discard display and cancel task
                if (!display.isRemoved()) display.discard();
                tridentToDisplay.remove(tridentId);
                ContinuousTask task = tridentTrackingTasks.remove(tridentId);
                if (task != null) task.stop();
                return;
            }
            // Re-enforce invisibility each tick — the entity-tracking packet can reset it.
            tridentEntity.setInvisible(true);
            // Belt-and-suspenders: directly set shared flag 5 (invisible bit) so the
            // metadata flush every 4 ticks cannot overwrite our invisible state.
            ((EntitySharedFlagInvoker) tridentEntity).invokeSetSharedFlag(5, true);
            tridentEntity.setGlowingTag(false);
            // Every 20 ticks re-send remove packet in case clients re-added the entity via late tracking.
            if (tickCount[0] % 20 == 0) {
                ServerLevel level = (ServerLevel) tridentEntity.level();
                net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket removePacket =
                    new net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket(tridentEntity.getId());
                for (ServerPlayer p : level.players()) {
                    p.connection.send(removePacket);
                }
            }
            // Ensure the trident's physics / hitbox remain active.
            tridentEntity.noPhysics = false;

            // Compute travel direction to align display position and rotation.
            Vec3 vel = tridentEntity.getDeltaMovement();
            Vec3 dir = vel.lengthSqr() > 0.0001 ? vel.normalize() : Vec3.ZERO;

            // Position display exactly at the trident — the vanilla entity is removed client-side
            // so the ItemDisplay is the only visible representation.
            display.setPos(
                    tridentEntity.getX(),
                    tridentEntity.getY(),
                    tridentEntity.getZ());
            if (Ascension.getServer() != null && Ascension.getServer().getTickCount() % 20 == 0) {
                Ascension.LOGGER.info("[TridentPos] trident=({},{},{}) display=({},{},{})",
                    tridentEntity.getX(), tridentEntity.getY(), tridentEntity.getZ(),
                    display.getX(), display.getY(), display.getZ());
            }
            display.setTransformationInterpolationDelay(0);
            display.setTransformationInterpolationDuration(1);

            // Rotate the item display to face the trident's travel direction.
            if (dir.lengthSqr() > 0.0001) {
                org.joml.Vector3f from = new org.joml.Vector3f(0, 1, 0); // trident model tip points +Y in FIXED context
                org.joml.Vector3f to   = new org.joml.Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
                org.joml.Quaternionf rotation = new org.joml.Quaternionf().rotationTo(from, to);
                display.setTransformation(new com.mojang.math.Transformation(
                        new org.joml.Vector3f(0, 0, 0),
                        rotation,
                        new org.joml.Vector3f(1, 1, 1),
                        new org.joml.Quaternionf()));
            }

            // Expire stale per-target hit counters: no hit on this target for > 60 ticks resets it.
            if (Ascension.getServer() != null) {
                long now = Ascension.getServer().getTickCount();
                hitTimestamps.entrySet().removeIf(e -> {
                    if (now - e.getValue() > 60) {
                        hitCounters.remove(e.getKey());
                        return true;
                    }
                    return false;
                });
            }
        });
        tridentTrackingTasks.put(tridentId, trackingTask);
        Ascension.scheduler.schedule(trackingTask);
    }

    // ─── Recovery ─────────────────────────────────────────────────────────────

    /**
     * Registers server-lifecycle and connection cleanup hooks.
     * Safe to call multiple times — only the first call has any effect.
     * Must be called from {@code Ascension.onInitialize()}.
     */
    public static void register() {
        if (cleanupRegistered) return;
        cleanupRegistered = true;

        // Track thrown tridents via entity load event (fires when entity is added to the world)
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof ThrownTrident trident)) return;
            if (!(world instanceof ServerLevel)) return;

            // Check if this trident is a TempestTrident
            ItemStack stack = trident.getWeaponItem();
            if (!INSTANCE.isItem(stack)) return;

            // Find the owner (thrower)
            var owner = trident.getOwner();
            if (!(owner instanceof ServerPlayer player)) return;

            onTridentThrown(trident, player);
        });

        // Dimension-change recovery: discard trident + ItemDisplay, give owner a new one
        ServerEntityWorldChangeEvents.AFTER_ENTITY_CHANGE_WORLD.register(
                (originalEntity, newEntity, origin, destination) -> {
                    if (!(originalEntity instanceof ThrownTrident)) return;
                    UUID tridentId = originalEntity.getUUID();
                    UUID ownerId = tridentToThrower.remove(tridentId);
                    if (ownerId == null) return;

                    // Discard both the original and the new-world entity
                    if (!originalEntity.isRemoved()) originalEntity.discard();
                    if (!newEntity.isRemoved())      newEntity.discard();
                    cleanupDisplay(tridentId, origin);

                    ServerPlayer ownerPlayer = origin.getServer() != null
                            ? origin.getServer().getPlayerList().getPlayer(ownerId) : null;
                    if (ownerPlayer != null) {
                        ownerPlayer.getInventory().add(INSTANCE.createItem());
                        ownerPlayer.sendSystemMessage(
                                Component.literal("§bYour Tempest Trident returned to you."));
                    }
                });

        // Per-player cleanup on disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID playerId = handler.getPlayer().getUUID();
            hitCounters.remove(playerId);
            lastToggleTick.remove(playerId);
            lastModeSwitchTick.remove(playerId);

            // Clean up any tridents thrown by this player
            List<UUID> ownedTridents = new ArrayList<>();
            tridentToThrower.forEach((tid, uid) -> {
                if (uid.equals(playerId)) ownedTridents.add(tid);
            });
            ownedTridents.forEach(tid -> {
                tridentToThrower.remove(tid);
                ContinuousTask task = tridentTrackingTasks.remove(tid);
                if (task != null) task.stop();
                UUID displayId = tridentToDisplay.remove(tid);
                if (displayId != null) {
                    for (var level : server.getAllLevels()) {
                        var e = level.getEntity(displayId);
                        if (e != null && !e.isRemoved()) { e.discard(); break; }
                    }
                }
            });
        });

        // Full cleanup on server stop
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            hitCounters.clear();
            hitTimestamps.clear();
            lastToggleTick.clear();
            lastModeSwitchTick.clear();
            tridentToThrower.clear();
            tridentToDisplay.clear();
            tridentTrackingTasks.values().forEach(ContinuousTask::stop);
            tridentTrackingTasks.clear();
        });

        // Void recovery: check every 20 ticks for tridents that fell below min-Y
        Ascension.scheduler.schedule(new ContinuousTask(20, () -> {
            if (Ascension.getServer() == null) return;
            Ascension.getServer().getAllLevels().forEach(level -> {
                if (!(level instanceof ServerLevel serverLevel)) return;

                List<UUID> fallen = new ArrayList<>();
                tridentToThrower.forEach((tridentId, ownerId) -> {
                    var entity = serverLevel.getEntity(tridentId);
                    if (entity instanceof ThrownTrident trident
                            && trident.getY() < serverLevel.getMinY()) {
                        fallen.add(tridentId);
                    }
                });

                fallen.forEach(tridentId -> {
                    UUID ownerId = tridentToThrower.remove(tridentId);
                    ContinuousTask task = tridentTrackingTasks.remove(tridentId);
                    if (task != null) task.stop();
                    cleanupDisplay(tridentId, serverLevel);

                    var tridentEntity = serverLevel.getEntity(tridentId);
                    if (tridentEntity != null && !tridentEntity.isRemoved()) tridentEntity.discard();

                    if (ownerId != null && Ascension.getServer() != null) {
                        ServerPlayer owner = Ascension.getServer().getPlayerList().getPlayer(ownerId);
                        if (owner != null) {
                            owner.getInventory().add(INSTANCE.createItem());
                            owner.sendSystemMessage(
                                    Component.literal("§bYour Tempest Trident was recovered from the void."));
                        }
                    }
                });
            });
        }));

        // Global cleanup: expire per-target hit counters that haven't been refreshed in 60 ticks.
        // This runs even when no trident is in flight, covering hits made after the trident landed.
        Ascension.scheduler.schedule(new ContinuousTask(20, () -> {
            if (Ascension.getServer() == null) return;
            long now = Ascension.getServer().getTickCount();
            hitTimestamps.entrySet().removeIf(e -> {
                if (now - e.getValue() > 60) {
                    hitCounters.remove(e.getKey());
                    return true;
                }
                return false;
            });
        }));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the stack has model data string {@code "tempest_trident_loyalty"}.
     * Used by the hit counter and VFX logic to gate loyalty-only behaviour.
     */
    public static boolean isLoyaltyModeStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null) return false;
        return cmd.strings().stream().anyMatch(s -> s.equals(MODEL_LOYALTY));
    }

    /** Discards the ItemDisplay entity tracked for the given trident UUID, if any. */
    private static void cleanupDisplay(UUID tridentId, ServerLevel level) {
        UUID displayId = tridentToDisplay.remove(tridentId);
        if (displayId == null) return;
        var displayEntity = level.getEntity(displayId);
        if (displayEntity != null && !displayEntity.isRemoved()) displayEntity.discard();
    }

    /** Scans the player's full inventory for the first TempestTrident stack, or {@code null}. */
    private ItemStack findTempestTrident(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (isItem(s)) return s;
        }
        return null;
    }
}
