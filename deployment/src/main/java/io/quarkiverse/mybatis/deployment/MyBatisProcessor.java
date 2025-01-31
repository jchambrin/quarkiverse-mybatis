package io.quarkiverse.mybatis.deployment;

import java.util.*;
import java.util.stream.Collectors;

import javax.inject.Singleton;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.javassist.util.proxy.ProxyFactory;
import org.apache.ibatis.logging.log4j.Log4jImpl;
import org.apache.ibatis.scripting.defaults.RawLanguageDriver;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;

import io.quarkiverse.mybatis.runtime.MyBatisProducers;
import io.quarkiverse.mybatis.runtime.MyBatisRecorder;
import io.quarkiverse.mybatis.runtime.MyBatisRuntimeConfig;
import io.quarkus.agroal.spi.JdbcDataSourceBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageProxyDefinitionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.deployment.configuration.ConfigurationError;

class MyBatisProcessor {

    private static final Logger LOG = Logger.getLogger(MyBatisProcessor.class);
    private static final String FEATURE = "mybatis";
    private static final DotName MYBATIS_MAPPER = DotName.createSimple(Mapper.class.getName());

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void runtimeInitialzed(BuildProducer<RuntimeInitializedClassBuildItem> runtimeInit) {
        runtimeInit.produce(new RuntimeInitializedClassBuildItem(Log4jImpl.class.getName()));
    }

    @BuildStep
    void reflectiveClasses(BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        reflectiveClass.produce(new ReflectiveClassBuildItem(false, false,
                ProxyFactory.class,
                XMLLanguageDriver.class,
                RawLanguageDriver.class));

        reflectiveClass.produce(new ReflectiveClassBuildItem(true, true,
                PerpetualCache.class, LruCache.class));
    }

    @BuildStep
    void addMyBatisMappers(BuildProducer<MyBatisMapperBuildItem> mappers,
            BuildProducer<ReflectiveClassBuildItem> reflective,
            BuildProducer<NativeImageProxyDefinitionBuildItem> proxy,
            CombinedIndexBuildItem indexBuildItem) {
        for (AnnotationInstance i : indexBuildItem.getIndex().getAnnotations(MYBATIS_MAPPER)) {
            if (i.target().kind() == AnnotationTarget.Kind.CLASS) {
                DotName dotName = i.target().asClass().name();
                mappers.produce(new MyBatisMapperBuildItem(dotName));
                reflective.produce(new ReflectiveClassBuildItem(true, false, dotName.toString()));
                proxy.produce(new NativeImageProxyDefinitionBuildItem(dotName.toString()));
            }
        }
    }

    @BuildStep
    MyBatisMapperConfigBuildItem categorizeMappers(MyBatisRuntimeConfig myBatisRuntimeConfig,
            List<MyBatisMapperBuildItem> mappers) {
        Map<String, List<DotName>> mappersMap = new HashMap<>();
        List<DotName> alreadyInList = new ArrayList<>();
        if (myBatisRuntimeConfig.dataSourceMap.size() > 0) {
            myBatisRuntimeConfig.dataSourceMap.forEach((datasource, config) -> {
                List<DotName> dsMappers = mappers.stream()
                        .filter(m -> m.getMapperName().toString().startsWith(config.packages))
                        .map(MyBatisMapperBuildItem::getMapperName)
                        .collect(Collectors.toList());
                alreadyInList.addAll(dsMappers);
                mappersMap.put(datasource, dsMappers);
            });
        }
        mappersMap.put("default", mappers.stream().filter(m -> !alreadyInList.contains(m.getMapperName()))
                .map(MyBatisMapperBuildItem::getMapperName).collect(Collectors.toList()));

        return new MyBatisMapperConfigBuildItem(mappersMap);
    }

    @BuildStep
    void unremovableBeans(BuildProducer<AdditionalBeanBuildItem> beanProducer) {
        beanProducer.produce(AdditionalBeanBuildItem.unremovableOf(MyBatisProducers.class));
    }

    @BuildStep
    void initalSql(BuildProducer<NativeImageResourceBuildItem> resource, MyBatisRuntimeConfig config) {
        if (config.initialSql.isPresent()) {
            resource.produce(new NativeImageResourceBuildItem(config.initialSql.get()));
        }
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    SqlSessionFactoryBuildItem generateSqlSessionFactory(MyBatisRuntimeConfig myBatisRuntimeConfig,
            MyBatisMapperConfigBuildItem myBatisMapperConfigBuildItem,
            List<JdbcDataSourceBuildItem> jdbcDataSourcesBuildItem,
            MyBatisRecorder recorder) {
        List<DotName> mappers = myBatisMapperConfigBuildItem.getMappersMap().get("default");

        String dataSourceName = null;
        if (myBatisRuntimeConfig.dataSource.isPresent()) {
            dataSourceName = myBatisRuntimeConfig.dataSource.get();
            String finalDataSourceName = dataSourceName;
            Optional<JdbcDataSourceBuildItem> jdbcDataSourceBuildItem = jdbcDataSourcesBuildItem.stream()
                    .filter(i -> i.getName().equals(finalDataSourceName))
                    .findFirst();
            if (!jdbcDataSourceBuildItem.isPresent()) {
                throw new ConfigurationError("Can not find datasource " + dataSourceName);
            }
        } else {
            Optional<JdbcDataSourceBuildItem> defaultJdbcDataSourceBuildItem = jdbcDataSourcesBuildItem.stream()
                    .filter(i -> i.isDefault())
                    .findFirst();
            if (defaultJdbcDataSourceBuildItem.isPresent()) {
                dataSourceName = defaultJdbcDataSourceBuildItem.get().getName();
            }
        }

        if (dataSourceName != null) {
            return new SqlSessionFactoryBuildItem(recorder.createSqlSessionFactory(
                    myBatisRuntimeConfig.environment,
                    myBatisRuntimeConfig.transactionFactory,
                    dataSourceName,
                    mappers.stream().map(DotName::toString).collect(Collectors.toList())), mappers);
        }

        return null;
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void generateSqlSessionFactory(MyBatisRuntimeConfig myBatisRuntimeConfig,
            MyBatisMapperConfigBuildItem myBatisMapperConfigBuildItem,
            List<JdbcDataSourceBuildItem> jdbcDataSourcesBuildItem,
            BuildProducer<SqlSessionFactoryBuildItem> sqlSessionFactoryBuildItemBuildProducer,
            MyBatisRecorder recorder) {

        if (myBatisRuntimeConfig.dataSourceMap.size() > 0) {
            myBatisRuntimeConfig.dataSourceMap.forEach((datasource, config) -> {
                List<DotName> mappers = myBatisMapperConfigBuildItem.getMappersMap().get(datasource);
                String dataSourceName = config.datasource != null ? config.datasource : datasource;
                Optional<JdbcDataSourceBuildItem> jdbcDataSourceBuildItem = jdbcDataSourcesBuildItem.stream()
                        .filter(i -> i.getName().equals(dataSourceName))
                        .findFirst();
                if (!jdbcDataSourceBuildItem.isPresent()) {
                    throw new ConfigurationError("Can not find datasource " + dataSourceName);
                }
                sqlSessionFactoryBuildItemBuildProducer.produce(new SqlSessionFactoryBuildItem(recorder.createSqlSessionFactory(
                        myBatisRuntimeConfig.environment,
                        myBatisRuntimeConfig.transactionFactory,
                        dataSourceName,
                        mappers.stream().map(DotName::toString).collect(Collectors.toList())), mappers));
            });
        }

    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void generateSqlSessionManager(List<SqlSessionFactoryBuildItem> sqlSessionFactoryBuildItems,
            BuildProducer<SqlSessionManagerBuildItem> SqlSessionManagerBuildItemBuildProducer,
            MyBatisRecorder recorder) {
        for (SqlSessionFactoryBuildItem sqlSessionFactoryBuildItem : sqlSessionFactoryBuildItems) {
            SqlSessionManagerBuildItemBuildProducer.produce(new SqlSessionManagerBuildItem(recorder.createSqlSessionManager(
                    sqlSessionFactoryBuildItem.getSqlSessionFactory()), sqlSessionFactoryBuildItem.getMappers()));
        }
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void generateMapperBeans(MyBatisRecorder recorder,
            List<SqlSessionManagerBuildItem> sqlSessionManagerBuildItems,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer) {

        for (SqlSessionManagerBuildItem sqlSessionManagerBuildItem : sqlSessionManagerBuildItems) {
            for (DotName mapper : sqlSessionManagerBuildItem.getMappers()) {
                SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                        .configure(mapper)
                        .scope(Singleton.class)
                        .setRuntimeInit()
                        .unremovable()
                        .supplier(recorder.MyBatisMapperSupplier(mapper.toString(),
                                sqlSessionManagerBuildItem.getSqlSessionManager()));
                syntheticBeanBuildItemBuildProducer.produce(configurator.done());
            }
        }
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void register(List<SqlSessionFactoryBuildItem> sqlSessionFactoryBuildItems,
            BeanContainerBuildItem beanContainerBuildItem,
            MyBatisRecorder recorder) {
        for (SqlSessionFactoryBuildItem sqlSessionFactoryBuildItem : sqlSessionFactoryBuildItems) {
            recorder.register(sqlSessionFactoryBuildItem.getSqlSessionFactory(), beanContainerBuildItem.getValue());
        }
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void runInitialSql(List<SqlSessionFactoryBuildItem> sqlSessionFactoryBuildItems,
            MyBatisRuntimeConfig myBatisRuntimeConfig,
            MyBatisRecorder recorder) {
        if (myBatisRuntimeConfig.initialSql.isPresent()) {
            // recorder.runInitialSql(sqlSessionFactoryBuildItem.getSqlSessionFactory(), myBatisRuntimeConfig.initialSql.get());
        }
    }
}
