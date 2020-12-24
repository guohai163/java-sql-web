package org.guohai.javasqlweb.service;

import org.guohai.javasqlweb.beans.QueryLogBean;
import org.guohai.javasqlweb.beans.Result;

import java.util.List;

/**
 * 后台服务
 * @author guohai
 */
public interface BackstageService {

    /**
     * 获取查询日志
     * @return
     */
    Result<List<QueryLogBean>> getQueryLog();
}
