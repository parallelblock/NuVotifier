package com.vexsoftware.votifier.velocity.event;

import com.velocitypowered.api.event.Event;
import com.vexsoftware.votifier.model.Vote;

public class VotifierEvent implements Event {
    private final Vote vote;

    public VotifierEvent(Vote vote) {
        this.vote = vote;
    }

    public Vote getVote() {
        return vote;
    }

    @Override
    public String toString() {
        return "VotifierEvent{" +
                "vote=" + vote +
                '}';
    }
}
