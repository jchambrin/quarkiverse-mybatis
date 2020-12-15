package io.quarkiverse.mybatis.deployment;

import java.util.List;

import org.apache.ibatis.session.SqlSessionFactory;
import org.jboss.jandex.DotName;

import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.runtime.RuntimeValue;

/**
 * Hold the RuntimeValue of {@link SqlSessionFactory}
 */
public final class SqlSessionFactoryBuildItem extends MultiBuildItem {

    private final RuntimeValue<SqlSessionFactory> sqlSessionFactory;

    private final List<DotName> mappers;

    public SqlSessionFactoryBuildItem(RuntimeValue<SqlSessionFactory> sqlSessionFactory, List<DotName> mappers) {
        this.sqlSessionFactory = sqlSessionFactory;
        this.mappers = mappers;
    }

    public RuntimeValue<SqlSessionFactory> getSqlSessionFactory() {
        return sqlSessionFactory;
    }

    public List<DotName> getMappers() {
        return mappers;
    }
}
