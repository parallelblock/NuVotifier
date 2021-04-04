package com.vexsoftware.votifier.sponge8;

import com.vexsoftware.votifier.platform.scheduler.ScheduledVotifierTask;
import com.vexsoftware.votifier.platform.scheduler.VotifierScheduler;
import org.spongepowered.api.scheduler.ScheduledTask;
import org.spongepowered.api.scheduler.Scheduler;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.plugin.PluginContainer;

import java.util.concurrent.TimeUnit;

class SpongeScheduler implements VotifierScheduler {
    private final PluginContainer plugin;
    private final Scheduler syncScheduler;
    private final Scheduler asyncScheduler;

    SpongeScheduler(PluginContainer plugin, Scheduler syncScheduler, Scheduler asyncScheduler) {
        this.plugin = plugin;
        this.syncScheduler = syncScheduler;
        this.asyncScheduler = asyncScheduler;
    }

    private Task.Builder taskBuilder(Runnable runnable) {
        return Task.builder()
                .plugin(plugin)
                .execute(runnable);
    }

    @Override
    public ScheduledVotifierTask sync(Runnable runnable) {
        return new TaskWrapper(syncScheduler.submit(taskBuilder(runnable).build()));
    }

    @Override
    public ScheduledVotifierTask onPool(Runnable runnable) {
        return new TaskWrapper(asyncScheduler.submit(taskBuilder(runnable).build()));
    }

    @Override
    public ScheduledVotifierTask delayedSync(Runnable runnable, int delay, TimeUnit unit) {
        return new TaskWrapper(syncScheduler.submit(taskBuilder(runnable).delay(delay, unit).build()));
    }

    @Override
    public ScheduledVotifierTask delayedOnPool(Runnable runnable, int delay, TimeUnit unit) {
        return new TaskWrapper(asyncScheduler.submit(taskBuilder(runnable).delay(delay, unit).build()));
    }

    @Override
    public ScheduledVotifierTask repeatOnPool(Runnable runnable, int delay, int repeat, TimeUnit unit) {
        return new TaskWrapper(asyncScheduler.submit(taskBuilder(runnable).delay(delay, unit).interval(repeat, unit).build()));
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
