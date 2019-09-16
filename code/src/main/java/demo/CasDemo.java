package demo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/9/12
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
@Slf4j
public class CasDemo extends BaseDemo {

    @Test
    public void casAba() throws Exception{
        Integer a = 1;
        AtomicStampedReference atomicStampedReference = new AtomicStampedReference(a,1);
        boolean b = atomicStampedReference.compareAndSet(1   , 2, 1, 2);
        System.out.println(b);
    }
}
