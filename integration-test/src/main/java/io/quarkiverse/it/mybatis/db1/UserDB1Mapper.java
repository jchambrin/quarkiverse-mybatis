package io.quarkiverse.it.mybatis.db1;

import org.apache.ibatis.annotations.*;

import io.quarkiverse.it.mybatis.User;

@Mapper
@CacheNamespace(readWrite = false)
public interface UserDB1Mapper {

    @Select("select * from users where id = #{id}")
    User getUser(Integer id);

    @Insert("insert into users (id, name) values (#{id}, #{name})")
    Integer createUser(@Param("id") Integer id, @Param("name") String name);

    @Delete("delete from users where id = #{id}")
    Integer removeUser(Integer id);
}
