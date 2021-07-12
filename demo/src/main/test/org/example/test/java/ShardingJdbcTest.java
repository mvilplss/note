package org.example.test.java;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidDataSourceFactory;
import com.google.common.base.Function;
import com.google.common.collect.Maps;
import org.apache.shardingsphere.api.config.sharding.ShardingRuleConfiguration;
import org.apache.shardingsphere.api.config.sharding.strategy.ShardingStrategyConfiguration;
import org.apache.shardingsphere.api.config.sharding.strategy.StandardShardingStrategyConfiguration;
import org.apache.shardingsphere.api.sharding.standard.PreciseShardingAlgorithm;
import org.apache.shardingsphere.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.shardingjdbc.api.ShardingDataSourceFactory;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ShardingJdbcTest {

    @Test
    public void test_() throws Exception{
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUsername("root");
        dataSource.setPassword("root");
        dataSource.setUrl("jdbc:mysql://localhost:3306/test");
        Map<String, DataSource> dataSourceMap=new HashMap<>();
        dataSourceMap.put("test",dataSource);

        ShardingRuleConfiguration shardingRuleConfig = new ShardingRuleConfiguration();
        PreciseShardingAlgorithm psa = new PreciseShardingAlgorithm() {
            @Override
            public String doSharding(Collection availableTargetNames, PreciseShardingValue shardingValue) {
                System.out.println(availableTargetNames);
                System.out.println(shardingValue);
                return null;
            }
        };
        ShardingStrategyConfiguration dtssc = new StandardShardingStrategyConfiguration("name",psa);
        shardingRuleConfig.setDefaultTableShardingStrategyConfig(dtssc);
        Properties props = new Properties();
        DataSource source = ShardingDataSourceFactory.createDataSource(dataSourceMap, shardingRuleConfig, props);
        ResultSet resultSet = source.getConnection().createStatement().executeQuery("select * from t");
        while (resultSet.next()){
            System.out.println(resultSet.getString(1));
        }
    }
}
