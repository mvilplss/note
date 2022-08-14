package org.example.design.states.practice;

import org.example.design.states.practice.state.AuctionEnum;

public class Buyer {
    public void bid(AuctionStateMachine auction) {
        if (auction.getState().getState().equals(AuctionEnum.BiddingState)){
            System.out.println("出价成功");
        }else {
            System.err.println("出价失败，当前状态："+auction.getState().getState());
        }
    }
}
