package me.qmftm.quantumPhobia.command;

import me.qmftm.quantumPhobia.gui.PhobiaListMenu;
import me.qmftm.quantumPhobia.insanity.InsanityManager;
import me.qmftm.quantumPhobia.insanity.PlayerStat;
import me.qmftm.quantumPhobia.insanity.WillpowerManager;
import me.qmftm.quantumPhobia.phobia.ActivePhobia;
import me.qmftm.quantumPhobia.phobia.PhobiaManager;
import me.qmftm.quantumPhobia.phobia.PhobiaType;
import me.qmftm.quantumPhobia.util.DurationParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public final class QuantumPhobiaCommand implements CommandExecutor, TabCompleter {

    private static final Component PREFIX = Component.text("[QuantumPhobia] ", NamedTextColor.GRAY);

    private final PhobiaManager manager;
    private final PhobiaListMenu listMenu;
    private final InsanityManager insanity;
    private final WillpowerManager willpower;

    public QuantumPhobiaCommand(PhobiaManager manager, PhobiaListMenu listMenu,
                                InsanityManager insanity, WillpowerManager willpower) {
        this.manager = manager;
        this.listMenu = listMenu;
        this.insanity = insanity;
        this.willpower = willpower;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("list")) {
            if (sender instanceof Player player) {
                listMenu.open(player);
            } else {
                String types = Arrays.stream(PhobiaType.values())
                        .map(type -> type.displayName() + "(" + type.name().toLowerCase(Locale.ROOT) + ")")
                        .collect(Collectors.joining(", "));
                send(sender, Component.text("사용 가능한 공포증: " + types, NamedTextColor.WHITE));
            }
            return true;
        }

        if (!sub.equals("give") && !sub.equals("remove") && !sub.equals("info")
                && !sub.equals("insanity") && !sub.equals("willpower")) {
            sendUsage(sender, label);
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender, label);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(sender, Component.text("온라인 상태의 플레이어 '" + args[1] + "'를 찾을 수 없습니다.", NamedTextColor.RED));
            return true;
        }

        if (sub.equals("remove")) {
            boolean removed = manager.remove(target.getUniqueId());
            if (removed) {
                send(sender, Component.text(target.getName() + "의 공포증을 제거했습니다.", NamedTextColor.WHITE));
                send(target, Component.text("당신의 공포증이 제거되었습니다.", NamedTextColor.WHITE));
            } else {
                send(sender, Component.text(target.getName() + "에게는 적용된 공포증이 없습니다.", NamedTextColor.WHITE));
            }
            return true;
        }

        if (sub.equals("insanity") || sub.equals("willpower")) {
            handleStat(sender, label, sub, sub.equals("insanity") ? insanity : willpower, target, args);
            return true;
        }

        if (sub.equals("info")) {
            Optional<ActivePhobia> current = manager.get(target.getUniqueId());
            if (current.isEmpty()) {
                send(sender, Component.text(target.getName() + "에게는 적용된 공포증이 없습니다.", NamedTextColor.WHITE));
            } else {
                ActivePhobia phobia = current.get();
                String remaining = phobia.isPermanent() ? "영구" : formatSeconds(phobia.remainingMillis() / 1000L);
                send(sender, Component.text(target.getName() + " -> " + phobia.type().displayName()
                        + " (남은 시간: " + remaining + ")", NamedTextColor.WHITE));
            }
            return true;
        }

        if (args.length < 3) {
            send(sender, Component.text("공포증을 입력하세요. (사용 가능: /" + label + " list)", NamedTextColor.RED));
            return true;
        }

        Optional<PhobiaType> phobiaType = PhobiaType.fromId(args[2]);
        if (phobiaType.isEmpty()) {
            send(sender, Component.text("알 수 없는 공포증입니다: " + args[2] + " (사용 가능: /" + label + " list)", NamedTextColor.RED));
            return true;
        }
        PhobiaType type = phobiaType.get();

        if (args.length < 4) {
            manager.setPermanent(target.getUniqueId(), type);
            send(sender, Component.text(target.getName() + "에게 " + type.displayName()
                    + "을(를) 영구적으로 부여했습니다.", NamedTextColor.WHITE));
        } else {
            Optional<Long> durationMillis = DurationParser.parseMillis(args[3]);
            if (durationMillis.isEmpty()) {
                send(sender, Component.text("지속 시간 형식이 잘못되었습니다. 예: 10m, 1h, 30s", NamedTextColor.RED));
                return true;
            }
            manager.set(target.getUniqueId(), type, durationMillis.get());
            send(sender, Component.text(target.getName() + "에게 " + type.displayName()
                    + "을(를) " + formatSeconds(durationMillis.get() / 1000L) + " 동안 부여했습니다.", NamedTextColor.WHITE));
        }
        // The target isn't told which phobia it is; that's what the diagnosis kit is for.
        send(target, Component.text("무언가에 대한 두려움이 생겨났습니다.", NamedTextColor.WHITE));
        return true;
    }

    /** Admin view and edit of a 0-100 stat; the target is not told anything. */
    private void handleStat(CommandSender sender, String label, String sub, PlayerStat stat, Player target, String[] args) {
        if (args.length == 2) {
            send(sender, Component.text(target.getName() + "의 " + stat.displayName() + ": " + stat.get(target)
                    + "/" + PlayerStat.MAX, NamedTextColor.WHITE));
            return;
        }

        String action = args[2].toLowerCase(Locale.ROOT);
        if ((!action.equals("set") && !action.equals("add")) || args.length < 4) {
            send(sender, Component.text("사용법: /" + label + " " + sub + " <player> [set|add <값>]", NamedTextColor.RED));
            return;
        }

        int value;
        try {
            value = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            send(sender, Component.text("숫자를 입력하세요: " + args[3], NamedTextColor.RED));
            return;
        }

        int result = action.equals("set") ? stat.set(target, value) : stat.add(target, value);
        send(sender, Component.text(target.getName() + "의 " + stat.displayName() + "을(를) " + result
                + "(으)로 설정했습니다.", NamedTextColor.WHITE));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("give", "remove", "info", "insanity", "willpower", "list"), args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean targetsPlayer = sub.equals("give") || sub.equals("remove") || sub.equals("info")
                || sub.equals("insanity") || sub.equals("willpower");

        if (args.length == 2 && targetsPlayer) {
            List<String> options = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
            return filter(options, args[1]);
        }

        if (args.length == 3 && sub.equals("give")) {
            List<String> options = new ArrayList<>();
            for (PhobiaType type : PhobiaType.values()) {
                options.add(type.name().toLowerCase(Locale.ROOT));
            }
            return filter(options, args[2]);
        }

        if (args.length == 4 && sub.equals("give")) {
            return filter(List.of("30s", "10m", "1h", "1d"), args[3]);
        }

        if (args.length == 3 && (sub.equals("insanity") || sub.equals("willpower"))) {
            return filter(List.of("set", "add"), args[2]);
        }

        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }

    private String formatSeconds(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        StringBuilder builder = new StringBuilder();
        if (hours > 0) {
            builder.append(hours).append("시간 ");
        }
        if (minutes > 0) {
            builder.append(minutes).append("분 ");
        }
        if (seconds > 0 || builder.isEmpty()) {
            builder.append(seconds).append("초");
        }
        return builder.toString().trim();
    }

    private void sendUsage(CommandSender sender, String label) {
        send(sender, Component.text("사용법:", NamedTextColor.WHITE));
        sender.sendMessage(Component.text(" /" + label + " give <player> <phobia> [duration] - 공포증 부여 (생략하면 영구)", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" /" + label + " remove <player> - 공포증 제거", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" /" + label + " info <player> - 현재 공포증 확인", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" /" + label + " insanity <player> [set|add <값>] - 정신병 수치 확인/설정 (0~100)", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" /" + label + " willpower <player> [set|add <값>] - 정신력 확인/설정 (0~100)", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" /" + label + " list - 공포증 목록 보기", NamedTextColor.GRAY));
    }

    private void send(CommandSender sender, Component message) {
        sender.sendMessage(PREFIX.append(message));
    }
}
