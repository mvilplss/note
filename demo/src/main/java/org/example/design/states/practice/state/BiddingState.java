package org.example.design.states.practice.state;

import org.example.design.states.practice.AuctionStateMachine;

public class BiddingState extends AuctionState {
    private AuctionStateMachine auctionStateMachine;

    public BiddingState(AuctionStateMachine auctionStateMachine) {
        this.auctionStateMachine = auctionStateMachine;
    }

    @Override
    public AuctionEnum getState() {
        return AuctionEnum.BiddingState;
    }

    @Override
    public AuctionStateMachine bidOverAuction() {
        auctionStateMachine.setState(auctionStateMachine.getAuctionSuccessState());
        // 也可能竞价失败
        auctionStateMachine.setState(auctionStateMachine.getAuctionFailState());
        return auctionStateMachine;
    }
}
