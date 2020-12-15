package io.quarkiverse.mybatis.runtime;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

@ConfigGroup
public class MyBatisDSConfig {

    /**
     * datasource
     */
    @ConfigItem(name = "datasource")
    public String datasource;

    /**
     * packages
     */
    @ConfigItem(name = "packages")
    public String packages;

}
