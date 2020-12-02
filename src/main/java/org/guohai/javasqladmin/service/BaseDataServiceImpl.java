package org.guohai.javasqladmin.service;

import org.guohai.javasqladmin.beans.*;
import org.guohai.javasqladmin.dao.BaseConfigDao;
import org.guohai.javasqladmin.service.operation.DBOperation;
import org.guohai.javasqladmin.service.operation.DBOperationFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BaseDataServiceImpl implements BaseDataService{

    @Autowired
    BaseConfigDao baseConfigDao;

    /**
     * 服务器实例集合
     */
    private static Map<Integer,DBOperation> operationMap = new HashMap<>();

    @Override
    public Result<List<ConnectConfigBean>> getAllDataConnect() {
        return new Result<>(true, baseConfigDao.getAllConnectConfig());
    }

    /**
     * 活的指定DB服务器的库名列表
     *
     * @param serverCode
     * @return
     */
    @Override
    public Result<List<DatabaseNameBean>> getDbName(Integer serverCode) {

        DBOperation operation = createDbOperation(serverCode);
        if(null != operation){
            try{
                return new Result<>(true, operation.getDbList());
            } catch (Exception e) {
                e.printStackTrace();
                return new Result<>(false,null);
            }
        }else{
            return new Result<>(false,null);
        }

    }

    /**
     * 获得指定库的所有表名
     *
     * @param serverCode
     * @param dbName
     * @return
     */
    @Override
    public Result<List<TablesNameBean>> getTableList(Integer serverCode, String dbName) {
        DBOperation operation = createDbOperation(serverCode);
        if(null != operation){
            try{
                return new Result<>(true, operation.getTableList(dbName));
            } catch (Exception e) {
                e.printStackTrace();
                return new Result<>(false,null);
            }
        }else{
            return new Result<>(false,null);
        }
    }

    /**
     * 获取所有列名
     * @param serverCode
     * @param dbName
     * @param tableName
     * @return
     */
    @Override
    public Result<List<ColumnsNameBean>> getColumnList(Integer serverCode, String dbName, String tableName) {
        
        DBOperation operation = createDbOperation(serverCode);
        if(null != operation){
            try{
                return new Result<>(true, operation.getColumnsList(dbName, tableName));
            } catch (Exception e) {
                e.printStackTrace();
                return new Result<>(false,null);
            }
        }else{
            return new Result<>(false,null);
        }
    }

    /**
     * 获得指定表的所有索引
     *
     * @param serverCode
     * @param dbName
     * @param tableName
     * @return
     */
    @Override
    public Result<List<TableIndexesBean>> getTableIndexes(Integer serverCode, String dbName, String tableName) {
        DBOperation operation = createDbOperation(serverCode);
        if(null != operation){
            try{
                return new Result<>(true, operation.getIndexesList(dbName, tableName));
            } catch (Exception e) {
                e.printStackTrace();
                return new Result<>(false,null);
            }
        }else{
            return new Result<>(false,null);
        }
    }

    /**
     * 执行查询语句
     *
     * @param serverCode
     * @param dbName
     * @param sql
     * @return
     */
    @Override
    public Result<Object> quereyDataBySql(Integer serverCode, String dbName, String sql) {
        DBOperation operation = createDbOperation(serverCode);
        if(null != operation){
            try{
                return new Result<>(true, operation.queryDatabaseBySql(dbName, sql));
            } catch (Exception e) {
                e.printStackTrace();
                return new Result<>(false,null);
            }
        }else{
            return new Result<>(false,null);
        }
    }

    /**
     * 使用单例模式创建一个数据操作实例对象
     * @param serverCode
     * @return
     */
    private DBOperation createDbOperation(Integer serverCode){
        DBOperation dbOperation = operationMap.get(serverCode);
        if(null == dbOperation){
            synchronized (BaseDataServiceImpl.class) {
                if(null == operationMap.get(serverCode)){
                    ConnectConfigBean connConfigBean = baseConfigDao.getConnectConfig(serverCode);
                    try{
                        dbOperation = DBOperationFactory.createDbOperation(connConfigBean);
                        operationMap.put(serverCode,dbOperation);
                    } catch (Exception e){
                        e.printStackTrace();
                    }

                }
            }
        }
        return dbOperation;
    }
}
