package com.favasur.cavehorror.torch;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ModTorchConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue BURNOUT_CHANCE = BUILDER
            .comment("Chance for a lit torch to burn out each random tick. Higher value = torches burn out faster")
            .defineInRange("burnoutChance", 0.03, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue BURNED_TORCHES = BUILDER
            .comment("Whether torches leave behind a 'burned-out' variant or simply disappear.")
            .define("burnedTorches", true);

    public static final ModConfigSpec.BooleanValue CAN_RELIGHT = BUILDER
            .comment("Whether burnt-out torches can be reignited with Flint and Steel")
            .define("canRelight", true);

    public static final ModConfigSpec.BooleanValue PLACE_LIT = BUILDER
            .comment("Whether torches should automatically be lit when placed, like in vanilla")
            .define("placeLit", true);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
