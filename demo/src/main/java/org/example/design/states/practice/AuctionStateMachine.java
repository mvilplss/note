package org.example.design.states.practice;

import lombok.Data;
import org.example.design.states.practice.state.*;

@Data
public class AuctionStateMachine {

    private AuctionState initState;
    private AuctionState waitingShowState;
    private AuctionState showingState;
    private AuctionState biddingState;
    private AuctionState auctionSuccessState;
    private AuctionState auctionFailState;

    private AuctionState state;

    private String auctionName;
    private String beginTime;
    private String endTime;

    public AuctionStateMachine() {
        initState = new InitState(this);
        waitingShowState = new WaitingShowState(this);
        showingState = new ShowingState(this);
        biddingState = new BiddingState(this);
        auctionSuccessState = new AuctionSuccessState(this);
        auctionFailState = new AuctionFailState(this);
        state = initState;
    }

    /**
     * 初始化
     * @return
     */
    public AuctionStateMachine initAuction(){
        AuctionStateMachine context = state.initAuction();
        return context;
    }

    /**
     * 发布
     * @return
     */
    public AuctionStateMachine publishAuction(){
        AuctionStateMachine context = state.publishAuction();
        return context;
    }

    /**
     * 预展结束
     * @return
     */
    public AuctionStateMachine showEndAuction() {
        AuctionStateMachine context = state.showEndAuction();
        return context;
    }

    /**
     * 结束
     * @return
     */
    public AuctionStateMachine bidOverAuction() {
        AuctionStateMachine context = state.bidOverAuction();
        return context;
    }

    @Override
    public String toString() {
        return "AuctionContext{" +
                "state=" + state.getState() +
                ", auctionName='" + auctionName + '\'' +
                ", beginTime='" + beginTime + '\'' +
                ", endTime='" + endTime + '\'' +
                '}';
    }
}
