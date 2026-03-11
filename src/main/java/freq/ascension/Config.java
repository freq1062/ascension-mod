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

    // === EARTH ===
    public static int earthSupermineCD = 60;
    public static int earthMagmaBubbleCD = 10;
    public static int earthMagmaBubbleRange = 4;
    public static int earthMagmaBubbleDmg = 30;
    public static int earthGodMagmaBubbleCD = 900;
    public static int earthGodMagmaBubbleRange = 4;
    public static int earthGodMagmaBubbleDmg = 40;

    // === OCEAN ===
    public static int oceanDolphinsGraceCD = 60;
    public static int oceanMolecularFluxCD = 300;
    public static int oceanMolecularFluxRange = 20;
    public static int oceanMolecularFluxDuration = 5;
    public static int oceanDrownCD = 600;
    public static int oceanDrownDuration = 7;
    public static int oceanDrownRadius = 8;
    public static int oceanGodDolphinsGraceCD = 60;
    public static int oceanGodMolecularFluxCD = 300;
    public static int oceanGodMolecularFluxRange = 40;
    public static int oceanGodMolecularFluxDuration = 15;
    public static int oceanGodDrownCD = 600;
    public static int oceanGodDrownDuration = 10;
    public static int oceanGodDrownRadius = 12;

    // === SKY ===
    public static int skyDoubleJumpCD = 160;
    public static int skyDoubleJumpRange = 6;
    public static int skyDashCD = 225;
    public static int skyDashDistance = 9;
    public static int skyStarStrikeCD = 675;
    public static int skyGodDoubleJumpCD = 160;
    public static int skyGodDashCD = 225;
    public static int skyGodDashDistance = 12;
    public static int skyGodStarStrikeCD = 675;

    // === FLORA ===
    public static int floraThornsCD = 60;
    public static int floraGodThornsCD = 60;

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

        godDeathCooldown = config.getOrElse("god_death_cooldown", 86400);
        influenceBanDuration = config.getOrElse("influence_ban_duration", 86400);

        // Earth
        earthSupermineCD = config.getOrElse("earth.supermine.cooldown_ticks", 60);
        earthMagmaBubbleCD = config.getOrElse("earth.magma_bubble.cooldown_ticks", 10);
        earthMagmaBubbleRange = config.getOrElse("earth.magma_bubble.range", 4);
        earthMagmaBubbleDmg = config.getOrElse("earth.magma_bubble.damage", 30);
        earthGodMagmaBubbleCD = config.getOrElse("earth_god.magma_bubble.cooldown_ticks", 900);
        earthGodMagmaBubbleRange = config.getOrElse("earth_god.magma_bubble.range", 4);
        earthGodMagmaBubbleDmg = config.getOrElse("earth_god.magma_bubble.damage", 40);

        // Ocean
        oceanDolphinsGraceCD = config.getOrElse("ocean.dolphins_grace.cooldown_ticks", 60);
        oceanMolecularFluxCD = config.getOrElse("ocean.molecular_flux.cooldown_ticks", 300);
        oceanMolecularFluxRange = config.getOrElse("ocean.molecular_flux.range", 20);
        oceanMolecularFluxDuration = config.getOrElse("ocean.molecular_flux.duration_seconds", 5);
        oceanDrownCD = config.getOrElse("ocean.drown.cooldown_ticks", 600);
        oceanDrownDuration = config.getOrElse("ocean.drown.duration_seconds", 7);
        oceanDrownRadius = config.getOrElse("ocean.drown.radius", 8);
        oceanGodDolphinsGraceCD = config.getOrElse("ocean_god.dolphins_grace.cooldown_ticks", 60);
        oceanGodMolecularFluxCD = config.getOrElse("ocean_god.molecular_flux.cooldown_ticks", 300);
        oceanGodMolecularFluxRange = config.getOrElse("ocean_god.molecular_flux.range", 40);
        oceanGodMolecularFluxDuration = config.getOrElse("ocean_god.molecular_flux.duration_seconds", 15);
        oceanGodDrownCD = config.getOrElse("ocean_god.drown.cooldown_ticks", 600);
        oceanGodDrownDuration = config.getOrElse("ocean_god.drown.duration_seconds", 10);
        oceanGodDrownRadius = config.getOrElse("ocean_god.drown.radius", 12);

        // Sky
        skyDoubleJumpCD = config.getOrElse("sky.double_jump.cooldown_ticks", 160);
        skyDoubleJumpRange = config.getOrElse("sky.double_jump.range", 6);
        skyDashCD = config.getOrElse("sky.dash.cooldown_ticks", 225);
        skyDashDistance = config.getOrElse("sky.dash.distance", 9);
        skyStarStrikeCD = config.getOrElse("sky.star_strike.cooldown_ticks", 675);
        skyGodDoubleJumpCD = config.getOrElse("sky_god.double_jump.cooldown_ticks", 160);
        skyGodDashCD = config.getOrElse("sky_god.dash.cooldown_ticks", 225);
        skyGodDashDistance = config.getOrElse("sky_god.dash.distance", 12);
        skyGodStarStrikeCD = config.getOrElse("sky_god.star_strike.cooldown_ticks", 675);

        // Flora
        floraThornsCD = config.getOrElse("flora.thorns.cooldown_ticks", 60);
        floraGodThornsCD = config.getOrElse("flora_god.thorns.cooldown_ticks", 60);

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

        config.set("god_death_cooldown", godDeathCooldown);
        config.setComment("god_death_cooldown", "How long a player must wait after dying as a god (in seconds).");
        config.set("influence_ban_duration", influenceBanDuration);
        config.setComment("influence_ban_duration", "How long a player is banned when their influence drops below -5 (in seconds).");

        // Earth
        config.set("earth.supermine.cooldown_ticks", earthSupermineCD);
        config.set("earth.magma_bubble.cooldown_ticks", earthMagmaBubbleCD);
        config.set("earth.magma_bubble.range", earthMagmaBubbleRange);
        config.set("earth.magma_bubble.damage", earthMagmaBubbleDmg);
        config.set("earth_god.magma_bubble.cooldown_ticks", earthGodMagmaBubbleCD);
        config.set("earth_god.magma_bubble.range", earthGodMagmaBubbleRange);
        config.set("earth_god.magma_bubble.damage", earthGodMagmaBubbleDmg);

        // Ocean
        config.set("ocean.dolphins_grace.cooldown_ticks", oceanDolphinsGraceCD);
        config.set("ocean.molecular_flux.cooldown_ticks", oceanMolecularFluxCD);
        config.set("ocean.molecular_flux.range", oceanMolecularFluxRange);
        config.set("ocean.molecular_flux.duration_seconds", oceanMolecularFluxDuration);
        config.set("ocean.drown.cooldown_ticks", oceanDrownCD);
        config.set("ocean.drown.duration_seconds", oceanDrownDuration);
        config.set("ocean.drown.radius", oceanDrownRadius);
        config.set("ocean_god.dolphins_grace.cooldown_ticks", oceanGodDolphinsGraceCD);
        config.set("ocean_god.molecular_flux.cooldown_ticks", oceanGodMolecularFluxCD);
        config.set("ocean_god.molecular_flux.range", oceanGodMolecularFluxRange);
        config.set("ocean_god.molecular_flux.duration_seconds", oceanGodMolecularFluxDuration);
        config.set("ocean_god.drown.cooldown_ticks", oceanGodDrownCD);
        config.set("ocean_god.drown.duration_seconds", oceanGodDrownDuration);
        config.set("ocean_god.drown.radius", oceanGodDrownRadius);

        // Sky
        config.set("sky.double_jump.cooldown_ticks", skyDoubleJumpCD);
        config.set("sky.double_jump.range", skyDoubleJumpRange);
        config.set("sky.dash.cooldown_ticks", skyDashCD);
        config.set("sky.dash.distance", skyDashDistance);
        config.set("sky.star_strike.cooldown_ticks", skyStarStrikeCD);
        config.set("sky_god.double_jump.cooldown_ticks", skyGodDoubleJumpCD);
        config.set("sky_god.dash.cooldown_ticks", skyGodDashCD);
        config.set("sky_god.dash.distance", skyGodDashDistance);
        config.set("sky_god.star_strike.cooldown_ticks", skyGodStarStrikeCD);

        // Flora
        config.set("flora.thorns.cooldown_ticks", floraThornsCD);
        config.set("flora_god.thorns.cooldown_ticks", floraGodThornsCD);

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