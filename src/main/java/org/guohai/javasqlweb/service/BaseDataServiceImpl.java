package org.guohai.javasqlweb.service;

import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.dao.UserManageDao;
import org.guohai.javasqlweb.dao.BaseConfigDao;
import org.guohai.javasqlweb.service.operation.DBOperation;
import org.guohai.javasqlweb.service.operation.DBOperationFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础数据操作服务类
 * @author guohai
 * @date 2020-12-1
 */
@Service
public class BaseDataServiceImpl implements BaseDataService{

    @Autowired
    BaseConfigDao baseConfigDao;

    @Autowired
    UserManageDao adminDao;

    /**
     * 服务器实例集合
     */
    private static Map<Integer,DBOperation> operationMap = new HashMap<>();

    @Override
    public Result<List<ConnectConfigBean>> getAllDataConnect() {
        return new Result<>(true,"", baseConfigDao.getAllConnectConfig());
    }

    /**
     * 获取指定服务器信息
     * @return
     */
    @Override
    public Result<ConnectConfigBean> getServerInfo(Integer serverCode) {
        ConnectConfigBean connBean = baseConfigDao.getConnectConfig(serverCode);
        connBean.setDbServerPassword("");
        connBean.setDbServerUsername("");
        connBean.setDbServerHost("");
        return new Result<>(true,"成功", connBean);
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
                return new Result<>(true,"", operation.getDbList());
            } catch (Exception e) {
                e.printStackTrace();
                return new Result<>(false,e.toString(),null);
            }
        }else{
            return new Result<>(false,"没有找到对应 的数据库",null);
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
                return new Result<>(true,"", operation.getTableList(dbName));
            } catch (Exception e) {
                e.printStackTrace();
                return new Result<>(false,"",null);
            }
        }else{
            return new Result<>(false,"",null);
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
                return new Result<>(true,"", operation.getColumnsList(dbName, tableName));
            } catch (Exception e) {
                e.printStackTrace();
                return new Result<>(false,"",null);
            }
        }else{
            return new Result<>(false,"",null);
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
                return new Result<>(true,"", operation.getIndexesList(dbName, tableName));
            } catch (Exception e) {
                e.printStackTrace();
                return new Result<>(false,"",null);
            }
        }else{
            return new Result<>(false,"",null);
        }
    }

    /**
     * 获取指定库的存储过程列表,只含名字
     *
     * @param serverCode
     * @param dbName
     * @return
     */
    @Override
    public Result<List<StoredProceduresBean>> getSpList(Integer serverCode, String dbName) {
        DBOperation operation = createDbOperation(serverCode);
        if(null != operation){
            try{
                return new Result<>(true,"", operation.getStoredProceduresList(dbName));
            } catch (Exception e) {
                e.printStackTrace();
                return new Result<>(false,"",null);
            }
        }else{
            return new Result<>(false,"",null);
        }
    }

    /**
     * 通过存储过程名获取存储过程内容
     *
     * @param serverCode
     * @param dbName
     * @param spName
     * @return
     */
    @Override
    public Result<StoredProceduresBean> getSpByName(Integer serverCode, String dbName, String spName) {
        DBOperation operation = createDbOperation(serverCode);
        if(null != operation){
            try{
                return new Result<>(true,"", operation.getStoredProcedure(dbName, spName));
            } catch (Exception e) {
                e.printStackTrace();
                return new Result<>(false,"",null);
            }
        }else{
            return new Result<>(false,"",null);
        }
    }

    /**
     * 执行查询语句
     *
     * @param serverCode
     * @param dbName
     * @param sql
     * @param userIp
     * @return
     */
    @Override
    public Result<Object> quereyDataBySql(Integer serverCode, String dbName, String sql, String token, String userIp) {
        UserBean user = adminDao.getUserByToken(token);
        if(null == user){
            return new Result<>(false,"",null);
        }
        DBOperation operation = createDbOperation(serverCode);
        if(null != operation){
            try{
                baseConfigDao.saveQueryLog(user.getUserName(),dbName,sql, userIp,new Date());
                return new Result<>(true,"", operation.queryDatabaseBySql(dbName, sql));
            } catch (Exception e) {
                e.printStackTrace();
                return new Result<>(false,e.getMessage(),null);
            }
        }else{
            return new Result<>(false,"",null);
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
