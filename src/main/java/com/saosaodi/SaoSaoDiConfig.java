package com.saosaodi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SaoSaoDiConfig {
    // 默认配置参数
    private int cleanInterval = 300000; // 5分钟（毫秒）
    private int cleanRange = 128;       // 默认清理范围（方块）
    private String warningMessage60s = "§e掉落物将在60秒后自动清理，请及时拾取！";
    private String warningMessage10s = "§c掉落物将在10秒后自动清理，请注意！";
    private String cleanCompleteMessage = "§a已自动清理 %d 个掉落物";
    private List<String> whitelistItems = new ArrayList<>();

    // Gson 序列化工具
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    /**
     * 从文件加载配置
     */
    public static SaoSaoDiConfig load(File file) throws IOException {
        if (!file.exists()) {
            SaoSaoDi.LOGGER.info("配置文件 {} 不存在，将使用默认配置", file.getAbsolutePath());
            return new SaoSaoDiConfig();
        }

        try (FileReader reader = new FileReader(file,StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, SaoSaoDiConfig.class);
        } catch (Exception e) {
            SaoSaoDi.LOGGER.error("配置文件 {} 格式错误，使用默认配置", file.getAbsolutePath(), e);
            return new SaoSaoDiConfig();
        }
    }

    /**
     * 保存配置到文件（修复：校验目录创建结果）
     */
    public void save(File file) throws IOException {
        // 1. 获取配置文件的父目录（如 config/）
        File parentDir = file.getParentFile();

        SaoSaoDi.LOGGER.info("尝试保存配置文件到: {}", file.getAbsolutePath());

        // 2. 校验父目录是否存在，不存在则创建，并检查创建结果
        if (parentDir != null && !parentDir.exists()) {
            // 调用 mkdirs() 并判断结果，创建失败时抛异常提示
            boolean dirCreated = parentDir.mkdirs();
            if (!dirCreated) {
                throw new IOException("无法创建配置目录：" + parentDir.getAbsolutePath()
                        + "（可能是权限不足或路径非法）");
            }
            SaoSaoDi.LOGGER.info("配置目录 {} 不存在，已自动创建", parentDir.getAbsolutePath());
        }

        // 3. 写入配置文件
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
            SaoSaoDi.LOGGER.info("配置已成功保存到：{}", file.getAbsolutePath());
        }
    }

    // Getter 方法（不变）
    public int getCleanInterval() {
        return cleanInterval;
    }

    public int getCleanRange() {
        return cleanRange;
    }

    public String getWarningMessage60s() {
        return warningMessage60s;
    }

    public String getWarningMessage10s() {
        return warningMessage10s;
    }

    public String getCleanCompleteMessage() {
        return cleanCompleteMessage;
    }

    public List<String> getWhitelistItems() {
        return whitelistItems;
    }

    // Setter 方法（不变）
    public void setCleanInterval(int cleanInterval) {
        this.cleanInterval = Math.max(cleanInterval, 10000);
    }

    public void setCleanRange(int cleanRange) {
        this.cleanRange = Math.max(cleanRange, 0);
    }

    public void setWarningMessage60s(String warningMessage60s) {
        this.warningMessage60s = warningMessage60s == null ? "§e60秒后清理掉落物！" : warningMessage60s;
    }

    public void setWarningMessage10s(String warningMessage10s) {
        this.warningMessage10s = warningMessage10s == null ? "§c10秒后清理掉落物！" : warningMessage10s;
    }

    public void setCleanCompleteMessage(String cleanCompleteMessage) {
        if (cleanCompleteMessage == null || !cleanCompleteMessage.contains("%d")) {
            this.cleanCompleteMessage = "§a已清理 %d 个掉落物";
        } else {
            this.cleanCompleteMessage = cleanCompleteMessage;
        }
    }

    public void setWhitelistItems(List<String> whitelistItems) {
        this.whitelistItems = whitelistItems == null ? new ArrayList<>() : whitelistItems;
    }
}