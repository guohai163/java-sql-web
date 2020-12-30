package org.guohai.javasqlweb.beans;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

/**
 * 查询日志实体
 * @author guohai
 */
@Data
@AllArgsConstructor
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
     * 查询脚本
     */
    private String querySqlscript;

    /**
     * 查询时间
     */
    private Date queryTime;
}
