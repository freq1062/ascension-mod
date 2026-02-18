package freq.ascension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import freq.ascension.managers.AscensionData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.LivingEntity;
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

    public static List<Component> wrapToComponents(String input) {
        return wrapToComponents(input, 45);
    }

    public static List<Component> wrapToComponents(String input, int maxVisibleCharsPerLine) {
        List<Component> out = new ArrayList<>();
        if (input == null || input.isEmpty())
            return out;

        int limit = Math.max(1, maxVisibleCharsPerLine);
        String normalized = input.replace("\r", "");

        for (String paragraph : normalized.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                out.add(Component.literal(""));
                continue;
            }

            String[] words = paragraph.trim().isEmpty() ? new String[0] : paragraph.trim().split("\\s+");
            StringBuilder line = new StringBuilder();

            for (String word : words) {
                if (line.length() == 0) {
                    if (visibleLength(word) <= limit) {
                        line.append(word);
                    } else {
                        splitLongToken(word, limit, out);
                    }
                    continue;
                }

                int newLen = visibleLength(line) + 1 + visibleLength(word);
                if (newLen <= limit) {
                    line.append(' ').append(word);
                } else {
                    out.add(Component.literal(line.toString()));
                    line.setLength(0);

                    if (visibleLength(word) <= limit) {
                        line.append(word);
                    } else {
                        splitLongToken(word, limit, out);
                    }
                }
            }

            if (line.length() > 0) {
                out.add(Component.literal(line.toString()));
            }
        }

        return out;
    }

    private static int visibleLength(CharSequence s) {
        if (s == null)
            return 0;

        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                i++; // skip formatting code character
                continue;
            }
            count++;
        }
        return count;
    }

    private static void splitLongToken(String token, int limit, List<Component> out) {
        if (token == null || token.isEmpty()) {
            out.add(Component.literal(""));
            return;
        }

        StringBuilder part = new StringBuilder();
        int visible = 0;

        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);

            if (c == '§' && i + 1 < token.length()) {
                part.append(c).append(token.charAt(i + 1));
                i++;
                continue;
            }

            part.append(c);
            visible++;

            if (visible >= limit) {
                out.add(Component.literal(part.toString()));
                part.setLength(0);
                visible = 0;
            }
        }

        if (part.length() > 0) {
            out.add(Component.literal(part.toString()));
        }
    }

    public static Component costComponent(int cost) {
        return Component.literal("Cost: " + cost + " influence").withStyle(style -> style
                .withColor(ChatFormatting.LIGHT_PURPLE)
                .withItalic(false));
    }

    // Custom damage type for spells, bypasses armor
    public static DamageSource spellDmgType(Level world, Entity attacker) {
        // 1. Create the ResourceKey
        ResourceKey<DamageType> key = ResourceKey.create(
                Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath("ascension", "spell"));

        // 2. Look it up in the world's registry
        // world.registryAccess() is the correct way to get dynamic data
        return world.registryAccess()
                .lookup(Registries.DAMAGE_TYPE)
                .flatMap(registry -> registry.get(key))
                .map(holder -> new DamageSource(holder, attacker))
                .orElseGet(() -> world.damageSources().magic()); // Fallback to vanilla magic if registry fails
    }

    @SuppressWarnings("deprecation")
    public static void spellDmg(Entity target, Entity attacker, float percent) {
        if (target instanceof LivingEntity livingTarget) {
            if (livingTarget.invulnerableTime > 0) {
                return;
            }
            float damageAmount = livingTarget.getMaxHealth() * (percent / 100);
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
