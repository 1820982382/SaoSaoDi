package com.saosaodi;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.util.Formatting;
import net.minecraft.registry.Registries;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LootCleanerManager {
    private final MinecraftServer server;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private boolean isRunning = false;

    public LootCleanerManager(MinecraftServer server) {
        this.server = server;
    }

    public void start() {
        if (isRunning) {
            return;
        }

        isRunning = true;
        long interval = SaoSaoDi.getConfig().getCleanInterval();
        scheduler.scheduleAtFixedRate(this::scheduleCleanup, 0, interval, TimeUnit.MILLISECONDS);
    }

    private void scheduleCleanup() {
        long interval = SaoSaoDi.getConfig().getCleanInterval();

        if (interval > 60 * 1000) {
            scheduler.schedule(this::send60sWarning, interval - 60 * 1000, TimeUnit.MILLISECONDS);
        }

        if (interval > 10 * 1000) {
            scheduler.schedule(this::send10sWarning, interval - 10 * 1000, TimeUnit.MILLISECONDS);
        }

        scheduler.schedule(this::cleanupLoot, interval, TimeUnit.MILLISECONDS);
    }

    private void send60sWarning() {
        server.execute(() -> {
            String message = SaoSaoDi.getConfig().getWarningMessage60s();
            broadcastMessage(message);
        });
    }

    private void send10sWarning() {
        server.execute(() -> {
            String message = SaoSaoDi.getConfig().getWarningMessage10s();
            broadcastMessage(message);
        });
    }

    private void cleanupLoot() {
        server.execute(() -> {
            int totalRemoved = 0;
            int cleanRange = SaoSaoDi.getConfig().getCleanRange();

            for (ServerWorld world : server.getWorlds()) {
                List<ServerPlayerEntity> playersInWorld = world.getPlayers();
                if (playersInWorld.isEmpty()) {
                    continue;
                }

                Box playerArea = createPlayerAreaBounds(playersInWorld, cleanRange, world);
                List<ItemEntity> itemEntities = world.getEntitiesByClass(
                        ItemEntity.class,
                        playerArea,
                        entity -> true
                );

                for (ItemEntity entity : itemEntities) {
                    if (!isWhitelisted(entity)) {
                        entity.remove(Entity.RemovalReason.DISCARDED);
                        totalRemoved++;
                    }
                }
            }

            String message = String.format(SaoSaoDi.getConfig().getCleanCompleteMessage(), totalRemoved);
            broadcastMessage(message);
            SaoSaoDi.LOGGER.info("清理了 {} 个掉落物", totalRemoved);

        });
    }

    public void manualCleanup(int customRange) {
        server.execute(() -> {
            int totalRemoved = 0;

            for (ServerWorld world : server.getWorlds()) {
                List<ServerPlayerEntity> playersInWorld = world.getPlayers();
                if (playersInWorld.isEmpty()) {
                    continue;
                }

                Box playerArea = createPlayerAreaBounds(playersInWorld, customRange, world);
                List<ItemEntity> itemEntities = world.getEntitiesByClass(
                        ItemEntity.class,
                        playerArea,
                        entity -> true
                );

                for (ItemEntity entity : itemEntities) {
                    if (!isWhitelisted(entity)) {
                        entity.remove(Entity.RemovalReason.DISCARDED);
                        totalRemoved++;
                    }
                }
            }

            String message = String.format("[手动清理] 已清理 %d 个掉落物", totalRemoved);
            broadcastMessageToAdmins(message);
            SaoSaoDi.LOGGER.info(message);
        });
    }

    /**
     * 修复核心：通过 WorldBorder 获取所有边界值
     * 1.20.4 中所有世界边界值均需从 WorldBorder 对象获取，而非 ServerWorld
     */
    private Box createPlayerAreaBounds(List<ServerPlayerEntity> players, int range, ServerWorld world) {
        if (range <= 0) {
            // 获取世界边界对象
            WorldBorder worldBorder = world.getWorldBorder();

            // 从 WorldBorder 而非 ServerWorld 获取所有边界值
            return new Box(
                    worldBorder.getBoundWest(),   // 西边界（最小X）
                    world.getBottomY(),           // Y轴最小值（直接从世界获取）
                    worldBorder.getBoundNorth(),  // 北边界（最小Z）
                    worldBorder.getBoundEast(),   // 东边界（最大X）- 修复此处错误
                    world.getTopY(),              // Y轴最大值（直接从世界获取）
                    worldBorder.getBoundSouth()   // 南边界（最大Z）- 修复此处错误
            );
        }

        // 玩家范围清理逻辑（不变）
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE, maxZ = Double.MIN_VALUE;

        for (ServerPlayerEntity player : players) {
            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();

            minX = Math.min(minX, x - range);
            minY = Math.min(minY, y - range);
            minZ = Math.min(minZ, z - range);
            maxX = Math.max(maxX, x + range);
            maxY = Math.max(maxY, y + range);
            maxZ = Math.max(maxZ, z + range);
        }

        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private boolean isWhitelisted(ItemEntity item) {
        String itemId = Registries.ITEM.getId(item.getStack().getItem()).toString();
        return SaoSaoDi.getConfig().getWhitelistItems().contains(itemId);
    }

    private void broadcastMessageToAdmins(String message) {
        Text text = Text.literal("[SaoSaoDi] " + message).formatted(Formatting.AQUA);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.hasPermissionLevel(4)) {
                player.sendMessage(text, false);
            }
        }
    }

    private void broadcastMessage(String message) {
        Text text = Text.literal(message);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(text, false);
        }
    }

    public void stop() {
        isRunning = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}
