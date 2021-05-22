package com.vexsoftware.votifier.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.vexsoftware.votifier.platform.BackendServer;

class VelocityBackendServer implements BackendServer {
    private final ProxyServer server;
    private final RegisteredServer rs;

    VelocityBackendServer(ProxyServer server, RegisteredServer rs) {
        this.server = server;
        this.rs = rs;
    }

    @Override
    public String getName() {
        return rs.serverInfo().name();
    }

    @Override
    public boolean sendPluginMessage(String channel, byte[] data) {
        return rs.sendPluginMessage(VelocityUtil.getId(channel), data);
    }

    @Override
    public String toString() {
        return rs.serverInfo().name();
    }
}
