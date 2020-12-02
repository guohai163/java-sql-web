package org.guohai.javasqladmin.service;

import org.guohai.javasqladmin.beans.*;
import org.guohai.javasqladmin.dao.BaseConfigDao;
import org.guohai.javasqladmin.service.operation.DBOperation;
import org.guohai.javasqladmin.service.operation.DBOperationFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;

@Service
public class BaseDataServiceImpl implements BaseDataService{

    @Autowired
    BaseConfigDao baseConfigDao;

    @Override
    public Result<List<ConnectConfigBean>> getAllDataConnect() {
        return new Result<>(true, baseConfigDao.getAllConnectConfig());
    }

    /**
     * 活的指定DB服务器的库名列表
     *
     * @param dbCode
     * @return
     */
    @Override
    public Result<List<DatabaseNameBean>> getDbName(Integer dbCode) {
        ConnectConfigBean connConfigBean = baseConfigDao.getConnectConfig(dbCode);
        DBOperation operation = null;
        try {
            operation = DBOperationFactory.createDBOperation(connConfigBean);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        try {
            return new Result<>(true, operation.getDBList());
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return new Result<>(false,null);
    }

    /**
     * 获得指定库的所有表名
     *
     * @param dbCode
     * @param dbName
     * @return
     */
    @Override
    public Result<List<TablesNameBean>> getTableList(Integer dbCode, String dbName) {
        ConnectConfigBean connConfigBean = baseConfigDao.getConnectConfig(dbCode);
        DBOperation operation = null;
        try {
            operation = DBOperationFactory.createDBOperation(connConfigBean);
            return new Result<>(true, operation.getTableList(dbName));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new Result<>(false,null);
    }

    /**
     * @param dbCode
     * @param dbName
     * @param tableName
     * @return
     */
    @Override
    public Result<List<ColumnsNameBean>> getColumnList(Integer dbCode, String dbName, String tableName) {
        ConnectConfigBean connConfigBean = baseConfigDao.getConnectConfig(dbCode);
        DBOperation operation = null;
        try {
            operation = DBOperationFactory.createDBOperation(connConfigBean);
            return new Result<>(true, operation.getColumnsList(dbName, tableName));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new Result<>(false,null);
    }
}
