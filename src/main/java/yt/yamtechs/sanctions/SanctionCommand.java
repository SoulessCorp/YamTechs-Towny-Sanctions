package yt.yamtechs.sanctions;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class SanctionCommand implements TabExecutor {
    private static final String ADMIN_PERMISSION = "sanction.admin";
    private static final String PREFIX = ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + "YamTech"
            + ChatColor.DARK_GRAY + "] " + ChatColor.GRAY;
    private static final int PAGE_SIZE = 10;
    private static final Pattern DURATION = Pattern.compile("(?i)^(permanent|perm|[1-9][0-9]*[smhd])$");

    private final SanctionsPlugin plugin;
    private final SanctionManager sanctions;

    public SanctionCommand(SanctionsPlugin plugin, SanctionManager sanctions) {
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
        int forIndex = optionIndex(args, "--for");
        if (forIndex < 2 || forIndex + 1 >= args.length || !DURATION.matcher(args[forIndex + 1]).matches()) {
            sender.sendMessage(PREFIX + "Usage: /sanction impose <nation|team:name> --for <duration> [reason]");
            sender.sendMessage(PREFIX + "Use --for to separate the target from the duration; this supports names like '5m Republic'.");
            return;
        }
        Target target = resolveTarget(String.join(" ", Arrays.copyOfRange(args, 1, forIndex)));
        if (target == null) {
            sender.sendMessage(PREFIX + "Nation or AllyTeam not found.");
            return;
        }

        Nation nationIssuer = kingNation(sender);
        boolean admin = sender.hasPermission(ADMIN_PERMISSION);
        if (nationIssuer == null && !admin) {
            sender.sendMessage(PREFIX + "Only a nation king or an admin can issue sanctions.");
            return;
        }
        if (!admin && plugin.getAllyTeamManager().findTeamForNation(nationIssuer.getUUID()) != null) {
            sender.sendMessage(PREFIX + "Your nation is in an AllyTeam. Start a team vote with /allyteam vote sanction ...");
            return;
        }
        if (target.type() == Sanction.TargetType.NATION && nationIssuer != null
                && nationIssuer.getUUID().equals(target.id())) {
            sender.sendMessage(PREFIX + "A nation cannot sanction itself.");
            return;
        }
        if (target.type() == Sanction.TargetType.ALLY_TEAM && nationIssuer != null
                && plugin.getAllyTeamManager().findTeamForNation(nationIssuer.getUUID()) != null
                && plugin.getAllyTeamManager().findTeamForNation(nationIssuer.getUUID()).getId().equals(target.id())) {
            sender.sendMessage(PREFIX + "A nation cannot sanction its own AllyTeam.");
            return;
        }

        DurationSpec duration = parseDuration(args[forIndex + 1]);
        if (duration == null) {
            sender.sendMessage(PREFIX + "Invalid or excessively large duration.");
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
        int reasonIndex = forIndex + 2;
        if (reasonIndex < args.length && args[reasonIndex].equalsIgnoreCase("--reason")) {
            reasonIndex++;
        }
        String reason = reasonIndex < args.length
                ? String.join(" ", Arrays.copyOfRange(args, reasonIndex, args.length)) : "No reason given.";
        if (reason.isBlank()) {
            reason = "No reason given.";
        }

        Sanction.IssuerType issuerType = admin ? Sanction.IssuerType.SERVER : Sanction.IssuerType.NATION;
        UUID issuerId = admin ? null : nationIssuer.getUUID();
        String issuerName = admin ? "Server" : nationIssuer.getName();
        sanctions.add(target.type(), target.id(), target.name(), issuerType, issuerId, issuerName,
                sender.getName(), expiresAt, reason);
        String length = expiresAt < 0 ? "permanently" : "for " + formatRemaining(expiresAt);
        sender.sendMessage(PREFIX + "Trade embargo issued against " + ChatColor.GOLD + target.label()
                + ChatColor.GRAY + " " + length + ". Reason: " + ChatColor.WHITE + reason);
    }

    private void lift(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "Usage: /sanction lift <nation|team:name>");
            return;
        }
        Target target = resolveTarget(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        if (target == null) {
            sender.sendMessage(PREFIX + "Nation or AllyTeam not found.");
            return;
        }
        Nation nationIssuer = kingNation(sender);
        boolean admin = sender.hasPermission(ADMIN_PERMISSION);
        if (nationIssuer == null && !admin) {
            sender.sendMessage(PREFIX + "Only a nation king or an admin can lift sanctions.");
            return;
        }
        if (!admin && plugin.getAllyTeamManager().findTeamForNation(nationIssuer.getUUID()) != null) {
            sender.sendMessage(PREFIX + "Your nation is in an AllyTeam; only an admin can lift this directly.");
            return;
        }
        int removed = admin
                ? sanctions.removeAllForTarget(target.type(), target.id())
                : sanctions.removeFromTarget(target.type(), target.id(), Sanction.IssuerType.NATION,
                nationIssuer.getUUID());
        if (removed == 0) {
            sender.sendMessage(PREFIX + "No sanctions from your issuer were found against " + target.label() + ".");
        } else {
            sender.sendMessage(PREFIX + "Lifted " + removed + " sanction(s) against " + ChatColor.GOLD
                    + target.label() + ChatColor.GRAY + ". Other issuers' sanctions remain active.");
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
                if (page < 1) throw new NumberFormatException();
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
        sender.sendMessage(ChatColor.DARK_GRAY + "--- " + ChatColor.AQUA + "YamTech Sanctions"
                + ChatColor.GRAY + " (" + page + "/" + pages + ") " + ChatColor.DARK_GRAY + "---");
        if (active.isEmpty()) {
            sender.sendMessage(PREFIX + "No active sanctions.");
            return;
        }
        int start = (page - 1) * PAGE_SIZE;
        for (int i = start; i < Math.min(start + PAGE_SIZE, active.size()); i++) {
            Sanction sanction = active.get(i);
            sender.sendMessage(ChatColor.GOLD + displayTarget(sanction) + ChatColor.GRAY + " ← "
                    + ChatColor.YELLOW + displayIssuer(sanction) + ChatColor.GRAY + " | "
                    + formatExpiry(sanction) + " | " + truncate(sanction.getReason(), 70));
        }
        if (page < pages) {
            sender.sendMessage(ChatColor.GRAY + "Use /sanction list " + (page + 1) + " for the next page.");
        }
    }

    private void status(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "Usage: /sanction status <nation|team:name>");
            return;
        }
        Target target = resolveTarget(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        if (target == null) {
            sender.sendMessage(PREFIX + "Nation or AllyTeam not found.");
            return;
        }
        List<Sanction> active;
        if (target.type() == Sanction.TargetType.NATION) {
            Nation nation = TownyAPI.getInstance().getNations().stream()
                    .filter(candidate -> candidate.getUUID().equals(target.id())).findFirst().orElse(null);
            active = nation == null ? List.of() : sanctions.forNation(nation);
        } else {
            active = sanctions.forTarget(target.type(), target.id());
        }
        sender.sendMessage(ChatColor.DARK_GRAY + "--- " + ChatColor.GOLD + target.label()
                + ChatColor.GRAY + " sanction status " + ChatColor.DARK_GRAY + "---");
        if (active.isEmpty()) {
            sender.sendMessage(PREFIX + "No active sanctions apply to this target.");
            return;
        }
        for (Sanction sanction : active) {
            sender.sendMessage(ChatColor.GRAY + "Issued by " + ChatColor.YELLOW + displayIssuer(sanction)
                    + ChatColor.GRAY + " (" + sanction.getImposedBy() + "), " + formatExpiry(sanction) + ".");
            sender.sendMessage(ChatColor.GRAY + "Reason: " + ChatColor.WHITE + sanction.getReason());
        }
        sender.sendMessage(ChatColor.GRAY + "Effects: villager/wandering-trader trades and piglin bartering are blocked.");
    }

    private void clear(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(PREFIX + "Only an admin can clear every sanction.");
            return;
        }
        sender.sendMessage(PREFIX + "Cleared " + sanctions.clear() + " active sanction(s).");
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(PREFIX + "Only an admin can reload this plugin.");
            return;
        }
        plugin.reloadPluginState();
        sender.sendMessage(PREFIX + "Configuration, AllyTeams, proposals, and sanctions reloaded.");
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_GRAY + "--- " + ChatColor.AQUA + "YamTech Sanctions" + ChatColor.DARK_GRAY + " ---");
        sender.sendMessage(ChatColor.YELLOW + "/sanction impose <nation|team:name> --for <duration> [reason]"
                + ChatColor.GRAY + " - issue a trade embargo");
        sender.sendMessage(ChatColor.YELLOW + "/sanction lift <nation|team:name>"
                + ChatColor.GRAY + " - lift your issuer's sanction; admins lift all");
        sender.sendMessage(ChatColor.YELLOW + "/sanction status <nation|team:name>"
                + ChatColor.GRAY + " - show direct sanctions");
        sender.sendMessage(ChatColor.YELLOW + "/sanction list [page]"
                + ChatColor.GRAY + " - list active sanctions");
        sender.sendMessage(ChatColor.YELLOW + "/sanction clear | reload"
                + ChatColor.GRAY + " - admin actions");
        sender.sendMessage(ChatColor.GRAY + "Use --for to separate the duration from any nation name, e.g. /sanction impose The 5m Republic --for 7d blockade.");
        sender.sendMessage(ChatColor.GRAY + "Nation kings outside AllyTeams sanction directly; team members use /allyteam vote.");
    }

    private Target resolveTarget(String raw) {
        if (raw.regionMatches(true, 0, "team:", 0, 5)) {
            AllyTeam team = plugin.getAllyTeamManager().findTeamByName(raw.substring(5).trim());
            return team == null ? null : new Target(Sanction.TargetType.ALLY_TEAM, team.getId(), team.getName());
        }
        Nation nation = TownyAPI.getInstance().getNation(raw.trim());
        return nation == null ? null : new Target(Sanction.TargetType.NATION, nation.getUUID(), nation.getName());
    }

    private Nation kingNation(CommandSender sender) {
        if (!(sender instanceof Player player)) return null;
        TownyAPI towny = TownyAPI.getInstance();
        Nation nation = towny.getNation(player);
        Resident resident = towny.getResident(player);
        return nation != null && resident != null && nation.isKing(resident) ? nation : null;
    }

    private int optionIndex(String[] args, String option) {
        for (int i = 1; i < args.length; i++) {
            if (args[i].equalsIgnoreCase(option)) return i;
        }
        return -1;
    }

    private DurationSpec parseDuration(String raw) {
        String value = raw.toLowerCase(Locale.ROOT);
        if (value.equals("permanent") || value.equals("perm")) return new DurationSpec(true, -1L);
        char unit = value.charAt(value.length() - 1);
        long multiplier = switch (unit) {
            case 's' -> 1_000L;
            case 'm' -> 60_000L;
            case 'h' -> 3_600_000L;
            case 'd' -> 86_400_000L;
            default -> 0L;
        };
        try {
            long amount = Long.parseLong(value.substring(0, value.length() - 1));
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
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m";
        return Math.max(1L, seconds) + "s";
    }

    private String displayTarget(Sanction sanction) {
        return sanction.getTargetType() == Sanction.TargetType.ALLY_TEAM
                ? "AllyTeam " + sanction.getTargetName() : sanction.getTargetName();
    }

    private String displayIssuer(Sanction sanction) {
        return sanction.getIssuerType() == Sanction.IssuerType.ALLY_TEAM
                ? "AllyTeam " + sanction.getIssuerName() : sanction.getIssuerName();
    }

    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 1) + "…";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return matching(List.of("impose", "lift", "list", "status", "clear", "reload", "help"), args[0]);
        String action = args[0].toLowerCase(Locale.ROOT);
        if ((action.equals("impose") || action.equals("lift") || action.equals("status")) && args.length == 2) {
            List<String> names = new ArrayList<>(TownyAPI.getInstance().getNations().stream().map(Nation::getName).toList());
            plugin.getAllyTeamManager().allTeams().forEach(team -> names.add("team:" + team.getName()));
            return matching(names, args[1]);
        }
        if (action.equals("impose") && args[args.length - 1].equalsIgnoreCase("--for")) {
            return List.of("30m", "12h", "7d", "permanent");
        }
        if (action.equals("list") && args.length == 2) return matching(List.of("1", "2", "3"), args[1]);
        return List.of();
    }

    private List<String> matching(List<String> options, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }

    private record Target(Sanction.TargetType type, UUID id, String name) {
        private String label() {
            return type == Sanction.TargetType.ALLY_TEAM ? "AllyTeam " + name : name;
        }
    }

    private record DurationSpec(boolean permanent, long millis) {
    }
}
