package org.example.design.states.practice.state;

import org.example.design.states.practice.AuctionStateMachine;

public class InitState extends AuctionState {

    public AuctionStateMachine auctionStateMachine;
    public InitState(AuctionStateMachine auctionStateMachine){
        this.auctionStateMachine = auctionStateMachine;
    }

    @Override
    public AuctionEnum getState() {
        return AuctionEnum.InitState;
    }

    @Override
    public AuctionStateMachine initAuction() {
        auctionStateMachine.setState(auctionStateMachine.getWaitingShowState());
        return auctionStateMachine;
    }
}
