package demo.other.bean;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/11/6
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
public class LittleBag {

    public BigBag bigBag;

    public LittleBag(){
        System.out.println("LittleBag construct");
    }

    public void say(){
        System.out.println("i am LittleBag has :"+bigBag);
    }

    public BigBag getBigBag() {
        return bigBag;
    }

    public void setBigBag(BigBag bigBag) {
        this.bigBag = bigBag;
    }
}
