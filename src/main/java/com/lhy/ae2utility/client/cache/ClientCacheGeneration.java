package com.lhy.ae2utility.client.cache;

/** Identity-based client scope, independent of Minecraft to make reconnect/menu reuse testable. */
public final class ClientCacheGeneration {
    private Object world;
    private Object player;
    private Object menu;
    private long generation;

    public boolean observe(Object world, Object player, Object menu) {
        if (this.world == world && this.player == player && this.menu == menu) return false;
        this.world = world;
        this.player = player;
        this.menu = menu;
        generation++;
        return true;
    }

    public void invalidate() { generation++; }
    public long generation() { return generation; }
}
