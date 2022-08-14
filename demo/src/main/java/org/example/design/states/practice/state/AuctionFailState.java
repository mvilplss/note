package org.example.design.states.practice.state;

import org.example.design.states.practice.AuctionStateMachine;

public class AuctionFailState extends AuctionState {
    private AuctionStateMachine auctionStateMachine;

    public AuctionFailState(AuctionStateMachine auctionStateMachine) {
        this.auctionStateMachine = auctionStateMachine;
    }
    @Override
    public AuctionEnum getState() {
        return AuctionEnum.AuctionFailState;
    }

}
