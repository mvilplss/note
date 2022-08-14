package org.example.design.states.practice.state;

import org.example.design.states.practice.AuctionStateMachine;

public class AuctionSuccessState extends AuctionState {
    private AuctionStateMachine auctionStateMachine;

    public AuctionSuccessState(AuctionStateMachine auctionStateMachine) {
        this.auctionStateMachine = auctionStateMachine;
    }
    @Override
    public AuctionEnum getState() {
        return AuctionEnum.AuctionSuccessState;
    }


}
