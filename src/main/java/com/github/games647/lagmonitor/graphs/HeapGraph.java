package com.github.games647.lagmonitor.graphs;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

import org.bukkit.map.MapCanvas;

public class HeapGraph extends GraphRenderer {

    private final MemoryMXBean heapUsage = ManagementFactory.getMemoryMXBean();

    public HeapGraph() {
        super("HeapUsage (MB)");
    }

    @Override
    public int renderGraphTick(MapCanvas canvas, int nextPosX) {
        //byte -> mega byte
        int max = (int) (heapUsage.getHeapMemoryUsage().getCommitted() / 1024 / 1024);
        int used = (int) (heapUsage.getHeapMemoryUsage().getUsed() / 1024 / 1024);

        //round to the next 100 e.g. 801 -> 900
        int roundedMax = ((max + 99) / 100) * 100;

        int maxHeight = getHeightScaled(roundedMax, max);
        int usedHeight = getHeightScaled(roundedMax, used);

        //x=0 y=0 is the left top point so convert it
        int convertedMaxHeight = MAX_HEIGHT - maxHeight;
        int convertedUsedHeight = MAX_HEIGHT - usedHeight;
        canvas.setPixel(nextPosX, convertedMaxHeight, MAX_COLOR);
        for (int yPos = convertedMaxHeight; yPos < 128; yPos++) {
            canvas.setPixel(nextPosX, yPos, MAX_COLOR);
        }

        canvas.setPixel(nextPosX, convertedUsedHeight, USED_COLOR);
        for (int yPos = convertedUsedHeight; yPos < 128; yPos++) {
            canvas.setPixel(nextPosX, yPos, USED_COLOR);
        }

        return maxHeight;
    }
}
