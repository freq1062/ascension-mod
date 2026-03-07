package freq.ascension.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.entity.Entity;

/**
 * Guards against a DisguiseLib NPE that occurs when a ServerPlayer logs in
 * while their saved data still contains a {@code disguiselib$profile} entry.
 *
 * <p><b>Root cause chain:</b>
 * <ol>
 *   <li>{@code PrepareSpawnTask.spawn()} calls {@code Entity.load(ValueInput)}
 *       to restore player state from disk.</li>
 *   <li>DisguiseLib's {@code fromTag} injection (in {@code EntityMixin_Disguise})
 *       reads the saved profile and calls {@code Entity.setGameProfile(GameProfile)}.</li>
 *   <li>{@code setGameProfile} calls {@code disguiselib$sendProfileUpdates()}, which
 *       constructs a {@code ClientboundPlayerInfoUpdatePacket} using DisguiseLib's
 *       internal {@code serverPlayer} reference — a field that is only populated
 *       when the entity is tracked by the chunk-loading manager.</li>
 *   <li>Because the player has not entered the PLAY phase yet, that reference is
 *       {@code null}, causing a {@link NullPointerException}.</li>
 * </ol>
 *
 * <p><b>Why this Mixin is necessary:</b> No Fabric API hook fires between
 * {@code Entity.load()} and DisguiseLib's {@code fromTag} injection.
 * The {@link net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents#JOIN}
 * event fires too late. The only safe interception point is the
 * {@code setGameProfile} method itself, which DisguiseLib adds to {@code Entity}
 * via its own Mixin. Using {@code remap = false} prevents the compile-time
 * Mixin annotation processor from rejecting the target (the method does not
 * exist on {@code Entity} at compile time; it is present at runtime after
 * DisguiseLib's Mixin has been applied).
 *
 * <p><b>Prevention:</b> The DISCONNECT handler in {@link freq.ascension.Ascension}
 * now calls {@code removeDisguise()} (not {@code disguiseAs(EntityType.PLAYER)}),
 * which clears all DisguiseLib NBT fields before the player data is saved.
 * This Mixin is a belt-and-suspenders guard for data that was saved before that
 * fix was deployed, and for any edge cases where the lifecycle ordering races.
 *
 * <p><b>Why no Fabric API event exists:</b> There is no pre-join entity-load event
 * that provides access to the partially-loaded ServerPlayer entity before the
 * DisguiseLib injection runs; hence, a Mixin is the only viable option.
 */
@Mixin(value = Entity.class, priority = 1100)
public abstract class DisguiseLoadMixin {

    /**
     * Cancels {@code setGameProfile} when invoked on a {@link ServerPlayer}
     * whose network connection has not yet been established.
     *
     * <p>During {@code PrepareSpawnTask.spawn()}, {@code ServerPlayer.connection}
     * is {@code null}. This is the reliable signal that the player entity is in
     * the entity-load phase and must not trigger any network broadcasts.
     *
     * <p>{@code remap = false}: the target method ({@code setGameProfile}) is
     * added to {@code Entity} by DisguiseLib's Mixin and does not exist in the
     * vanilla class at compile time. Remapping is intentionally skipped.
     */
    @Inject(method = "setGameProfile(Lcom/mojang/authlib/GameProfile;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void ascension$guardSetGameProfileDuringLoad(GameProfile profile, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayer sp)) return;
        // connection is null during PrepareSpawnTask.spawnPlayer() before the
        // player enters the PLAY network phase — safe signal to abort.
        if (sp.connection == null) {
            ci.cancel();
        }
    }
}
