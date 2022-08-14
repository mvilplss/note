package org.example.design.states.practice;

public class Task {

    public void showEndAuction(AuctionStateMachine auctionStateMachine){
        AuctionStateMachine context = auctionStateMachine.showEndAuction();
        System.out.println("开始竞拍插入数据库："+ context);
    }

    public void bidOverAuction(AuctionStateMachine auctionStateMachine){
        AuctionStateMachine context = auctionStateMachine.bidOverAuction();
        System.out.println("竞拍结束插入数据库："+ context);
    }

}
