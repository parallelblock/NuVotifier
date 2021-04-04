package com.vexsoftware.votifier.sponge8.event;

import com.vexsoftware.votifier.model.Vote;
import org.spongepowered.api.event.Cause;
import org.spongepowered.api.event.impl.AbstractEvent;

/**
 * This event is posted whenever a vote is received and processed by NuVotifier. Note that NuVotifier posts this event
 * synchronously.
 */
public class VotifierEvent extends AbstractEvent {
    private final Vote vote;
    private final Cause cause;

    public VotifierEvent(Vote vote, Cause cause) {
        this.vote = vote;
        this.cause = cause;
    }

    @Override
    public Cause cause() {
        return cause;
    }

    public Vote getVote() {
        return vote;
    }
}
