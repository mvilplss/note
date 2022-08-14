package org.example.design.states.practice.state;

import org.example.design.states.practice.AuctionStateMachine;

public class ShowingState extends AuctionState {
    private AuctionStateMachine auctionStateMachine;

    public ShowingState(AuctionStateMachine auctionStateMachine) {
        this.auctionStateMachine = auctionStateMachine;
    }

    @Override
    public AuctionEnum getState() {
        return AuctionEnum.ShowingState;
    }

    @Override
    public AuctionStateMachine showEndAuction() {
        auctionStateMachine.setState(auctionStateMachine.getBiddingState());
        return auctionStateMachine;
    }
}
