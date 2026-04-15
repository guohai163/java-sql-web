package org.guohai.javasqlweb.dao;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.guohai.javasqlweb.beans.DashboardObjectHotspotItem;
import org.guohai.javasqlweb.beans.DashboardRecentQueryItem;
import org.guohai.javasqlweb.beans.DashboardSummary;
import org.guohai.javasqlweb.beans.DashboardTrendPoint;
import org.guohai.javasqlweb.beans.DashboardUserRankingItem;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * Dashboard aggregate queries.
 */
@Repository
public interface DashboardDao {

    @Select("SELECT COUNT(*) AS total_users, " +
            "SUM(CASE WHEN create_time >= #{startTime} AND create_time < #{endTime} THEN 1 ELSE 0 END) AS new_users " +
            "FROM user_tb")
    DashboardSummary getUserSummary(@Param("startTime") Date startTime, @Param("endTime") Date endTime);

    @Select({
            "<script>",
            "SELECT ${bucketExpr} AS time_bucket, ",
            "COUNT(*) AS query_count, ",
            "COALESCE(SUM(result_row_count), 0) AS total_returned_rows ",
            "FROM db_query_log ",
            "WHERE query_time &gt;= #{startTime} AND query_time &lt; #{endTime} ",
            "GROUP BY ${bucketExpr} ",
            "ORDER BY MIN(query_time) ASC",
            "</script>"
    })
    List<DashboardTrendPoint> getTrend(@Param("startTime") Date startTime,
                                       @Param("endTime") Date endTime,
                                       @Param("bucketExpr") String bucketExpr);

    @Select("SELECT l.query_name AS user_name, " +
            "COUNT(DISTINCT l.code) AS query_count, " +
            "COALESCE(SUM(l.result_row_count), 0) AS total_returned_rows, " +
            "COUNT(DISTINCT l.query_database) AS database_count, " +
            "COUNT(DISTINCT CONCAT(COALESCE(t.database_name, l.query_database), '.', t.table_name)) AS table_count, " +
            "AVG(l.query_consuming) AS average_query_consuming " +
            "FROM db_query_log l " +
            "LEFT JOIN db_query_log_target_tb t ON t.query_log_code = l.code " +
            "WHERE l.query_time >= #{startTime} AND l.query_time < #{endTime} " +
            "GROUP BY l.query_name " +
            "ORDER BY query_count DESC, total_returned_rows DESC " +
            "LIMIT #{limit}")
    List<DashboardUserRankingItem> getUserRanking(@Param("startTime") Date startTime,
                                                  @Param("endTime") Date endTime,
                                                  @Param("limit") Integer limit);

    @Select("SELECT CONCAT(COALESCE(c.db_server_name, '未知实例'), ' / ', l.query_database) AS object_name, " +
            "c.db_server_name AS server_name, " +
            "l.query_database AS database_name, " +
            "COUNT(*) AS query_count, " +
            "COALESCE(SUM(l.result_row_count), 0) AS total_returned_rows " +
            "FROM db_query_log l " +
            "LEFT JOIN db_connect_config_tb c ON c.code = l.server_code " +
            "WHERE l.query_time >= #{startTime} AND l.query_time < #{endTime} " +
            "GROUP BY l.server_code, c.db_server_name, l.query_database " +
            "ORDER BY query_count DESC, total_returned_rows DESC " +
            "LIMIT #{limit}")
    List<DashboardObjectHotspotItem> getDatabaseHotspots(@Param("startTime") Date startTime,
                                                         @Param("endTime") Date endTime,
                                                         @Param("limit") Integer limit);

    @Select("SELECT CONCAT(COALESCE(t.database_name, l.query_database), '.', t.table_name) AS object_name, " +
            "c.db_server_name AS server_name, " +
            "COALESCE(t.database_name, l.query_database) AS database_name, " +
            "t.table_name AS table_name, " +
            "COUNT(DISTINCT l.code) AS query_count, " +
            "COALESCE(SUM(l.result_row_count), 0) AS total_returned_rows " +
            "FROM db_query_log_target_tb t " +
            "JOIN db_query_log l ON l.code = t.query_log_code " +
            "LEFT JOIN db_connect_config_tb c ON c.code = l.server_code " +
            "WHERE l.query_time >= #{startTime} AND l.query_time < #{endTime} " +
            "GROUP BY c.db_server_name, COALESCE(t.database_name, l.query_database), t.table_name " +
            "ORDER BY query_count DESC, total_returned_rows DESC " +
            "LIMIT #{limit}")
    List<DashboardObjectHotspotItem> getTableHotspots(@Param("startTime") Date startTime,
                                                      @Param("endTime") Date endTime,
                                                      @Param("limit") Integer limit);

    @Select("SELECT l.query_time, l.query_name, c.db_server_name AS server_name, l.query_database, " +
            "COALESCE(GROUP_CONCAT(DISTINCT CONCAT(COALESCE(t.database_name, l.query_database), '.', t.table_name) " +
            "ORDER BY t.table_name SEPARATOR ', '), '-') AS target_tables, " +
            "l.result_row_count, l.query_consuming, l.query_sqlscript " +
            "FROM db_query_log l " +
            "LEFT JOIN db_connect_config_tb c ON c.code = l.server_code " +
            "LEFT JOIN db_query_log_target_tb t ON t.query_log_code = l.code " +
            "WHERE l.query_time >= #{startTime} AND l.query_time < #{endTime} " +
            "GROUP BY l.code, l.query_time, l.query_name, c.db_server_name, l.query_database, l.result_row_count, l.query_consuming, l.query_sqlscript " +
            "ORDER BY l.query_time DESC " +
            "LIMIT #{limit}")
    List<DashboardRecentQueryItem> getRecentQueries(@Param("startTime") Date startTime,
                                                    @Param("endTime") Date endTime,
                                                    @Param("limit") Integer limit);

    @Select("SELECT COUNT(*) FROM db_query_log WHERE query_time >= #{startTime} AND query_time < #{endTime}")
    Long countQueries(@Param("startTime") Date startTime, @Param("endTime") Date endTime);

    @Select("SELECT COALESCE(SUM(result_row_count), 0) FROM db_query_log WHERE query_time >= #{startTime} AND query_time < #{endTime}")
    Long sumResultRows(@Param("startTime") Date startTime, @Param("endTime") Date endTime);

    @Select("SELECT AVG(query_consuming) FROM db_query_log WHERE query_time >= #{startTime} AND query_time < #{endTime}")
    Double avgQueryConsuming(@Param("startTime") Date startTime, @Param("endTime") Date endTime);
}
