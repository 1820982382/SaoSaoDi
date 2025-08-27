package com.saosaodi;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

// 手动清理指令类：支持 /saosaodi clean [范围] 和 /saosaodi reload
public class CleanCommand {
    // 指令注册入口
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // 根指令：/saosaodi
        dispatcher.register(literal("saosaodi")
                // 子指令1：手动清理 /saosaodi clean（可选指定范围）
                .then(literal("clean")
                        // 权限要求：管理员权限（4级，与 /op 同级别，可根据需求调整）
                        .requires(source -> source.hasPermissionLevel(4))
                        // 可选参数：清理范围（方块数，0=全玩家区域）
                        .then(argument("range", IntegerArgumentType.integer(0))
                                .executes(ctx -> executeCleanWithRange(ctx, IntegerArgumentType.getInteger(ctx, "range"))))
                        // 无参数：使用配置中的默认范围
                        .executes(ctx -> executeCleanDefault(ctx)))
                // 子指令2：重载配置 /saosaodi reload
                .then(literal("reload")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(ctx -> executeReloadConfig(ctx)))
                // 子指令3：查看帮助 /saosaodi help
                .then(literal("help")
                        .executes(ctx -> executeHelp(ctx)))
        );
    }

    // 1. 执行默认范围清理（使用配置中的 cleanRange）
    private static int executeCleanDefault(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        // 获取全局清理管理器实例（从主类中获取，需先在 SaoSaoDi 类中添加静态 getter）
        LootCleanerManager cleaner = SaoSaoDi.getCleanerManager();

        if (cleaner == null) {
            source.sendError(Text.literal("掉落物清理管理器未启动！").formatted(Formatting.RED));
            return 0;
        }

        // 调用手动清理方法（需在 LootCleanerManager 中新增手动清理接口）
        cleaner.manualCleanup(SaoSaoDi.getConfig().getCleanRange());
        source.sendFeedback(() -> Text.literal("已手动触发掉落物清理（使用配置默认范围）！").formatted(Formatting.GREEN), true);
        return 1;
    }

    // 2. 执行指定范围清理（玩家输入自定义范围）
    private static int executeCleanWithRange(CommandContext<ServerCommandSource> ctx, int customRange) {
        ServerCommandSource source = ctx.getSource();
        LootCleanerManager cleaner = SaoSaoDi.getCleanerManager();

        if (cleaner == null) {
            source.sendError(Text.literal("掉落物清理管理器未启动！").formatted(Formatting.RED));
            return 0;
        }

        cleaner.manualCleanup(customRange);
        source.sendFeedback(() -> Text.literal("已手动触发掉落物清理（范围：" + customRange + " 方块）！").formatted(Formatting.GREEN), true);
        return 1;
    }

    // 3. 重载配置文件
    private static int executeReloadConfig(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        try {
            SaoSaoDi.reloadConfig(); // 需在 SaoSaoDi 类中实现配置重载逻辑
            source.sendFeedback(() -> Text.literal("配置文件已成功重载！").formatted(Formatting.GREEN), true);
        } catch (Exception e) {
            source.sendError(Text.literal("配置重载失败：" + e.getMessage()).formatted(Formatting.RED));
            SaoSaoDi.LOGGER.error("配置重载异常", e);
            return 0;
        }
        return 1;
    }

    // 4. 查看指令帮助
    private static int executeHelp(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        source.sendFeedback(() -> Text.literal("=== SaoSaoDi 手动指令帮助 ===").formatted(Formatting.YELLOW), false);
        source.sendFeedback(() -> Text.literal("/saosaodi clean - 使用配置默认范围清理掉落物").formatted(Formatting.WHITE), false);
        source.sendFeedback(() -> Text.literal("/saosaodi clean [范围] - 自定义范围清理（0=全玩家区域）").formatted(Formatting.WHITE), false);
        source.sendFeedback(() -> Text.literal("/saosaodi reload - 重载配置文件").formatted(Formatting.WHITE), false);
        source.sendFeedback(() -> Text.literal("/saosaodi help - 查看帮助信息").formatted(Formatting.WHITE), false);
        return 1;
    }
}