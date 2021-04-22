package org.guohai.javasqlweb.beans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.Date;

/**
 * 查询日志实体
 * @author guohai
 */
@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class QueryLogBean {

    /**
     * 主键
     */
    private Integer code;

    /**
     * 查询者IP
     */
    private final String queryIp;

    /**
     * 查询人
     */
    private final String queryName;

    /**
     * 执行查询时的库
     */
    private final String queryDatabase;
    /**
     * 查询脚本
     */
    private final String querySqlscript;

    /**
     * 查询消耗
     */
    private Integer queryConsuming;

    /**
     * 查询时间
     */
    private final Date queryTime;
}
