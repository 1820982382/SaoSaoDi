package com.saosaodi;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback; // Fabric 指令API
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class SaoSaoDi implements ModInitializer {
    public static final String MOD_ID = "saosaodi";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static SaoSaoDiConfig config;
    private static LootCleanerManager cleanerManager; // 静态化，支持指令调用

    @Override
    public void onInitialize() {
        loadConfig();

        // 1. 注册指令（关键：Fabric 指令注册回调）
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CleanCommand.register(dispatcher);
        });

        // 2. 注册服务器生命周期事件
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        LOGGER.info("SaoSaoDi 掉落物清理模组已加载! 支持手动指令：/saosaodi help");
    }

    // 原有配置加载逻辑（不变）
    private void loadConfig() {
        File configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "SaosaodiConfig.json");
        try {
            if (!configFile.exists()) {
                // 如果配置文件不存在，创建默认配置并保存
                config = new SaoSaoDiConfig();
                config.save(configFile); // 保存默认配置
                LOGGER.info("已创建默认配置文件: {}", configFile.getAbsolutePath());
            } else {
                // 如果配置文件存在，加载配置
                config = SaoSaoDiConfig.load(configFile);
            }
        } catch (IOException e) {
            LOGGER.error("配置文件处理失败，使用默认配置", e);
            config = new SaoSaoDiConfig();
            try {
                // 即使出错也尝试保存默认配置
                config.save(configFile);
            } catch (IOException ex) {
                LOGGER.error("保存默认配置文件失败", ex);
            }
        }
    }


    // 新增：配置重载方法（供指令调用）
    public static void reloadConfig() throws IOException {
        File configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "SaosaodiConfig.json");
        config = SaoSaoDiConfig.load(configFile); // 重新加载配置文件
        LOGGER.info("配置文件已重载");
    }

    // 服务器启动：初始化清理管理器（不变）
    private void onServerStarted(MinecraftServer server) {
        cleanerManager = new LootCleanerManager(server);
        cleanerManager.start();
        LOGGER.info("掉落物清理管理器已启动");
    }

    // 服务器停止：停止管理器（不变）
    private void onServerStopping(MinecraftServer server) {
        if (cleanerManager != null) {
            cleanerManager.stop();
            LOGGER.info("掉落物清理管理器已停止");
        }
    }

    // 新增：清理管理器静态 getter（供指令类调用）
    public static LootCleanerManager getCleanerManager() {
        return cleanerManager;
    }

    public static SaoSaoDiConfig getConfig() {
        return config;
    }
}