package math;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * 有N个m长度的管子
 * 需要截取x组长度y<=m的子管
 * 求：如何截取剩余的管子个数最少（剩余的管子单个长度最长）
 */
public class 最佳材料使用 {
    // 3个100cm管子，如何最佳截取3个50cm的管子
    static Map<Integer,Integer> tubes = new HashMap<>();
    static Map<Integer,Integer> subSubes = new HashMap<>();

    static {
        tubes.put(1,100);
        tubes.put(2,100);
        tubes.put(3,100);
    }

    @Test
    public void 截取(){

        for (int i = 0; i < tubes.values().size(); i++) {
            Integer m = tubes.get(i);
//            while (m>)
        }
    }
}
