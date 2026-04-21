package org.guohai.javasqlweb.beans;

import lombok.Data;

import java.util.Date;

/**
 * 查询日志实体
 * @author guohai
 */
@Data
public class QueryLogBean {

    /**
     * 主键
     */
    private Integer code;

    /**
     * 查询者IP
     */
    private String queryIp;

    /**
     * 查询人
     */
    private String queryName;

    /**
     * 执行查询时的库
     */
    private String queryDatabase;

    /**
     * 查询实例编号
     */
    private Integer serverCode;

    /**
     * 查询实例名称
     */
    private String serverName;

    /**
     * 数据库会话ID
     */
    private String dbSessionId;

    /**
     * 查询脚本
     */
    private String querySqlscript;

    /**
     * 查询消耗
     */
    private Integer queryConsuming;

    /**
     * 返回条数
     */
    private Integer resultRowCount;

    /**
     * 查询时间
     */
    private Date queryTime;

    /**
     * 目标表摘要
     */
    private String targetTables;

    public QueryLogBean() {
    }

    public QueryLogBean(String queryIp,
                        String queryName,
                        String queryDatabase,
                        Integer serverCode,
                        String querySqlscript,
                        Date queryTime) {
        this.queryIp = queryIp;
        this.queryName = queryName;
        this.queryDatabase = queryDatabase;
        this.serverCode = serverCode;
        this.querySqlscript = querySqlscript;
        this.queryTime = queryTime;
    }
}
