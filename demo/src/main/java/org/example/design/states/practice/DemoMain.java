package org.example.design.states.practice;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;

public class DemoMain {

    public static void main(String[] args) {
        Seller seller = new Seller();
        AuctionStateMachine auction = new AuctionStateMachine();
        auction.setAuctionName("拍卖车辆A");
        auction.setBeginTime(DateUtil.format(new DateTime(),"yyyy-MM-dd HH:mm:ss"));
        auction.setEndTime(DateUtil.format(new DateTime().offset(DateField.MINUTE,3),"yyyy-MM-dd HH:mm:ss"));
        // 创建竞拍单
        seller.initAuction(auction);
        // 发拍
        seller.publishAuction(auction);
        // 任务到期开始竞拍
        Task task = new Task();
        task.showEndAuction(auction);
        // 出价
        Buyer buyer = new Buyer();
        buyer.bid(auction);
        // 竞拍结束
        task.bidOverAuction(auction);
    }
}
