package io.quarkiverse.mybatis.deployment;

import java.util.List;

import org.apache.ibatis.session.SqlSessionManager;
import org.jboss.jandex.DotName;

import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.runtime.RuntimeValue;

/**
 * Hold the RuntimeValue of {@link SqlSessionManager}
 */
public final class SqlSessionManagerBuildItem extends MultiBuildItem {
    private final RuntimeValue<SqlSessionManager> sqlSessionManager;

    private final List<DotName> mappers;

    public SqlSessionManagerBuildItem(RuntimeValue<SqlSessionManager> sqlSessionManager, List<DotName> mappers) {
        this.sqlSessionManager = sqlSessionManager;
        this.mappers = mappers;
    }

    public RuntimeValue<SqlSessionManager> getSqlSessionManager() {
        return sqlSessionManager;
    }

    public List<DotName> getMappers() {
        return mappers;
    }
}
