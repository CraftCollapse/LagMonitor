package com.github.games647.lagmonitor.commands;

import co.aikar.timings.TimingHistory;
import co.aikar.timings.Timings;
import co.aikar.timings.TimingsManager;
import com.github.games647.lagmonitor.LagMonitor;
import com.github.games647.lagmonitor.Pagination;
import com.github.games647.lagmonitor.traffic.Reflection;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * PaperSpigot and Sponge uses a new timings system (v2).
 * Missing data:
 * * TicksRecord
 * -> player ticks
 * -> timedTicks
 * -> entityTicks
 * -> activatedEntityTicks
 * -> tileEntityTicks
 * * MinuteReport
 * -> time
 * -> tps
 * -> avgPing
 * -> fullServerTick
 * -> ticks
 * * World data
 * -> worldName
 * -> tileEntities
 * -> entities
 *
 * => This concludes to the fact that the big benefits from Timings v2 isn't available. For example you cannot
 * scroll through your history
 */
public class PaperTimingsCommand implements CommandExecutor {

    private static final String TIMINGS_PACKAGE = "co.aikar.timings";

    private static final String EXPORT_CLASS = TIMINGS_PACKAGE + '.' + "TimingsExport";
    private static final String HANDLER_CLASS = TIMINGS_PACKAGE + '.' + "TimingHandler";
    private static final String HISTORY_ENTRY_CLASS = TIMINGS_PACKAGE + '.' + "TimingHistoryEntry";
    private static final String DATA_CLASS = TIMINGS_PACKAGE + '.' + "TimingData";

    private static final ChatColor PRIMARY_COLOR = ChatColor.DARK_AQUA;
    private static final ChatColor HEADER_COLOR = ChatColor.YELLOW;
    private static final ChatColor SECONDARY_COLOR = ChatColor.GRAY;

    private final LagMonitor plugin;
    private int historyIntervall;

    public PaperTimingsCommand(LagMonitor plugin) {
        this.plugin = plugin;

        try {
            historyIntervall = Reflection.getField("com.destroystokyo.paper.PaperConfig", "config"
            , YamlConfiguration.class).get(null).getInt("timings.history-interval");
        } catch (IllegalArgumentException illegalArgumentException) {
            //cannot find paper spigot
            historyIntervall = -1;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isAllowed(sender, command)) {
            sender.sendMessage(org.bukkit.ChatColor.DARK_RED + "Not whitelisted");
            return true;
        }

        try {
            Class.forName(EXPORT_CLASS);
        } catch (ClassNotFoundException e) {
            sender.sendMessage(ChatColor.DARK_RED + "You aren't using PaperSpigot.");
            sender.sendMessage(ChatColor.DARK_RED + "This command is for the new timings (v2) system only");
            sender.sendMessage(ChatColor.DARK_RED + "Please use '/timing' for the old system");
            return true;
        }

        if (!Timings.isTimingsEnabled()) {
            sender.sendMessage(ChatColor.DARK_RED + "The server deactivated timing reports");
            sender.sendMessage(ChatColor.DARK_RED + "Go to paper.yml and activate timings");
            return true;
        }

        EvictingQueue<TimingHistory> history = Reflection.getField(TimingsManager.class, "HISTORY", EvictingQueue.class)
                .get(null);

        TimingHistory lastHistory = history.peek();
        if (lastHistory == null) {
            sender.sendMessage(ChatColor.DARK_RED + "Not enough data collected yet");
            return true;
        }

        List<BaseComponent[]> lines = Lists.newArrayList();
        printTimings(lines, lastHistory);

        Pagination pagination = new Pagination("Paper Timings", lines);
        pagination.send(sender);

        this.plugin.getPaginations().put(sender, pagination);
        return true;
    }

    public void printTimings(List<BaseComponent[]> lines, TimingHistory lastHistory) {
        printHeadData(lastHistory, lines);

        Map<Integer, String> idHandler = Maps.newHashMap();

        Map<?, ?> groups = Reflection.getField(TIMINGS_PACKAGE + ".TimingIdentifier", "GROUP_MAP", Map.class).get(null);
        for (Object group : groups.values()) {
            String groupName = Reflection.getField(group.getClass(), "name", String.class).get(group);
            ArrayDeque<?> handlers = Reflection.getField(group.getClass(), "handlers", ArrayDeque.class).get(group);
            for (Object handler : handlers) {
                int id = Reflection.getField(HANDLER_CLASS, "id", Integer.TYPE).get(handler);
                String name = Reflection.getField(HANDLER_CLASS, "name", String.class).get(handler);
                if (name.contains("Combined")) {
                    idHandler.put(id, "Combined " + groupName);
                } else {
                    idHandler.put(id, name);
                }
            }
        }

        //TimingHistoryEntry
        Object[] entries = Reflection.getField(TimingHistory.class, "entries", Object[].class).get(lastHistory);
        for (Object entry : entries) {
            Object parentData = Reflection.getField(HISTORY_ENTRY_CLASS, "data", Object.class).get(entry);
            int childId = Reflection.getField(DATA_CLASS, "id", Integer.TYPE).get(parentData);

            String handlerName = idHandler.get(childId);
            String parentName;
            if (handlerName == null) {
                parentName = "Unknown-" + childId;
            } else {
                parentName = handlerName;
            }

            int parentCount = Reflection.getField(DATA_CLASS, "count", Integer.TYPE).get(parentData);
            long parentTime = Reflection.getField(DATA_CLASS, "totalTime", Long.TYPE).get(parentData);

//            long parentLagCount = Reflection.getField(DATA_CLASS, "lagCount", Integer.TYPE).get(parentData);
//            long parentLagTime = Reflection.getField(DATA_CLASS, "lagTime", Long.TYPE).get(parentData);
            lines.add(new ComponentBuilder(parentName).color(HEADER_COLOR)
                    .append(" Count: " + parentCount + " Time: " + parentTime).create());

            Object[] children = Reflection.getField(HISTORY_ENTRY_CLASS, "children", Object[].class).get(entry);
            for (Object childData : children) {
                printChilds(parentData, childData, idHandler, lines);
            }
        }
    }

    private void printChilds(Object parent, Object childData, Map<Integer, String> idMap, List<BaseComponent[]> lines) {
        int childId = Reflection.getField(DATA_CLASS, "id", Integer.TYPE).get(childData);

        String handlerName = idMap.get(childId);
        String childName;
        if (handlerName == null) {
            childName = "Unknown-" + childId;
        } else {
            childName = handlerName;
        }

        int childCount = Reflection.getField(DATA_CLASS, "count", Integer.TYPE).get(childData);
        long childTime = Reflection.getField(DATA_CLASS, "totalTime", Long.TYPE).get(childData);

        long parentTime = Reflection.getField(DATA_CLASS, "totalTime", Long.TYPE).get(parent);
        float percent = (float) childTime / parentTime;

        lines.add(new ComponentBuilder("    " + childName + " Count: " + childCount + " Time: " + childTime
                + ' ' + round(percent) + '%')
                .color(PRIMARY_COLOR).create());
    }

    private void printHeadData(TimingHistory lastHistory, List<BaseComponent[]> lines) {
        // Represents all time spent running the server this history

        long totalTime = Reflection.getField(TimingHistory.class, "totalTime", Long.TYPE).get(lastHistory);
        long totalTicks = Reflection.getField(TimingHistory.class, "totalTicks", Long.TYPE).get(lastHistory);

        long cost = (long) Reflection.getMethod(EXPORT_CLASS, "getCost").invoke(null);
        lines.add(new ComponentBuilder("Cost: ").color(PRIMARY_COLOR)
                .append(Long.toString(cost)).color(SECONDARY_COLOR).create());

        float totalSeconds = (float) totalTime / 1000 / 1000;

        long playerTicks = TimingHistory.playerTicks;
        long tileEntityTicks = TimingHistory.tileEntityTicks;
        long activatedEntityTicks = TimingHistory.activatedEntityTicks;
        long entityTicks = TimingHistory.entityTicks;

        float activatedAvgEntities = (float) activatedEntityTicks / totalTicks;
        float totalAvgEntities = (float) entityTicks / totalTicks;

        float averagePlayers = (float) playerTicks / totalTicks;

        float desiredTicks = 20 * historyIntervall;
        float averageTicks = totalTicks / desiredTicks * 20;

        String format = ChatColor.DARK_AQUA + "%s" + " " + ChatColor.GRAY + "%s";

        //head data
        lines.add(TextComponent.fromLegacyText(String.format(format, "Total (sec):", round(totalSeconds))));
        lines.add(TextComponent.fromLegacyText(String.format(format, "Ticks:", round(totalTicks))));
        lines.add(TextComponent.fromLegacyText(String.format(format, "Avg ticks:", round(averageTicks))));
//        lines.add(TextComponent.fromLegacyText(String.format(format, "Server Load:", round(serverLoad))));
        lines.add(TextComponent.fromLegacyText(String.format(format, "AVG Players:", round(averagePlayers))));

        lines.add(TextComponent.fromLegacyText(String.format(format, "Activated Entities:", round(activatedAvgEntities))
                + " / " + round(totalAvgEntities)));
    }

    private float round(float number) {
        return (float) (Math.round(number * 100.0) / 100.0);
    }
}
