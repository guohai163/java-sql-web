package org.guohai.javasqlweb.service;

import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.guohai.javasqlweb.beans.QueryLogBean;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.dao.BaseConfigDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 后台服务
 */
@Service
public class BackstageServiceImpl implements BackstageService{
    @Autowired
    BaseConfigDao baseConfigDao;
    /**
     * 获取查询日志
     *
     * @return
     */
    @Override
    public Result<List<QueryLogBean>> getQueryLog() {

        return new Result<>(true,"", baseConfigDao.getQueryLog());
    }

    /**
     * 获取连接表
     *
     * @return
     */
    @Override
    public Result<List<ConnectConfigBean>> getConnData() {
        List<ConnectConfigBean> listConn = baseConfigDao.getConnData();

        return new Result<>(true, "", listConn);
    }
}
