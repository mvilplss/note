package org.example.design.states.practice.state;

import org.example.design.states.practice.AuctionStateMachine;
import org.example.design.states.practice.state.AuctionEnum;

public abstract class AuctionState {

    public abstract AuctionEnum getState();

    // 初始化
    public AuctionStateMachine initAuction(){
        throw new RuntimeException("need to impl.");
    }

    // 发拍预展
    public AuctionStateMachine publishAuction(){
        throw new RuntimeException("need to impl.");
    }

    // 预展结束
    public AuctionStateMachine showEndAuction(){
        throw new RuntimeException("need to impl.");
    }

    // 竞价结束
    public AuctionStateMachine bidOverAuction(){
        throw new RuntimeException("need to impl.");
    }
}
