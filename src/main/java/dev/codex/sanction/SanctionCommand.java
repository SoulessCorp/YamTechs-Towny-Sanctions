package dev.codex.sanction;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class SanctionCommand implements TabExecutor {
    private static final String ADMIN_PERMISSION = "sanction.admin";
    private static final String PREFIX = ChatColor.DARK_GRAY + "[" + ChatColor.RED + "Sanction"
            + ChatColor.DARK_GRAY + "] " + ChatColor.GRAY;
    private static final int PAGE_SIZE = 10;

    private final SanctionPlugin plugin;
    private final SanctionManager sanctions;

    public SanctionCommand(SanctionPlugin plugin, SanctionManager sanctions) {
        this.plugin = plugin;
        this.sanctions = sanctions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "impose" -> impose(sender, args);
            case "lift", "pardon" -> lift(sender, args);
            case "list" -> list(sender, args);
            case "status", "info" -> status(sender, args);
            case "clear" -> clear(sender);
            case "reload" -> reload(sender);
            default -> showHelp(sender);
        }
        return true;
    }

    private void impose(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(PREFIX + "Usage: /sanction impose <nation> <duration> [reason]");
            return;
        }

        int durationIndex = -1;
        DurationSpec duration = null;
        for (int i = 2; i < args.length; i++) {
            DurationSpec parsed = parseDuration(args[i]);
            if (parsed != null) {
                durationIndex = i;
                duration = parsed;
                break;
            }
        }
        if (durationIndex < 0 || durationIndex == 1) {
            sender.sendMessage(PREFIX + "Use a duration such as 30m, 12h, 7d, or permanent.");
            return;
        }

        Nation issuerNation = kingNation(sender);
        boolean admin = sender.hasPermission(ADMIN_PERMISSION);
        if (issuerNation == null && !admin) {
            sender.sendMessage(PREFIX + "Only a nation king or an admin can issue sanctions.");
            return;
        }

        String targetName = String.join(" ", Arrays.copyOfRange(args, 1, durationIndex));
        Nation target = TownyAPI.getInstance().getNation(targetName);
        if (target == null) {
            sender.sendMessage(PREFIX + "Towny nation not found: " + ChatColor.YELLOW + targetName);
            return;
        }
        if (issuerNation != null && issuerNation.getUUID().equals(target.getUUID())) {
            sender.sendMessage(PREFIX + "A nation cannot sanction itself.");
            return;
        }

        long expiresAt = -1L;
        if (!duration.permanent()) {
            try {
                expiresAt = Math.addExact(System.currentTimeMillis(), duration.millis());
            } catch (ArithmeticException exception) {
                sender.sendMessage(PREFIX + "That duration is too long.");
                return;
            }
        }

        String reason = durationIndex + 1 < args.length
                ? String.join(" ", Arrays.copyOfRange(args, durationIndex + 1, args.length))
                : "No reason given.";
        if (reason.isBlank()) {
            reason = "No reason given.";
        }

        String issuerName = issuerNation == null ? "Server" : issuerNation.getName();
        sanctions.add(target, issuerNation == null ? null : issuerNation.getUUID(), issuerName,
                sender.getName(), expiresAt, reason);

        String length = expiresAt < 0 ? "permanently" : "for " + formatRemaining(expiresAt);
        sender.sendMessage(PREFIX + "Trade embargo issued against " + ChatColor.GOLD + target.getName()
                + ChatColor.GRAY + " " + length + ". Reason: " + ChatColor.WHITE + reason);
    }

    private void lift(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "Usage: /sanction lift <nation>");
            return;
        }
        String targetName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Nation target = TownyAPI.getInstance().getNation(targetName);
        if (target == null) {
            sender.sendMessage(PREFIX + "Towny nation not found: " + ChatColor.YELLOW + targetName);
            return;
        }

        Nation issuerNation = kingNation(sender);
        boolean admin = sender.hasPermission(ADMIN_PERMISSION);
        if (issuerNation == null && !admin) {
            sender.sendMessage(PREFIX + "Only a nation king or an admin can lift sanctions.");
            return;
        }

        int removed = admin
                ? sanctions.removeAllForNation(target)
                : sanctions.removeFromNation(target, issuerNation.getUUID());
        if (removed == 0) {
            sender.sendMessage(PREFIX + (admin
                    ? "No active sanctions were found against " + target.getName() + "."
                    : "Your nation has no active sanction against " + target.getName() + "."));
        } else if (admin) {
            sender.sendMessage(PREFIX + "Lifted " + removed + " sanction(s) against "
                    + ChatColor.GOLD + target.getName() + ChatColor.GRAY + ".");
        } else {
            sender.sendMessage(PREFIX + "Your nation lifted its sanction against "
                    + ChatColor.GOLD + target.getName() + ChatColor.GRAY + ".");
        }
    }

    private void list(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length > 2) {
            sender.sendMessage(PREFIX + "Usage: /sanction list [page]");
            return;
        }
        if (args.length == 2) {
            try {
                page = Integer.parseInt(args[1]);
                if (page < 1) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException exception) {
                sender.sendMessage(PREFIX + "Page must be a positive number.");
                return;
            }
        }

        List<Sanction> active = sanctions.allActive();
        int pages = Math.max(1, (active.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > pages) {
            sender.sendMessage(PREFIX + "There are only " + pages + " page(s).");
            return;
        }
        sender.sendMessage(ChatColor.DARK_GRAY + "--- " + ChatColor.RED + "Active Sanctions"
                + ChatColor.GRAY + " (" + page + "/" + pages + ") " + ChatColor.DARK_GRAY + "---");
        if (active.isEmpty()) {
            sender.sendMessage(PREFIX + "No active sanctions.");
            return;
        }
        int start = (page - 1) * PAGE_SIZE;
        for (int i = start; i < Math.min(start + PAGE_SIZE, active.size()); i++) {
            Sanction sanction = active.get(i);
            sender.sendMessage(ChatColor.GOLD + sanction.getTargetNationName() + ChatColor.GRAY
                    + " ← " + ChatColor.YELLOW + sanction.getIssuerName() + ChatColor.GRAY
                    + " | " + formatExpiry(sanction) + " | " + truncate(sanction.getReason(), 70));
        }
        if (page < pages) {
            sender.sendMessage(ChatColor.GRAY + "Use /sanction list " + (page + 1) + " for the next page.");
        }
    }

    private void status(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "Usage: /sanction status <nation>");
            return;
        }
        String targetName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Nation target = TownyAPI.getInstance().getNation(targetName);
        if (target == null) {
            sender.sendMessage(PREFIX + "Towny nation not found: " + ChatColor.YELLOW + targetName);
            return;
        }
        List<Sanction> active = sanctions.forNation(target);
        sender.sendMessage(ChatColor.DARK_GRAY + "--- " + ChatColor.GOLD + target.getName()
                + ChatColor.GRAY + " sanction status " + ChatColor.DARK_GRAY + "---");
        if (active.isEmpty()) {
            sender.sendMessage(PREFIX + "This nation has no active sanctions.");
            return;
        }
        for (Sanction sanction : active) {
            sender.sendMessage(ChatColor.GRAY + "Issued by " + ChatColor.YELLOW + sanction.getIssuerName()
                    + ChatColor.GRAY + " (" + sanction.getImposedBy() + "), "
                    + formatExpiry(sanction) + ".");
            sender.sendMessage(ChatColor.GRAY + "Reason: " + ChatColor.WHITE + sanction.getReason());
        }
        sender.sendMessage(ChatColor.GRAY + "Embargo: villager shops, wandering traders, and piglin bartering.");
    }

    private void clear(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(PREFIX + "Only an admin can clear every sanction.");
            return;
        }
        int removed = sanctions.clear();
        sender.sendMessage(PREFIX + "Cleared " + removed + " active sanction(s).");
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(PREFIX + "Only an admin can reload this plugin.");
            return;
        }
        plugin.reloadConfig();
        sanctions.load();
        sender.sendMessage(PREFIX + "Configuration and sanctions reloaded.");
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_GRAY + "--- " + ChatColor.RED + "Sanction"
                + ChatColor.GRAY + " ---");
        sender.sendMessage(ChatColor.YELLOW + "/sanction impose <nation> <duration> [reason]"
                + ChatColor.GRAY + " - issue an embargo (30m, 12h, 7d, permanent)");
        sender.sendMessage(ChatColor.YELLOW + "/sanction lift <nation>"
                + ChatColor.GRAY + " - lift your nation's sanction; admins lift all");
        sender.sendMessage(ChatColor.YELLOW + "/sanction status <nation>"
                + ChatColor.GRAY + " - show sanctions against a nation");
        sender.sendMessage(ChatColor.YELLOW + "/sanction list [page]"
                + ChatColor.GRAY + " - list active sanctions");
        sender.sendMessage(ChatColor.YELLOW + "/sanction clear"
                + ChatColor.GRAY + " - admin: clear all sanctions");
        sender.sendMessage(ChatColor.YELLOW + "/sanction reload"
                + ChatColor.GRAY + " - admin: reload settings and saved sanctions");
        sender.sendMessage(ChatColor.GRAY + "Nation kings can sanction other nations. Sanctions block villager and wandering trader shops and piglin bartering.");
    }

    private Nation kingNation(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return null;
        }
        TownyAPI towny = TownyAPI.getInstance();
        Nation nation = towny.getNation(player);
        Resident resident = towny.getResident(player);
        if (nation != null && resident != null && nation.isKing(resident)) {
            return nation;
        }
        return null;
    }

    private DurationSpec parseDuration(String raw) {
        String value = raw.toLowerCase(Locale.ROOT);
        if (value.equals("permanent") || value.equals("perm")) {
            return new DurationSpec(true, -1L);
        }
        if (value.length() < 2) {
            return null;
        }
        char unit = value.charAt(value.length() - 1);
        long multiplier = switch (unit) {
            case 's' -> 1_000L;
            case 'm' -> 60_000L;
            case 'h' -> 3_600_000L;
            case 'd' -> 86_400_000L;
            default -> 0L;
        };
        if (multiplier == 0L) {
            return null;
        }
        try {
            long amount = Long.parseLong(value.substring(0, value.length() - 1));
            if (amount < 1L) {
                return null;
            }
            return new DurationSpec(false, Math.multiplyExact(amount, multiplier));
        } catch (NumberFormatException | ArithmeticException exception) {
            return null;
        }
    }

    private String formatExpiry(Sanction sanction) {
        return sanction.isPermanent() ? "permanent" : "expires in " + formatRemaining(sanction.getExpiresAt());
    }

    private String formatRemaining(long expiresAt) {
        long seconds = Math.max(0L, (expiresAt - System.currentTimeMillis()) / 1000L);
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m";
        }
        return Math.max(1L, seconds) + "s";
    }

    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 1) + "…";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return matching(List.of("impose", "lift", "list", "status", "clear", "reload", "help"), args[0]);
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && List.of("impose", "lift", "pardon", "status", "info").contains(action)) {
            List<String> names = TownyAPI.getInstance().getNations().stream()
                    .map(Nation::getName)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            return matching(names, args[1]);
        }
        if (action.equals("impose") && args.length == 3
                && TownyAPI.getInstance().getNation(args[1]) != null) {
            return matching(List.of("30m", "12h", "7d", "permanent"), args[args.length - 1]);
        }
        if (action.equals("list") && args.length == 2) {
            return matching(List.of("1", "2", "3"), args[1]);
        }
        return List.of();
    }

    private List<String> matching(List<String> options, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }

    private record DurationSpec(boolean permanent, long millis) {
    }
}
