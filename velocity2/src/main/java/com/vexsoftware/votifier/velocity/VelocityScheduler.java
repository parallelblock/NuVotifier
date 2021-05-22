package com.vexsoftware.votifier.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import com.vexsoftware.votifier.platform.scheduler.ScheduledVotifierTask;
import com.vexsoftware.votifier.platform.scheduler.VotifierScheduler;

import java.util.concurrent.TimeUnit;

class VelocityScheduler implements VotifierScheduler {
    private final ProxyServer server;
    private final VotifierPlugin plugin;

    public VelocityScheduler(ProxyServer server, VotifierPlugin plugin) {
        this.server = server;
        this.plugin = plugin;
    }

    private Scheduler.TaskBuilder builder(Runnable runnable) {
        return server.scheduler().buildTask(plugin, runnable);
    }

    @Override
    public ScheduledVotifierTask sync(Runnable runnable) {
        return onPool(runnable);
    }

    @Override
    public ScheduledVotifierTask onPool(Runnable runnable) {
        return new TaskWrapper(builder(runnable).schedule());
    }

    @Override
    public ScheduledVotifierTask delayedSync(Runnable runnable, int delay, TimeUnit unit) {
        return delayedOnPool(runnable, delay, unit);
    }

    @Override
    public ScheduledVotifierTask delayedOnPool(Runnable runnable, int delay, TimeUnit unit) {
        return new TaskWrapper(builder(runnable).delay(delay, unit).schedule());
    }

    @Override
    public ScheduledVotifierTask repeatOnPool(Runnable runnable, int delay, int repeat, TimeUnit unit) {
        return new TaskWrapper(builder(runnable).delay(delay, unit).repeat(repeat, unit).schedule());
    }

    private static class TaskWrapper implements ScheduledVotifierTask {
        private final ScheduledTask task;

        private TaskWrapper(ScheduledTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            task.cancel();
        }
    }
}
