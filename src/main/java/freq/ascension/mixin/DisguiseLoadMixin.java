package freq.ascension.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Guards against a DisguiseLib NPE that occurs when a ServerPlayer logs in
 * while their saved data still contains a {@code disguiselib$profile} entry.
 *
 * <p>
 * <b>Root cause chain:</b>
 * <ol>
 * <li>{@code PrepareSpawnTask.spawn()} calls {@code Entity.load(ValueInput)}
 * to restore player state from disk.</li>
 * <li>DisguiseLib's {@code fromTag} injection (in {@code EntityMixin_Disguise})
 * reads the saved profile and calls
 * {@code Entity.setGameProfile(GameProfile)}.</li>
 * <li>{@code setGameProfile} calls {@code disguiselib$sendProfileUpdates()},
 * which
 * constructs a {@code ClientboundPlayerInfoUpdatePacket} using DisguiseLib's
 * internal {@code serverPlayer} reference — a field that is only populated
 * when the entity is tracked by the chunk-loading manager.</li>
 * <li>Because the player has not entered the PLAY phase yet, that reference is
 * {@code null}, causing a {@link NullPointerException}.</li>
 * </ol>
 *
 * <p>
 * <b>Why this Mixin is necessary:</b> No Fabric API hook fires between
 * {@code Entity.load()} and DisguiseLib's {@code fromTag} injection.
 * The
 * {@link net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents#JOIN}
 * event fires too late. The only safe interception point is the
 * {@code setGameProfile} method itself, which DisguiseLib adds to
 * {@code Entity}
 * via its own Mixin. Using {@code remap = false} prevents the compile-time
 * Mixin annotation processor from rejecting the target (the method does not
 * exist on {@code Entity} at compile time; it is present at runtime after
 * DisguiseLib's Mixin has been applied).
 *
 * <p>
 * <b>Prevention:</b> The DISCONNECT handler in {@link freq.ascension.Ascension}
 * now calls {@code removeDisguise()} (not
 * {@code disguiseAs(EntityType.PLAYER)}),
 * which clears all DisguiseLib NBT fields before the player data is saved.
 * This Mixin is a belt-and-suspenders guard for data that was saved before that
 * fix was deployed, and for any edge cases where the lifecycle ordering races.
 *
 * <p>
 * <b>Why no Fabric API event exists:</b> There is no pre-join entity-load event
 * that provides access to the partially-loaded ServerPlayer entity before the
 * DisguiseLib injection runs; hence, a Mixin is the only viable option.
 * 
 * <p>
 * <b>Compile warnings:</b> The Mixin annotation processor emits warnings about
 * "Cannot find target method" for both {@code setGameProfile} and
 * {@code disguiselib$constructFakePlayer}. These warnings are expected and safe
 * to ignore — the methods are added by DisguiseLib at runtime via its own Mixins
 * and do not exist at compile time. The {@code require = 0} parameter on both
 * {@code @Inject} annotations ensures the game will not crash if DisguiseLib
 * changes its implementation in future versions.
 */
@Mixin(value = Entity.class, priority = 1100)
public abstract class DisguiseLoadMixin {

    /**
     * Cancels {@code setGameProfile} when invoked on a {@link ServerPlayer}
     * whose network connection has not yet been established OR if the profile is
     * null/malformed.
     *
     * <p>
     * During {@code PrepareSpawnTask.spawn()}, {@code ServerPlayer.connection}
     * is {@code null}. Carpet fake players ({@code EntityPlayerMPFake}) restore
     * NBT — including {@code disguiselib$profile} — before the server reference
     * is populated, so {@code getServer()} can return {@code null} even when
     * {@code connection} is non-null. Both conditions are therefore checked.
     * Either being {@code null} means the player is not yet in the PLAY phase
     * and must not trigger any network broadcasts.
     *
     * <p>
     * Additionally, this guards against null or malformed GameProfiles that would
     * cause DisguiseLib to crash when constructing fake players.
     *
     * <p>
     * {@code remap = false}: the target method ({@code setGameProfile}) is
     * added to {@code Entity} by DisguiseLib's Mixin and does not exist in the
     * vanilla class at compile time. Remapping is intentionally skipped.
     * 
     * <p>
     * {@code require = 0}: This method is added by DisguiseLib at runtime and will not
     * be found at compile time. The compile warning is expected and safe to ignore.
     */
    @SuppressWarnings("InvalidInjectorMethodSignature")
    @Inject(method = "setGameProfile(Lcom/mojang/authlib/GameProfile;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ascension$guardSetGameProfileDuringLoad(GameProfile profile, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayer sp))
            return;

        // Guard #1: Block setGameProfile when the server or connection is null.
        // Carpet fake players (EntityPlayerMPFake) restore NBT — including
        // disguiselib$profile — via loadPlayerData() AFTER onPlayerConnect(), so
        // their connection is non-null. However, their level's server reference
        // may not be fully initialized yet. Either condition being null means it
        // is not safe for DisguiseLib's sendProfileUpdates() to broadcast packets.
        //
        // Note: ServerPlayer.server is a private final field with no public
        // accessor in Mojmap 1.21.10; we reach it via level().getServer() which
        // delegates to the same server reference through the ServerLevel.
        if (sp.connection == null
                || sp.level() == null
                || sp.level().getServer() == null) {
            ci.cancel();
            return;
        }

        // Guard #2: Block setGameProfile if the profile itself is null or malformed
        // This prevents DisguiseLib from trying to construct a fake player with null
        // GameProfile
        if (profile == null || profile.name() == null || profile.id() == null) {
            ci.cancel();
            freq.ascension.Ascension.LOGGER.warn(
                    "Blocked attempt to set null/malformed GameProfile on player: " + sp.getName().getString());
        }
    }

    /**
     * Guards against DisguiseLib's {@code constructFakePlayer} being called with a
     * null or malformed GameProfile. This is the direct cause of the crash at
     * {@code Player.<init>:208} — DisguiseLib reads a null profile from saved NBT
     * and passes it straight into {@code new ServerPlayer(...)}, which immediately
     * dereferences it.
     *
     * <p>
     * {@code constructFakePlayer} is a {@code void} method; cancelling it prevents
     * the {@code ServerPlayer} constructor from being invoked entirely, so no NPE
     * occurs. DisguiseLib leaves {@code disguiselib$disguiseEntity} null in this
     * path, which the rest of its code handles safely.
     *
     * <p>
     * {@code remap = false}: {@code disguiselib$constructFakePlayer} is added to
     * {@code Entity} by DisguiseLib's own Mixin and does not exist in the vanilla
     * class at compile time.
     * 
     * <p>
     * {@code require = 0}: This method is added by DisguiseLib at runtime and will not
     * be found at compile time. The compile warning is expected and safe to ignore.
     */
    @SuppressWarnings("InvalidInjectorMethodSignature")
    @Inject(method = "disguiselib$constructFakePlayer(Lcom/mojang/authlib/GameProfile;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ascension$guardConstructFakePlayer(GameProfile profile, CallbackInfo ci) {
        if (profile == null || profile.id() == null || profile.name() == null) {
            freq.ascension.Ascension.LOGGER.warn(
                    "Blocked DisguiseLib constructFakePlayer with null/malformed GameProfile on entity: "
                            + ((Entity) (Object) this).getName().getString());
            ci.cancel();
        }
    }
}
