package org.guohai.javasqlweb.dao;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.guohai.javasqlweb.beans.QueryLogTargetBean;
import org.springframework.stereotype.Repository;

/**
 * Query log target dao.
 */
@Repository
public interface QueryLogTargetDao {

    @Insert("INSERT INTO db_query_log_target_tb (query_log_code,database_name,table_name) " +
            "VALUES (#{target.queryLogCode},#{target.databaseName},#{target.tableName})")
    @Options(useGeneratedKeys = true, keyProperty = "code", keyColumn = "code")
    Boolean saveTarget(@Param("target") QueryLogTargetBean target);
}
