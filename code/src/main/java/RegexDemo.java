import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * All rights Reserved, Designed By www.maihaoche.com
 *
 * @author: 三行（sanxing@maihaoche.com）
 * @date: 2019/8/30
 * @Copyright: 2017-2020 www.maihaoche.com Inc. All rights reserved.
 * 注意：本内容仅限于卖好车内部传阅，禁止外泄以及用于其他的商业目
 */
public class RegexDemo {
    String html=
            "              <li class=\"header\"><span>仓储管理系统</span></li>\n" +
            "                            \n" +
            "                    \n" +
            "                      <li class=\"\"><a href=\"https://mclaren-o.maihaoche.net/page/shortcut.htm\"><i class=\"fa fa-dashboard\"></i><span>工作台</span></a></li>\n" +
            "                                      \n" +
            "                    \n" +
            "                      <li class=\"treeview \">\n" +
            "              <a href=\"javascript:void(0);\"><i class=\"fa fa-pie-chart\"></i><span>报表管理</span><span class=\"pull-right-container\"><i class=\"fa fa-angle-right pull-right\"></i></span></a>\n" +
            "              <ul class=\"treeview-menu\">\n" +
            "                                                      <li><a href=\"https://mclaren-o.maihaoche.net/page/stock.htm\">库存报表</a></li>\n" +
            "                                                                        <li><a href=\"https://mclaren-o.maihaoche.net/page/checkIn.htm\">入库日报</a></li>\n" +
            "                                                                        <li><a href=\"https://mclaren-o.maihaoche.net/page/checkOut.htm\">出库日报</a></li>";

    @Test
    public void xxx(){
        String hrefRegex = "<a href=\"(http[^\"]*)\"\\s*>[^\\u4e00-\\u9fa5]*([\\u4e00-\\u9fa5]+[^</a>]*)";
        Pattern pattern = Pattern.compile(hrefRegex);
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()){
            System.out.println(matcher.group(1));
            System.out.println(matcher.group(2));
        }
    }

    @Test
    public void xxxx(){
        html = "<li class=\"\"><a href=\"https://mclaren-o.maihaoche.net/page/shortcut.htm\"><i class=\"fa fa-dashboard\">系统</i><span>NEW工作台NEW</span></a></li>";
        String hrefRegex = "<a href=\"(http[^\"]*)\"\\s*>[^\\w]*([\\w]+[^</a>]*)";
        Pattern pattern = Pattern.compile(hrefRegex);
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()){
            System.out.println(matcher.group(1));
            System.out.println(matcher.group(2));
        }
    }
}
