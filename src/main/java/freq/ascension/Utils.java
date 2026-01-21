package freq.ascension;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import freq.ascension.managers.AscensionData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;

/**
 * General utility methods.
 */
public class Utils {

    public static String smallCaps(String input) {
        if (input == null)
            return "";
        String lower = input.toLowerCase();
        StringBuilder sb = new StringBuilder();
        String map = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
        for (char c : lower.toCharArray()) {
            if (c >= 'a' && c <= 'z') {
                sb.append(map.charAt(c - 'a'));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String unsmallCaps(String input) {
        if (input == null)
            return "";

        StringBuilder sb = new StringBuilder();
        String map = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

        for (char c : input.toCharArray()) {
            int idx = map.indexOf(c);
            if (idx >= 0 && idx < 26) {
                sb.append((char) ('a' + idx));
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    // Custom damage type for spells, bypasses armor
    public static DamageSource spellDmgType(Level world, Entity attacker) {
        Holder<DamageType> damageTypeHolder = world.registryAccess()
                .getOrThrow(ResourceKey.create(
                        Registries.DAMAGE_TYPE,
                        ResourceLocation.fromNamespaceAndPath("ascension", "spell")));
        return new DamageSource(damageTypeHolder, attacker);
    }

    @SuppressWarnings("deprecation")
    public static void spellDmg(Entity target, Entity attacker, float percent) {
        if (target instanceof net.minecraft.world.entity.LivingEntity livingTarget) {
            float damageAmount = livingTarget.getMaxHealth() * percent;
            DamageSource source = spellDmgType(target.level(), attacker);
            livingTarget.hurt(source, damageAmount);
        }
    }

    public static boolean isGod(ServerPlayer player) {
        AscensionData data = (AscensionData) player;
        return data.getGodOrder() != null;
    }

    public static boolean isBreakable(BlockState state, ServerLevel level, BlockPos pos) {
        // 1. Check if it is Air (Modern air check)
        if (state.isAir())
            return false;

        // 2. Check if it is a Liquid
        if (!state.getFluidState().isEmpty())
            return false;

        // 3. Check the Hardness (The "Golden Rule" for Breakability)
        // getDestroySpeed returns -1.0f for Bedrock, Barrier, etc.
        float hardness = state.getDestroySpeed(level, pos);

        return hardness != -1.0f;
    }

    public static void applyTexture(ItemStack skull, String textureString) {
        if (skull.getItem() instanceof net.minecraft.world.item.PlayerHeadItem) {
            try {
                // Create a GameProfile with a random UUID
                GameProfile profile = new GameProfile(UUID.randomUUID(),
                        "OrderHead");

                // Add the texture property
                profile.properties().put("textures",
                        new com.mojang.authlib.properties.Property("textures", textureString));

                skull.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Parse a duration string like "24h", "1d", "30m", "3600s" into milliseconds.
     * Format: <number><unit> where unit is s (seconds), m (minutes), h (hours), or
     * d (days)
     * 
     * @param duration The duration string to parse
     * @return The duration in milliseconds, or 24 hours (86400000ms) if parsing
     *         fails
     */
    public static long parseDuration(String duration) {
        if (duration == null || duration.isEmpty())
            return 24L * 60L * 60L * 1000L; // Default to 24 hours

        duration = duration.trim().toLowerCase(java.util.Locale.ROOT);

        // Extract the numeric part and unit
        int i = 0;
        while (i < duration.length() && (Character.isDigit(duration.charAt(i)) || duration.charAt(i) == '.')) {
            i++;
        }

        if (i == 0)
            return 24L * 60L * 60L * 1000L; // No number found, use default

        try {
            long value = Long.parseLong(duration.substring(0, i));
            String unit = i < duration.length() ? duration.substring(i) : "h";

            return switch (unit) {
                case "s", "sec", "second", "seconds" -> value * 1000L;
                case "m", "min", "minute", "minutes" -> value * 60L * 1000L;
                case "h", "hr", "hour", "hours" -> value * 60L * 60L * 1000L;
                case "d", "day", "days" -> value * 24L * 60L * 60L * 1000L;
                default -> 24L * 60L * 60L * 1000L; // Unknown unit, use default
            };
        } catch (NumberFormatException e) {
            return 24L * 60L * 60L * 1000L; // Parse error, use default
        }
    }
}
