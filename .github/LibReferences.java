/**
 * LibReferences.java — Agent reference document for the Ascension mod.
 *
 * This file is NOT compiled. It is a structured code-reference document for
 * AI agents. Each section contains annotated examples drawn directly from the
 * codebase so that an agent can understand every system and its expected
 * patterns without reading every file individually.
 *
 * INDEX
 * ──────────────────────────────────────────────────────────────────────────
 * 1. Orders — Order interface, singletons, event hooks, getVersion
 * 2. Spells — Spell lifecycle, SpellStats, registration, binding
 * 3. Abilities — AbilityManager.broadcast / anyMatch, mixin pattern
 * 4. Task Scheduler — ContinuousTask, DelayedTask, RepeatedTask
 * 5. Animation — VFXBuilder chain, sticky vs. non-sticky, GeometrySource
 * 6. Persistent Data — AscensionData interface, cast pattern, stored fields
 * 7. Ascension Menu — sgui BookGui, page building, click/hover events
 * 8. Utils — Static helpers overview
 * 9. Config — night-config pattern, comments, getOrElse
 * ──────────────────────────────────────────────────────────────────────────
 */
public class LibReferences {

    // ═══════════════════════════════════════════════════════════════════════
    // 1. ORDERS
    // ═══════════════════════════════════════════════════════════════════════
    /**
     * OVERVIEW
     * ─────────
     * Every order is a singleton implementing the Order interface
     * (src/main/java/freq/ascension/orders/Order.java).
     * The interface defines all possible event hooks as default no-ops, so a
     * concrete order only overrides the events it cares about.
     *
     * KEY POINTS
     * ───────────
     * • INSTANCE field — singleton handle. Never instantiate an order directly.
     * • getVersion(rank) — returns EarthGod.INSTANCE when rank == "god", else
     * returns `this`. God classes extend the demigod and override stats.
     * • getOrderName() — lowercase string key used everywhere as an ID.
     * • getOrderIcon() — single-character string shown in the menu.
     * • getOrderColor() — TextColor used in the menu and action bar.
     * • getDescription(slotType) — human-readable ability text per slot.
     * • registerSpells() — called once at startup by OrderRegistry to register
     * all Spell objects for this order into SpellCooldownManager.
     * • getSpellStats(spellId) — returns a SpellStats record with cooldown,
     * description, and arbitrary extra args. God versions override this to
     * upgrade numbers.
     *
     * CONCRETE ORDER SKELETON
     * ────────────────────────
     *
     * public class MyOrder implements Order {
     * public static final MyOrder INSTANCE = new MyOrder();
     *
     * @Override public String getOrderName() { return "myorder"; }
     * @Override public String getOrderIcon() { return "♦"; }
     * @Override public TextColor getOrderColor() {
     *           return TextColor.fromRgb(0xAB1234);
     *           }
     *
     *           // Returns the god version when rank == "god"
     * @Override public Order getVersion(String rank) {
     *           return rank.equals("god") ? MyOrderGod.INSTANCE : this;
     *           }
     *
     * @Override public String getDescription(String slotType) {
     *           return switch (slotType.toLowerCase()) {
     *           case "passive" -> "Passive description here.";
     *           case "utility" -> "SPELL_NAME: " +
     *           getSpellStats("my_spell").getDescription();
     *           case "combat" -> "COMBAT_SPELL: description. Cooldown " +
     *           getSpellStats("my_combat").getCooldownSecs() + "s.";
     *           default -> "";
     *           };
     *           }
     *
     * @Override public void registerSpells() {
     *           SpellCooldownManager.register(new Spell("my_spell", this,
     *           "utility",
     *           (player, stats) -> SpellRegistry.mySpellImpl(player,
     *           stats.getInt(0))));
     *
     *           SpellCooldownManager.register(new Spell("my_combat", this,
     *           "combat",
     *           (player, stats) -> SpellRegistry.myCombatImpl(player,
     *           stats.getInt(0), stats.getBool(1))));
     *           }
     *
     * @Override public SpellStats getSpellStats(String spellId) {
     *           return switch (spellId.toLowerCase()) {
     *           // SpellStats(cooldownTicks, description, ...extraArgs)
     *           case "my_spell" -> new SpellStats(60, "Toggle something.", 3);
     *           case "my_combat" -> new SpellStats(200, "Deals 25% max hp.", 5,
     *           true);
     *           default -> null;
     *           };
     *           }
     *
     *           // ── Event hooks (all inherited as no-ops; override only what you
     *           need) ──
     *
     * @Override
     *           public void onBlockBreak(ServerPlayer player, ServerLevel level,
     *           BlockPos pos, BlockState state, BlockEntity entity) {
     *           // Example: double ore drops (see Earth.java for full
     *           implementation)
     *           if (state.is(ORE_TAG)) {
     *           List<ItemStack> drops = Block.getDrops(state, level, pos, entity,
     *           player, player.getMainHandItem());
     *           drops.forEach(drop -> Block.popResource(level, pos, drop));
     *           }
     *           }
     *
     * @Override
     *           public void onEntityDamageByEntity(ServerPlayer attacker,
     *           LivingEntity victim,
     *           Order.DamageContext ctx) {
     *           // DamageContext lets you modify or cancel the damage
     *           ctx.setAmount(ctx.getAmount() * 1.5f); // 50% bonus damage
     *           }
     *
     * @Override public boolean isDoubleJumpEnabled() { return true; }
     *
     * @Override public boolean ignoreAnvilCostLimit() { return true; }
     * @Override public boolean preventAnvilDamage() { return true; }
     *           }
     *
     *           FULL ORDER EVENT LIST (defined in Order.java, all have default
     *           no-ops)
     *           ─────────────────────────────────────────────────────────────────────
     *           void onBlockDamage(ServerPlayer, ServerLevel, BlockPos, ItemStack)
     *           void onBlockBreak(ServerPlayer, ServerLevel, BlockPos, BlockState,
     *           BlockEntity)
     *           void onAnvilPrepare(AnvilMenu)
     *           boolean ignoreAnvilCostLimit()
     *           boolean preventAnvilDamage()
     *           boolean isDoubleJumpEnabled()
     *           void onEntityDamageByEntity(ServerPlayer attacker, LivingEntity
     *           victim, DamageContext)
     *           void onEntityDamaged(ServerPlayer victim, LivingEntity attacker,
     *           DamageContext)
     *           void onPlayerKill(ServerPlayer killer, LivingEntity victim)
     *           void onProjectileFired(ServerPlayer shooter, Projectile projectile)
     *           void onProjectileHit(ServerPlayer shooter, Entity hit, Projectile
     *           projectile)
     *           void onMobEffect(ServerPlayer player, MobEffectInstance effect)
     *           void applyPassiveEffects(ServerPlayer player) ← called every 40
     *           ticks by AbilityManager
     *           void onJump(ServerPlayer player)
     *           void onDeath(ServerPlayer player, DamageSource source)
     *           void onRespawn(ServerPlayer player)
     *           void onFishingIn(ServerPlayer player, ServerLevel level)
     *
     *           GOD VERSION PATTERN
     *           ────────────────────
     *           public class MyOrderGod extends MyOrder {
     *           public static final MyOrderGod INSTANCE = new MyOrderGod();
     *
     * @Override public SpellStats getSpellStats(String id) {
     *           return switch (id.toLowerCase()) {
     *           // God version buffs the cooldown and the damage percent
     *           case "my_combat" -> new SpellStats(100, "Deals 40% max hp (god).",
     *           7, true);
     *           default -> super.getSpellStats(id); // inherit unchanged spells
     *           };
     *           }
     *           }
     *
     *           REGISTRATION (OrderRegistry.java static initializer)
     *           ─────────────────────────────────────────────────────
     *           static {
     *           register(MyOrder.INSTANCE); // adds order to the registry map
     *           }
     *           // Then at startup, OrderRegistry.registerAllSpells() calls
     *           // each order's registerSpells() method.
     */

    // ═══════════════════════════════════════════════════════════════════════
    // 2. SPELLS
    // ═══════════════════════════════════════════════════════════════════════
    /**
     * OVERVIEW
     * ─────────
     * A Spell is a player-activatable ability that goes through three states:
     * ready → in use → cooldown
     *
     * Players bind a spell to a hotbar slot with /bind <slot> <spellId>.
     * While that slot is selected, the action bar shows the spell name + state.
     * Shift + right-click activates the spell.
     *
     * SPELL DAMAGE
     * ─────────────
     * All spell damage MUST be dealt via Utils.dealSpellDamage(attacker, victim,
     * percentOfMaxHp). This bypasses armor and scales to max HP.
     * Example: 30% damage to an Iron Golem (50 hp) = 15 hp damage.
     *
     * // In SpellRegistry impl method:
     * Utils.dealSpellDamage(player, target, stats.getInt(1) / 100.0);
     * // stats.getInt(1) = 30 → 30% of victim's max HP
     *
     * SpellStats RECORD
     * ──────────────────
     * new SpellStats(
     * cooldownTicks, // int: 20 ticks = 1 second
     * description, // String shown in the ascension menu
     * extraArgs... // Object varargs; order is defined by each spell
     * )
     *
     * Accessing extra args inside a spell executor:
     * stats.getInt(0) // cast extra[0] to int
     * stats.getBool(2) // cast extra[2] to boolean
     *
     * REGISTRATION FLOW (done inside Order.registerSpells())
     * ────────────────────────────────────────────────────────
     * SpellCooldownManager.register(
     * new Spell(
     * "spell_id", // unique lowercase ID
     * MyOrder.INSTANCE, // which order owns this spell
     * "utility", // slot type: "passive" | "utility" | "combat"
     * (player, stats) -> {
     * // executor: runs when the player activates the spell
     * SpellRegistry.mySpellImpl(player, stats.getInt(0), stats.getBool(1));
     * }
     * )
     * );
     * // SpellCooldownManager.register also forwards to SpellRegistry.register.
     *
     * ACTUAL SPELL IMPLEMENTATION (lives in SpellRegistry.java)
     * ───────────────────────────────────────────────────────────
     * public static void magmaBubble(ServerPlayer p, int radius,
     * float damagePercent, boolean launch) {
     * // 1. Check pre-conditions
     * BlockPos center = p.getOnPos();
     * boolean onSolid = ...;
     * if (!onSolid) {
     * p.sendSystemMessage(Component.literal("§cMust be on land or in lava!"));
     * return; // abort — SpellCooldownManager will not put spell on cooldown
     * }
     * // 2. Spawn the VFX animation
     * new MagmaBubble.spawnMagmaSpikes(p, 12, radius, 1.4f);
     * // 3. Deal damage after a delay (so animation plays first)
     * Ascension.scheduler.schedule(new DelayedTask(10, () -> {
     * for (LivingEntity target : getTargetsInRadius(p, radius)) {
     * Utils.dealSpellDamage(p, target, damagePercent / 100.0);
     * }
     * }));
     * }
     *
     * COOLDOWN ACTIVATION
     * ────────────────────
     * SpellCooldownManager.activate(player, spellId) is called by the
     * /bind command handler after the spell executor returns. The spell then
     * enters "in use" state and transitions to "cooldown" when the registered
     * duration elapses. Use ActiveSpell to track in-use state if the spell
     * has a duration (e.g. toggle spells manage their own state flag).
     *
     * BINDING COMMANDS (src/main/java/freq/ascension/commands/)
     * ───────────────────────────────────────────────────────────
     * /bind <slot 1-9> <spellId> → data.bind(slot, spellId)
     * /unbind <spellId> → data.unbind(spellId)
     * Switching abilities auto-calls data.unbindAll().
     */

    // ═══════════════════════════════════════════════════════════════════════
    // 3. ABILITIES & MIXIN PATTERN
    // ═══════════════════════════════════════════════════════════════════════
    /**
     * OVERVIEW
     * ─────────
     * Mixins intercept game events and MUST route through AbilityManager
     * instead of checking for equipped orders directly.
     *
     * AbilityManager.broadcast(player, action)
     * ─────────────────────────────────────────
     * Calls action.accept(order) for each uniquely equipped order (passive /
     * utility / combat). Deduplicates so an order equipped in two slots only
     * fires once.
     *
     * // Inside a mixin @Inject:
     * if (player instanceof ServerPlayer sp) {
     * AbilityManager.broadcast(sp, order -> order.onJump(sp));
     * }
     *
     * AbilityManager.anyMatch(player, predicate)
     * ────────────────────────────────────────────
     * Returns true if ANY equipped order satisfies the predicate.
     *
     * boolean canFly = AbilityManager.anyMatch(sp,
     * order -> order.isDoubleJumpEnabled());
     *
     * MIXIN SKELETON
     * ───────────────
     * 
     * @Mixin(LivingEntity.class)
     *                            public abstract class MyMixin {
     * @Inject(method = "jump", at = @At("HEAD"))
     *                private void onJump(CallbackInfo ci) {
     *                if ((Object) this instanceof ServerPlayer sp) {
     *                AbilityManager.broadcast(sp, order -> order.onJump(sp));
     *                }
     *                }
     *                }
     *
     *                MIXIN RETURNING / CANCELLING
     *                ─────────────────────────────
     * @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
     *                private void onHurt(DamageSource src, float amount,
     *                CallbackInfoReturnable<Boolean> cir) {
     *                if ((Object) this instanceof ServerPlayer sp) {
     *                Order.DamageContext ctx = new Order.DamageContext(src,
     *                amount);
     *                AbilityManager.broadcast(sp, order ->
     *                order.onEntityDamaged(sp, null, ctx));
     *                if (ctx.isCancelled()) cir.setReturnValue(false);
     *                }
     *                }
     *
     *                FABRIC API EVENTS (no mixin needed — registered in
     *                AbilityManager.init())
     *                ───────────────────────────────────────────────────────────────────────
     *                PlayerBlockBreakEvents.BEFORE.register((world, player, pos,
     *                state, entity) -> {
     *                if (player instanceof ServerPlayer sp && world instanceof
     *                ServerLevel sl)
     *                AbilityManager.broadcast(sp, o -> o.onBlockBreak(sp, sl, pos,
     *                state, entity));
     *                return true;
     *                });
     *
     *                AttackBlockCallback.EVENT.register((player, world, hand, pos,
     *                dir) -> {
     *                if (player instanceof ServerPlayer sp && world instanceof
     *                ServerLevel sl)
     *                AbilityManager.broadcast(sp, o -> o.onBlockDamage(sp, sl, pos,
     *                player.getItemInHand(hand)));
     *                return InteractionResult.PASS;
     *                });
     *
     *                SKIP MODIFICATION FLAG
     *                ───────────────────────
     *                Some order implementations call vanilla methods internally
     *                (e.g., Earth
     *                breaks nearby blocks for supermine). To prevent those
     *                recursive calls
     *                from re-triggering other mixins:
     *
     *                AbilityManager.skipNextModification();
     *                // ... call vanilla block-break code ...
     *                // Next mixin call to shouldSkipModification() returns true
     *                once, then resets.
     *
     *                MIXIN REGISTRATION (ascension.mixins.json)
     *                ────────────────────────────────────────────
     *                After creating a new mixin class in
     *                src/main/java/freq/ascension/mixin/,
     *                add its simple class name to the "mixins" array in
     *                src/main/resources/ascension.mixins.json:
     *                { "mixins": [ "MyMixin", ... ] }
     */

    // ═══════════════════════════════════════════════════════════════════════
    // 4. TASK SCHEDULER
    // ═══════════════════════════════════════════════════════════════════════
    /**
     * OVERVIEW
     * ─────────
     * Ascension.scheduler is a TaskScheduler (api/TaskScheduler.java) ticked
     * every server tick via ServerTickEvents.END_SERVER_TICK. All timed logic
     * should use the three task types below.
     *
     * ContinuousTask — runs forever at fixed intervals until manually stopped.
     * ─────────────────────────────────────────────────────────────────────
     * // Fires every 40 ticks (2 seconds) indefinitely.
     * ContinuousTask task = new ContinuousTask(40, () -> {
     * for (ServerPlayer p : server.getPlayerList().getPlayers()) {
     * if (AbilityManager.anyMatch(p, o -> o instanceof Ocean)) {
     * p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, false,
     * false));
     * }
     * }
     * });
     * Ascension.scheduler.schedule(task);
     * // Stop it later:
     * task.stop();
     *
     * DelayedTask — runs once after N ticks.
     * ───────────────────────────────────────
     * // Wait 10 ticks (0.5 s) then deal damage.
     * Ascension.scheduler.schedule(new DelayedTask(10, () -> {
     * Utils.dealSpellDamage(player, target, 0.30);
     * }));
     *
     * RepeatedTask — runs every tick for a duration window, with an optional delay.
     * ─────────────────────────────────────────────────────────────────────────────
     * // RepeatedTask(startAfterTicks, durationTicks, Consumer<RepeatedTask>)
     * // Fires every tick from tick 5 to tick 25 (20 ticks = 1 second duration).
     * Ascension.scheduler.schedule(new RepeatedTask(5, 20, t -> {
     * long elapsed = t.getTick(); // ticks since the window started
     * float progress = elapsed / 20.0f;
     * // e.g. slide a block display or apply a per-tick effect
     * target.hurt(damageSource, 1.0f);
     * if (someCondition) t.cancel(); // cancel early
     * }));
     * // Optional finish callback:
     * RepeatedTask t = new RepeatedTask(0, 60, task -> { ... });
     * t.setOnFinish(() -> player.sendSystemMessage(Component.literal("Done!")));
     * Ascension.scheduler.schedule(t);
     */

    // ═══════════════════════════════════════════════════════════════════════
    // 5. ANIMATION LIBRARY (VFXBuilder + GeometrySource)
    // ═══════════════════════════════════════════════════════════════════════
    /**
     * OVERVIEW
     * ─────────
     * All visual effects use vanilla block displays (no resource pack).
     * VFXBuilder wraps a BlockDisplay entity and chains Keyframe objects that
     * are processed by Ascension.scheduler (VFXBuilder implements Task).
     *
     * CONSTRUCTOR
     * ────────────
     * new VFXBuilder(
     * Level level, // server level
     * Vector3f worldPos, // entity spawn position
     * BlockState block, // which block to render
     * Transformation initial // starting transform (use VFXBuilder.instant)
     * )
     * // The entity is IMMEDIATELY spawned at construction time.
     * // The task is AUTOMATICALLY scheduled in the constructor.
     *
     * KEYFRAME METHODS
     * ─────────────────
     * Non-sticky (addKeyframe) — null is NOT allowed; every component is explicit.
     * Sticky (addKeyframeS) — null means "keep the value from the last keyframe".
     *
     * .addKeyframe(pos, rot, scale, durationTicks)
     * .addKeyframe(pos, rot, scale, durationTicks, delayTicks)
     * .addKeyframeS(pos, rot, scale, durationTicks) // null = inherit
     * .addKeyframeS(pos, rot, scale, durationTicks, delayTicks)
     *
     * .withAction(Runnable) // appended to the LAST added keyframe; fires on start
     *
     * FULL EXAMPLE — magma spike (from MagmaBubble.java)
     * ─────────────────────────────────────────────────────
     * Vector3f spikeDir = new Vector3f(0.1f, 0.5f, -0.1f).normalize();
     * float thickness = 0.4f;
     *
     * new VFXBuilder(level, worldPos,
     * Blocks.MAGMA_BLOCK.defaultBlockState(),
     * // Initial transform: flat (height = 0), aligned along spikeDir
     * VFXBuilder.instant(new Vector3f(0),
     * GeometrySource.faceVector(spikeDir),
     * new Vector3f(thickness, 0f, thickness)))
     * // Keyframe 1: grow over 4 ticks (after 3-tick delay)
     * .addKeyframeS(null, null, new Vector3f(thickness, 1.4f, thickness), 4, 3)
     * .withAction(() -> {
     * // Particles fired at the start of keyframe 1
     * if (level instanceof ServerLevel sl) {
     * sl.sendParticles(ParticleTypes.FLAME,
     * worldPos.x, worldPos.y + 0.2, worldPos.z,
     * 3, 0.12, 0.25, 0.12, 0.01);
     * }
     * })
     * // Keyframe 2: sink back down after 20-tick pause
     * .addKeyframeS(null, null, new Vector3f(thickness, 0f, thickness), 10, 20);
     * // Entity auto-removes when all keyframes finish.
     *
     * VFXBuilder.instant(translation, rotation, scale)
     * ──────────────────────────────────────────────────
     * // Convenience: builds a Transformation with no interpolation time.
     * VFXBuilder.instant(new Vector3f(0,0,0), new Quaternionf(), new
     * Vector3f(1,1,1))
     *
     * GEOMETRY SOURCE HELPERS
     * ────────────────────────
     * GeometrySource.faceVector(Vector3f direction)
     * // Returns a Quaternionf that rotates the +Y axis to face 'direction'.
     * // Used to orient spikes, beams, or directional displays.
     *
     * GeometrySource.sphere(Vector3f center, float radius, boolean edgeOnly)
     * // Random point on or inside a sphere.
     *
     * GeometrySource.circle(Vector3f center, Vector3f normal, float radius, boolean
     * hollow)
     * // Random point on or inside a circle in 3D space.
     *
     * GeometrySource.randomRot()
     * // Uniform random rotation quaternion.
     *
     * DESIGN GUIDELINES FOR NEW ANIMATIONS
     * ──────────────────────────────────────
     * • Each animation lives in its own class under freq.ascension.animation/.
     * • Expose parameters (particle count, size, radius, colors) as method args,
     * not hard-coded constants, so spells can tune the look easily.
     * • Spawn particles inside .withAction() callbacks at the correct keyframe.
     * • For multi-entity effects (e.g. rings of spikes), create N VFXBuilders in
     * a loop; each manages its own lifecycle.
     * • Use sticky keyframes when you want to animate only one axis while keeping
     * others steady — avoids specifying all three every time.
     */

    // ═══════════════════════════════════════════════════════════════════════
    // 6. PERSISTENT DATA MANAGER
    // ═══════════════════════════════════════════════════════════════════════
    /**
     * OVERVIEW
     * ─────────
     * Player data is stored in Minecraft's NBT-backed persistent storage via a
     * mixin on ServerPlayer (ServerPlayerMixin.java). The public API is the
     * AscensionData interface (managers/AscensionData.java).
     *
     * ACCESS PATTERN
     * ───────────────
     * ServerPlayer implements AscensionData via the mixin. Cast directly:
     *
     * AscensionData data = (AscensionData) player;
     *
     * AVAILABLE FIELDS & METHODS
     * ────────────────────────────
     * // Rank: "demigod" | "god"
     * String rank = data.getRank();
     * data.setRank("god");
     *
     * // Influence (vote / reputation currency)
     * int inf = data.getInfluence();
     * data.setInfluence(10);
     * data.addInfluence(3); // addInfluence(-2) to subtract
     *
     * // Equipped orders
     * Order passive = data.getPassive(); // may be null
     * Order utility = data.getUtility();
     * Order combat = data.getCombat();
     * data.setPassive("earth"); // uses OrderRegistry.get internally
     * data.setUtility("sky");
     * data.setCombat("ocean");
     * List<Order> allEquipped = data.getEquippedOrders(); // [passive, utility,
     * combat]
     *
     * // Unlocked abilities per order
     * AscensionData.OrderUnlock unlock = data.getUnlockedOrder("earth");
     * unlock.hasPassive() // true if passive slot is unlocked
     * unlock.hasUtility()
     * unlock.hasCombat()
     * unlock.isFullyUnlocked()
     * data.unlock("earth", "passive"); // "passive" | "utility" | "combat"
     *
     * // Spell bindings {hotbarSlot (1-9) → spellId}
     * Map<Integer, String> bindings = data.getSpellBindings();
     * data.bind(3, "magma_bubble");
     * data.unbind("magma_bubble"); // remove by spell id
     * data.unbindAll(); // called when switching abilities
     *
     * // God order (the order a player has "ascended" to if rank == "god")
     * String godOrder = data.getGodOrder(); // e.g. "earth"
     * data.setGodOrder("sky");
     *
     * // Full unlock map (all orders)
     * Map<String, AscensionData.OrderUnlock> allUnlocks = data.getUnlocked();
     *
     * SAVING
     * ───────
     * All setters immediately update the underlying NBT via ServerPlayerMixin.
     * Data is auto-saved with the world. No explicit flush is required.
     */

    // ═══════════════════════════════════════════════════════════════════════
    // 7. ASCENSION MENU (sgui)
    // ═══════════════════════════════════════════════════════════════════════
    /**
     * EXTERNAL LIBRARY: eu.pb4:sgui (https://github.com/Patbox/sgui)
     * ────────────────────────────────────────────────────────────────
     * sgui provides a BookGui that opens a written-book UI for the player
     * entirely server-side. Each page is a Filterable<Component>.
     *
     * MENU ENTRY POINT
     * ─────────────────
     * new AscensionMenu().open(player); // opens on page 1
     * new AscensionMenu().open(player, pageNum); // opens at a specific page
     *
     * PAGE BUILDING PATTERN (from AscensionMenu.java)
     * ─────────────────────────────────────────────────
     * List<Filterable<Component>> pages = new ArrayList<>();
     *
     * // Build a page as a MutableComponent tree
     * MutableComponent page = Component.empty()
     * .append(Component.literal("Title").withStyle(ChatFormatting.DARK_PURPLE))
     * .append(Component.literal("\n"))
     * .append(Component.literal("Body text").withStyle(ChatFormatting.BLACK));
     *
     * pages.add(Filterable.passThrough(page)); // add the page
     *
     * CLICK EVENTS (navigate to another page)
     * ─────────────────────────────────────────
     * MutableComponent link = Component.literal("[Orders]")
     * .withStyle(Style.EMPTY
     * .withClickEvent(new ClickEvent(
     * ClickEvent.Action.RUN_COMMAND, "/ascension page 3"))
     * .withHoverEvent(new HoverEvent(
     * HoverEvent.Action.SHOW_TEXT,
     * Component.literal("Click to view Orders"))));
     *
     * HOVER EVENTS
     * ─────────────
     * Style hover = Style.EMPTY.withHoverEvent(new HoverEvent(
     * HoverEvent.Action.SHOW_TEXT,
     * Component.literal("§7Some tooltip text")));
     *
     * OPENING THE BOOK GUI
     * ─────────────────────
     * // Construct a WrittenBookContent from the pages list, then set it on
     * // an ItemStack of WRITTEN_BOOK, then use BookGui:
     * BookGui gui = new BookGui(player, pages);
     * gui.open();
     *
     * // In practice, AscensionMenu assembles pages into WrittenBookContent
     * // and calls player.openBook(bookStack) for compatibility. See
     * // AscensionMenu.java lines ~180–280 for the full implementation.
     *
     * COLOR HELPERS
     * ──────────────
     * Component.literal("text").withColor(order.getOrderColor().getValue())
     * // withColor(int rgb) is used in book GUIs; ChatFormatting only supports
     * // 16 colors so prefer withColor for order-branded text.
     *
     * ADDING A NEW ORDER PAGE
     * ────────────────────────
     * Each order gets its own page(s). The flow in AscensionMenu is:
     * 1. For each Order in OrderRegistry.iterable():
     * a. Build a header with order icon + name + color.
     * b. Show passive / utility / combat descriptions from order.getDescription().
     * c. Show the player's unlock state for each slot.
     * d. Add click events to equip or view spells.
     * 2. Track page indices in a map so the main page's order icons can link to
     * the correct page number.
     */

    // ═══════════════════════════════════════════════════════════════════════
    // 8. UTILS
    // ═══════════════════════════════════════════════════════════════════════
    /**
     * Static helpers in Utils.java (freq.ascension.Utils).
     *
     * SPELL DAMAGE (always use this for spell effects)
     * ──────────────────────────────────────────────────
     * Utils.dealSpellDamage(ServerPlayer attacker, LivingEntity victim, double
     * fraction)
     * // fraction = 0.30 → 30% of victim's maxHealth
     * // Damage bypasses armor, uses the custom "spell" damage type.
     * // Example: 30% to a 20hp player = 6 hp; to a 100hp Warden = 30 hp.
     *
     * TEXT FORMATTING
     * ────────────────
     * Utils.smallCaps(String text)
     * // Converts ASCII letters to Unicode small-caps for display in book menus.
     *
     * RAYCASTING / LINE-OF-SIGHT
     * ───────────────────────────
     * Utils.raycast(ServerLevel, Vec3 start, Vec3 end, ClipContext.Block,
     * ClipContext.Fluid)
     * // Returns a BlockHitResult. Use HitResult.Type.MISS to check no obstruction.
     *
     * NEARBY ENTITY COLLECTION
     * ─────────────────────────
     * List<LivingEntity> targets = Utils.getEntitiesInRadius(
     * ServerLevel level, Vec3 center, double radius, ServerPlayer exclude);
     * // Returns all LivingEntity instances within radius, excluding 'exclude'.
     *
     * ADDING NEW HELPERS
     * ───────────────────
     * Add self-contained, stateless static methods to Utils.java. Use Javadoc
     * comments so that agents can discover them via grep.
     */

    // ═══════════════════════════════════════════════════════════════════════
    // 9. CONFIG (night-config)
    // ═══════════════════════════════════════════════════════════════════════
    /**
     * EXTERNAL LIBRARY: com.electronwill.night-config
     * https://github.com/TheElectronWill/night-config
     * Import: com.electronwill.nightconfig.core.file.CommentedFileConfig
     *
     * PATTERN (from Config.java)
     * ───────────────────────────
     * public class Config {
     * private static final String CONFIG_PATH = "ascension-config.toml";
     *
     * // 1. Declare the static field with its default value.
     * public static int myCoolValue = 42;
     * public static boolean myFlag = true;
     *
     * public static void load() {
     * CommentedFileConfig config = CommentedFileConfig.builder(CONFIG_PATH)
     * .sync().build();
     * config.load(); // reads existing file (creates empty if absent)
     *
     * // 2. Read with getOrElse(key, default) — writes default if missing.
     * myCoolValue = config.getOrElse("section.my_cool_value", 42);
     * myFlag = config.getOrElse("my_flag", true);
     *
     * // 3. Write the value back (ensures key is present in the file).
     * config.set("section.my_cool_value", myCoolValue);
     * config.set("my_flag", myFlag);
     *
     * // 4. Attach human-readable comments (written as TOML comments).
     * config.setComment("section.my_cool_value",
     * "Controls how cool the value is (in seconds).");
     * config.setComment("my_flag",
     * "Set to false to disable the feature entirely.");
     *
     * config.save(); // flush to disk
     * }
     * }
     *
     * TOML KEY PATHS
     * ───────────────
     * Dots in keys produce TOML sections:
     * config.set("abilities.earth", true);
     * // Produces in the TOML file:
     * // [abilities]
     * // earth = true
     *
     * USING CONFIG VALUES IN GAME CODE
     * ──────────────────────────────────
     * Reference the public static fields directly — no need to call load() again.
     * if (Config.earthEnabled) { ... }
     * int cooldown = Config.godDeathCooldown; // already an int, no parsing
     *
     * ADDING A NEW CONFIG ENTRY — CHECKLIST
     * ───────────────────────────────────────
     * 1. Declare public static <type> myKey = <default>; in Config.java
     * 2. Read myKey = config.getOrElse("toml.path", <default>);
     * 3. Write config.set("toml.path", myKey);
     * 4. Comment config.setComment("toml.path", "Explain what it does.");
     * 5. Save config.save(); (already called at end of load())
     * 6. Reference Config.myKey wherever you need it in game code.
     */
}
