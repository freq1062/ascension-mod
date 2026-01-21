package freq.ascension;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;

public class Config {
    private static final String CONFIG_PATH = "ascension-config.toml";

    // These are the actual variables you use in your game code
    public static boolean spellsDamageTeammates = false;
    public static boolean earthEnabled = true;
    public static boolean skyEnabled = true;
    public static boolean oceanEnabled = true;
    public static boolean floraEnabled = true;
    public static boolean magicEnabled = true;
    public static boolean netherEnabled = true;
    public static boolean endEnabled = true;

    public static boolean earthGodEnabled = true;
    public static boolean skyGodEnabled = true;
    public static boolean oceanGodEnabled = true;
    public static boolean floraGodEnabled = true;
    public static boolean magicGodEnabled = true;
    public static boolean netherGodEnabled = true;
    public static boolean endGodEnabled = true;

    public static int godDeathCooldown = 86400;
    public static int influenceBanDuration = 86400;

    public static void load() {
        CommentedFileConfig config = CommentedFileConfig.builder(CONFIG_PATH).sync().build();
        config.load();

        spellsDamageTeammates = config.getOrElse("spells_damage_teammates", false);
        earthEnabled = config.getOrElse("earth", true);
        skyEnabled = config.getOrElse("sky", true);
        oceanEnabled = config.getOrElse("ocean", true);
        floraEnabled = config.getOrElse("flora", true);
        magicEnabled = config.getOrElse("magic", true);
        netherEnabled = config.getOrElse("nether", true);
        endEnabled = config.getOrElse("end", true);

        earthGodEnabled = config.getOrElse("earth_god", true);
        skyGodEnabled = config.getOrElse("sky_god", true);
        oceanGodEnabled = config.getOrElse("ocean_god", true);
        floraGodEnabled = config.getOrElse("flora_god", true);
        magicGodEnabled = config.getOrElse("magic_god", true);
        netherGodEnabled = config.getOrElse("nether_god", true);
        endGodEnabled = config.getOrElse("end_god", true);

        godDeathCooldown = config.getOrElse("god_death_cooldown", 86400);
        influenceBanDuration = config.getOrElse("influence_ban_duration", 86400);

        // Save back with comments
        config.set("spells_damage_teammates", spellsDamageTeammates);
        config.setComment("spells_damage_teammates", "Whether spells deal damage to teammates.");

        config.set("abilities.earth", earthEnabled);
        config.set("abilities.sky", skyEnabled);
        config.set("abilities.ocean", oceanEnabled);
        config.set("abilities.flora", floraEnabled);
        config.set("abilities.magic", magicEnabled);
        config.set("abilities.nether", netherEnabled);
        config.set("abilities.end", endEnabled);
        config.setComment("abilities", "Toggle whether order abilities can be unlocked");

        config.set("ascend.earth_god", earthGodEnabled);
        config.set("ascend.sky_god", skyGodEnabled);
        config.set("ascend.ocean_god", oceanGodEnabled);
        config.set("ascend.flora_god", floraGodEnabled);
        config.set("ascend.magic_god", magicGodEnabled);
        config.set("ascend.nether_god", netherGodEnabled);
        config.set("ascend.end_god", endGodEnabled);
        config.setComment("abilities", "Toggle whether players can ascend to gods");

        config.set("god_death_cooldown", godDeathCooldown);
        config.setComment("god_death_cooldown", "How long a player must wait after dying as a god (in seconds).");
        config.set("influence_ban_duration", influenceBanDuration);
        config.setComment("influence_ban_duration", "How long a player is banned when their influence drops below -5.");

        config.save();
        config.close();
    }
}