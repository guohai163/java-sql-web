package org.guohai.javasqladmin.service;

import org.guohai.javasqladmin.beans.ConnectConfigBean;
import org.guohai.javasqladmin.beans.DatabaseNameBean;
import org.guohai.javasqladmin.beans.Result;

import java.util.List;

public interface BaseDataService {

    /**
     * 返回完整的数据
     * @return
     */
    Result<List<ConnectConfigBean>> getAllDataConnect();

    /**
     * 活的指定DB服务器的库名列表
     * @param dbCode
     * @return
     */
    Result<List<DatabaseNameBean>> getDbName(Integer dbCode);
}
