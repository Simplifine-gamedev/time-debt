package com.orca.timedebt;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class TimeDebtMod implements ModInitializer {
    public static final String MOD_ID = "timedebt";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Map<UUID, Integer> playerDebt = new HashMap<>();
    private static final Map<UUID, Long> lastDayTracked = new HashMap<>();
    private static final Map<UUID, Boolean> wasAwakeLastDay = new HashMap<>();

    private static final Identifier MINING_SPEED_MODIFIER_ID = Identifier.of(MOD_ID, "debt_mining_penalty");
    private static final Random random = new Random();

    @Override
    public void onInitialize() {
        LOGGER.info("Time Debt mod initialized!");

        // Register sleep event
        EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> {
            if (entity instanceof ServerPlayerEntity player) {
                ServerWorld world = player.getServerWorld();
                // Check if it's now day (meaning they slept through the night)
                if (world.isDay()) {
                    addDebt(player, 1);
                    player.sendMessage(Text.literal("You skipped the night... Time Debt increased to " + getDebt(player)), true);
                    LOGGER.info("Player {} accumulated time debt: now at {}", player.getName().getString(), getDebt(player));
                }
            }
        });

        // Register server tick for day tracking and effects
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                ServerWorld world = player.getServerWorld();
                long currentDay = world.getTimeOfDay() / 24000L;
                UUID playerId = player.getUuid();

                // Track day transitions for debt reduction
                if (!lastDayTracked.containsKey(playerId)) {
                    lastDayTracked.put(playerId, currentDay);
                    wasAwakeLastDay.put(playerId, true);
                }

                long lastDay = lastDayTracked.get(playerId);
                if (currentDay > lastDay) {
                    // A new day has begun
                    Boolean wasAwake = wasAwakeLastDay.getOrDefault(playerId, false);
                    if (wasAwake && getDebt(player) > 0) {
                        // Player stayed awake through the full cycle, reduce debt
                        addDebt(player, -1);
                        player.sendMessage(Text.literal("You stayed awake through the cycle. Time Debt decreased to " + getDebt(player)), true);
                    }
                    lastDayTracked.put(playerId, currentDay);
                    wasAwakeLastDay.put(playerId, true);
                }

                // Apply debt effects
                int debt = getDebt(player);

                // At 3+ debt: Mining speed reduced 20%
                applyMiningPenalty(player, debt);

                // At 5+ debt: Spawn shadow mobs during day
                if (debt >= 5 && world.isDay() && random.nextInt(6000) == 0) {
                    spawnShadowMob(player, world);
                }

                // At 7+ debt: Apply darkness/blindness effect for vignette-like experience
                if (debt >= 7) {
                    if (!player.hasStatusEffect(StatusEffects.DARKNESS)) {
                        player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0, false, false, true));
                    }
                }
            }
        });

        // Register /timedebt command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
    }

    private void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("timedebt")
            .executes(this::showDebt));
    }

    private int showDebt(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (source.getPlayer() != null) {
            ServerPlayerEntity player = source.getPlayer();
            int debt = getDebt(player);

            StringBuilder message = new StringBuilder();
            message.append("=== Time Debt Status ===\n");
            message.append("Current Debt: ").append(debt).append("\n");

            if (debt >= 3) {
                message.append("- Mining speed reduced by 20%\n");
            }
            if (debt >= 5) {
                message.append("- Shadow mobs may spawn during day\n");
            }
            if (debt >= 7) {
                message.append("- Vision obscured by darkness\n");
            }
            if (debt < 3) {
                message.append("No negative effects active.\n");
            }
            message.append("Stay awake through a full day-night cycle to reduce debt.");

            player.sendMessage(Text.literal(message.toString()), false);
        }
        return 1;
    }

    public static int getDebt(ServerPlayerEntity player) {
        return playerDebt.getOrDefault(player.getUuid(), 0);
    }

    public static void addDebt(ServerPlayerEntity player, int amount) {
        int current = getDebt(player);
        int newDebt = Math.max(0, current + amount);
        playerDebt.put(player.getUuid(), newDebt);

        // Mark that player slept if adding debt (they didn't stay awake)
        if (amount > 0) {
            wasAwakeLastDay.put(player.getUuid(), false);
        }
    }

    public static void setDebt(ServerPlayerEntity player, int amount) {
        playerDebt.put(player.getUuid(), Math.max(0, amount));
    }

    private void applyMiningPenalty(ServerPlayerEntity player, int debt) {
        EntityAttributeInstance attribute = player.getAttributeInstance(EntityAttributes.PLAYER_BLOCK_BREAK_SPEED);
        if (attribute == null) return;

        // Remove existing modifier
        attribute.removeModifier(MINING_SPEED_MODIFIER_ID);

        // Apply penalty if debt >= 3
        if (debt >= 3) {
            EntityAttributeModifier modifier = new EntityAttributeModifier(
                MINING_SPEED_MODIFIER_ID,
                -0.20, // 20% reduction
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            );
            attribute.addTemporaryModifier(modifier);
        }
    }

    private void spawnShadowMob(ServerPlayerEntity player, ServerWorld world) {
        BlockPos playerPos = player.getBlockPos();

        // Spawn phantom near player (shadow mob)
        int offsetX = random.nextInt(20) - 10;
        int offsetZ = random.nextInt(20) - 10;
        BlockPos spawnPos = playerPos.add(offsetX, 10, offsetZ);

        PhantomEntity phantom = EntityType.PHANTOM.create(world);
        if (phantom != null) {
            phantom.refreshPositionAndAngles(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), random.nextFloat() * 360, 0);
            phantom.setPhantomSize(1);
            world.spawnEntity(phantom);
            player.sendMessage(Text.literal("A shadow from your debt manifests..."), true);
            LOGGER.info("Spawned shadow phantom for player {} with debt {}", player.getName().getString(), getDebt(player));
        }
    }
}
