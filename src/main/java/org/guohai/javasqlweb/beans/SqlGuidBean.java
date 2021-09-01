package org.guohai.javasqlweb.beans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.Date;

/**
 * SQL向导的实体
 * @author guohai
 */
@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class SqlGuidBean {
    private Integer code;
    /**
     * 分类
     */
    private String category;
    /**
     * 标题
     */
    private String title;
    /**
     * SQL语句
     */
    private String script;
    /**
     * 服务器
     */
    private String server;
    /**
     * 数据库
     */
    private String database;
    /**
     * 时间
     */
    private Date createDate;
}
