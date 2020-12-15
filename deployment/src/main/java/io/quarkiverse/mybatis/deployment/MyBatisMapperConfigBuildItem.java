package io.quarkiverse.mybatis.deployment;

import java.util.List;
import java.util.Map;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.SimpleBuildItem;

public final class MyBatisMapperConfigBuildItem extends SimpleBuildItem {

    private final Map<String, List<DotName>> mappersMap;

    public MyBatisMapperConfigBuildItem(Map<String, List<DotName>> mappersMap) {
        this.mappersMap = mappersMap;
    }

    public Map<String, List<DotName>> getMappersMap() {
        return mappersMap;
    }
}
