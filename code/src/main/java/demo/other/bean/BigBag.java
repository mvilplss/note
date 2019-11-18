package demo.other.bean;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/11/6
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
public class BigBag {

    public LittleBag littleBag;

    public BigBag(){
        System.out.println("BigBag construct");
    }


    public void say(){
        System.out.println("i am BigBag has :"+littleBag);
    }

    public LittleBag getLittleBag() {
        return littleBag;
    }

    public void setLittleBag(LittleBag littleBag) {
        this.littleBag = littleBag;
    }
}
