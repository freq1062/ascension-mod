package freq.ascension.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import freq.ascension.orders.*;

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

    public static boolean challengerTrialsEnabled = true;

    public static int godDeathCooldown = 86400;
    public static int influenceBanDuration = 86400;

    // === MAGIC ===
    public static int magicShapeshiftCD = 600;
    public static int magicShapeshiftDuration = 600;
    public static int magicGodShapeshiftCD = 600;
    public static int magicGodShapeshiftDuration = 900;

    // === NETHER ===
    public static int netherGhastCarryCD = 60;
    public static int netherSoulDrainCD = 60;
    public static int netherSoulDrainDuration = 200;
    public static int netherGodGhastCarryCD = 60;
    public static int netherGodSoulDrainCD = 60;
    public static int netherGodSoulDrainDuration = 300;
    public static int netherSoulRageCD = 90; // demigod soul rage cooldown (seconds)
    public static int netherSoulRageDuration = 20; // demigod soul rage duration (seconds)
    public static int netherSoulRageCDGod = 60; // god soul rage cooldown (seconds)
    public static int netherSoulRageDurationGod = 30; // god soul rage duration (seconds)

    // === END ===
    public static int endTeleportCD = 30;
    public static int endTeleportRange = 10;
    public static int endDesolationCD = 120;
    public static int endGodTeleportCD = 20;
    public static int endGodTeleportRange = 15;
    public static int endGodDesolationCD = 90;

    // === WEAPONS ===
    public static int gravitonGauntletCooldown = 400;
    public static double gravitonGauntletRange = 10.0;
    public static double prismWandRange = 64.0;
    public static double prismWandAngleDeg = 10.0;
    public static float prismWandDamageFraction = 0.10f;
    public static float prismWandBoltSpeed = 1.5f;
    public static int vinewrathAxeStunTicks = 60;
    public static int ruinousScytheHitsNeeded = 4;
    public static int hellfireCrossbowHitsBeforeFire = 3;

    public static void load() {
        CommentedFileConfig config = CommentedFileConfig.builder(CONFIG_PATH).sync().build();
        config.load();

        spellsDamageTeammates = config.getOrElse("spells_damage_teammates", false);
        earthEnabled = config.getOrElse("abilities.earth", true);
        skyEnabled = config.getOrElse("abilities.sky", true);
        oceanEnabled = config.getOrElse("abilities.ocean", true);
        floraEnabled = config.getOrElse("abilities.flora", true);
        magicEnabled = config.getOrElse("abilities.magic", true);
        netherEnabled = config.getOrElse("abilities.nether", true);
        endEnabled = config.getOrElse("abilities.end", true);

        earthGodEnabled = config.getOrElse("ascend.earth_god", true);
        skyGodEnabled = config.getOrElse("ascend.sky_god", true);
        oceanGodEnabled = config.getOrElse("ascend.ocean_god", true);
        floraGodEnabled = config.getOrElse("ascend.flora_god", true);
        magicGodEnabled = config.getOrElse("ascend.magic_god", true);
        netherGodEnabled = config.getOrElse("ascend.nether_god", true);
        endGodEnabled = config.getOrElse("ascend.end_god", true);

        challengerTrialsEnabled = config.getOrElse("challenger_trials_enabled", true);

        godDeathCooldown = config.getOrElse("god_death_cooldown", 86400);
        influenceBanDuration = config.getOrElse("influence_ban_duration", 86400);

        // Earth
        Earth.CONFIG_GROUP.load(config);
        EarthGod.CONFIG_GROUP.load(config);

        // Ocean
        Ocean.CONFIG_GROUP.load(config);
        OceanGod.CONFIG_GROUP.load(config);

        // Sky
        Sky.CONFIG_GROUP.load(config);
        SkyGod.CONFIG_GROUP.load(config);

        // Flora
        Flora.CONFIG_GROUP.load(config);
        FloraGod.CONFIG_GROUP.load(config);

        // Magic
        magicShapeshiftCD = config.getOrElse("magic.shapeshift.cooldown_ticks", 600);
        magicShapeshiftDuration = config.getOrElse("magic.shapeshift.duration_ticks", 600);
        magicGodShapeshiftCD = config.getOrElse("magic_god.shapeshift.cooldown_ticks", 600);
        magicGodShapeshiftDuration = config.getOrElse("magic_god.shapeshift.duration_ticks", 900);

        // Nether
        netherGhastCarryCD = config.getOrElse("nether.ghast_carry.cooldown_ticks", 60);
        netherSoulDrainCD = config.getOrElse("nether.soul_drain.cooldown_ticks", 60);
        netherSoulDrainDuration = config.getOrElse("nether.soul_drain.duration_ticks", 200);
        netherGodGhastCarryCD = config.getOrElse("nether_god.ghast_carry.cooldown_ticks", 60);
        netherGodSoulDrainCD = config.getOrElse("nether_god.soul_drain.cooldown_ticks", 60);
        netherGodSoulDrainDuration = config.getOrElse("nether_god.soul_drain.duration_ticks", 300);
        netherSoulRageCD = config.getOrElse("nether.soul_rage.cooldown_seconds", 90);
        netherSoulRageDuration = config.getOrElse("nether.soul_rage.duration_seconds", 20);
        netherSoulRageCDGod = config.getOrElse("nether_god.soul_rage.cooldown_seconds", 60);
        netherSoulRageDurationGod = config.getOrElse("nether_god.soul_rage.duration_seconds", 30);

        // End
        endTeleportCD = config.getOrElse("end.teleport.cooldown_ticks", 30);
        endTeleportRange = config.getOrElse("end.teleport.range", 10);
        endDesolationCD = config.getOrElse("end.desolation_of_time.cooldown_ticks", 120);
        endGodTeleportCD = config.getOrElse("end_god.teleport.cooldown_ticks", 20);
        endGodTeleportRange = config.getOrElse("end_god.teleport.range", 15);
        endGodDesolationCD = config.getOrElse("end_god.desolation_of_time.cooldown_ticks", 90);

        // Weapons
        gravitonGauntletCooldown = config.getOrElse("weapons.graviton_gauntlet.cooldown_ticks", 400);
        gravitonGauntletRange = config.getOrElse("weapons.graviton_gauntlet.range", 10.0);
        prismWandRange = config.getOrElse("weapons.prism_wand.range", 64.0);
        prismWandAngleDeg = config.getOrElse("weapons.prism_wand.angle_deg", 10.0);
        prismWandDamageFraction = config.<Double>getOrElse("weapons.prism_wand.damage_fraction", 0.10).floatValue();
        prismWandBoltSpeed = config.<Double>getOrElse("weapons.prism_wand.bolt_speed", 1.5).floatValue();
        vinewrathAxeStunTicks = config.getOrElse("weapons.vinewrath_axe.stun_ticks", 60);
        ruinousScytheHitsNeeded = config.getOrElse("weapons.ruinous_scythe.hits_needed", 4);
        hellfireCrossbowHitsBeforeFire = config.getOrElse("weapons.hellfire_crossbow.hits_before_fire", 3);

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

        config.set("challenger_trials_enabled", challengerTrialsEnabled);
        config.setComment("challenger_trials_enabled",
                "Whether Challenger Trials can be initiated. Players can still craft Challenger's Sigils, but right-clicking POIs will be blocked when this is false.");

        config.set("god_death_cooldown", godDeathCooldown);
        config.setComment("god_death_cooldown", "How long a player must wait after dying as a god (in seconds).");
        config.set("influence_ban_duration", influenceBanDuration);
        config.setComment("influence_ban_duration",
                "How long a player is banned when their influence drops below -5 (in seconds).");

        // Earth
        Ocean.CONFIG_GROUP.setAll(config);
        OceanGod.CONFIG_GROUP.setAll(config);

        // Ocean
        Ocean.CONFIG_GROUP.setAll(config);
        OceanGod.CONFIG_GROUP.setAll(config);

        // Sky
        Sky.CONFIG_GROUP.setAll(config);
        SkyGod.CONFIG_GROUP.setAll(config);

        // Flora
        Flora.CONFIG_GROUP.setAll(config);
        FloraGod.CONFIG_GROUP.setAll(config);

        // Magic
        config.set("magic.shapeshift.cooldown_ticks", magicShapeshiftCD);
        config.set("magic.shapeshift.duration_ticks", magicShapeshiftDuration);
        config.set("magic_god.shapeshift.cooldown_ticks", magicGodShapeshiftCD);
        config.set("magic_god.shapeshift.duration_ticks", magicGodShapeshiftDuration);

        // Nether
        config.set("nether.ghast_carry.cooldown_ticks", netherGhastCarryCD);
        config.set("nether.soul_drain.cooldown_ticks", netherSoulDrainCD);
        config.set("nether.soul_drain.duration_ticks", netherSoulDrainDuration);
        config.set("nether_god.ghast_carry.cooldown_ticks", netherGodGhastCarryCD);
        config.set("nether_god.soul_drain.cooldown_ticks", netherGodSoulDrainCD);
        config.set("nether_god.soul_drain.duration_ticks", netherGodSoulDrainDuration);
        config.set("nether.soul_rage.cooldown_seconds", netherSoulRageCD);
        config.setComment("nether.soul_rage.cooldown_seconds", "Soul Rage cooldown for demigod (seconds).");
        config.set("nether.soul_rage.duration_seconds", netherSoulRageDuration);
        config.setComment("nether.soul_rage.duration_seconds", "Soul Rage duration for demigod (seconds).");
        config.set("nether_god.soul_rage.cooldown_seconds", netherSoulRageCDGod);
        config.setComment("nether_god.soul_rage.cooldown_seconds", "Soul Rage cooldown for god (seconds).");
        config.set("nether_god.soul_rage.duration_seconds", netherSoulRageDurationGod);
        config.setComment("nether_god.soul_rage.duration_seconds", "Soul Rage duration for god (seconds).");

        // End
        config.set("end.teleport.cooldown_ticks", endTeleportCD);
        config.set("end.teleport.range", endTeleportRange);
        config.set("end.desolation_of_time.cooldown_ticks", endDesolationCD);
        config.set("end_god.teleport.cooldown_ticks", endGodTeleportCD);
        config.set("end_god.teleport.range", endGodTeleportRange);
        config.set("end_god.desolation_of_time.cooldown_ticks", endGodDesolationCD);

        // Weapons
        config.set("weapons.graviton_gauntlet.cooldown_ticks", gravitonGauntletCooldown);
        config.set("weapons.graviton_gauntlet.range", gravitonGauntletRange);
        config.set("weapons.prism_wand.range", prismWandRange);
        config.set("weapons.prism_wand.angle_deg", prismWandAngleDeg);
        config.set("weapons.prism_wand.damage_fraction", (double) prismWandDamageFraction);
        config.set("weapons.prism_wand.bolt_speed", (double) prismWandBoltSpeed);
        config.set("weapons.vinewrath_axe.stun_ticks", vinewrathAxeStunTicks);
        config.set("weapons.ruinous_scythe.hits_needed", ruinousScytheHitsNeeded);
        config.set("weapons.hellfire_crossbow.hits_before_fire", hellfireCrossbowHitsBeforeFire);
        config.setComment("weapons", "Configurable constants for mythic weapons.");

        config.save();
        config.close();
    }
}