package org.example.design.states.practice.state;

import org.example.design.states.practice.AuctionStateMachine;

public class WaitingShowState extends AuctionState {
    private AuctionStateMachine auctionStateMachine;

    public WaitingShowState(AuctionStateMachine auctionStateMachine) {
        this.auctionStateMachine = auctionStateMachine;
    }

    @Override
    public AuctionEnum getState() {
        return AuctionEnum.WaitingShowState;
    }

    @Override
    public AuctionStateMachine publishAuction() {
        auctionStateMachine.setState(auctionStateMachine.getShowingState());
        return auctionStateMachine;
    }
}
