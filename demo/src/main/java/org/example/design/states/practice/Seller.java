package org.example.design.states.practice;

public class Seller {
    public void initAuction(AuctionStateMachine auctionStateMachine){
        AuctionStateMachine context = auctionStateMachine.initAuction();
        System.out.println("初始化插入数据库："+ context);
    }


    public void publishAuction(AuctionStateMachine auctionStateMachine){
        AuctionStateMachine context = auctionStateMachine.publishAuction();
        System.out.println("发拍插入数据库："+ context);
    }


}
