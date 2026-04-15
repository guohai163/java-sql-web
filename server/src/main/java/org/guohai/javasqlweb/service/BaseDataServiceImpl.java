package org.guohai.javasqlweb.service;

import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.dao.BaseConfigDao;
import org.guohai.javasqlweb.dao.QueryLogTargetDao;
import org.guohai.javasqlweb.service.operation.DbOperation;
import org.guohai.javasqlweb.service.operation.DbOperationFactory;
import org.guohai.javasqlweb.util.AuditSqlMaskingUtils;
import org.guohai.javasqlweb.util.ReadOnlySqlGuard;
import org.guohai.javasqlweb.util.SqlTargetExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
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

    /**
     * 日志
     */
    private static final Logger LOG  = LoggerFactory.getLogger(BaseDataServiceImpl.class);
    @Autowired
    BaseConfigDao baseConfigDao;

    @Autowired
    QueryLogTargetDao queryLogTargetDao;

    @Value("${project.limit}")
    private Integer limit;
    /**
     * 服务器实例集合
     */
    private static Map<Integer, DbOperation> operationMap = new HashMap<>();

    /**
     * sql最大保存长度
     */
    private static final Integer SAVE_SQL_LENGTH_LIMIT = 8000;

    @Override
    public Result<List<ConnectConfigBean>> getAllDataConnect() {
        return new Result<>(true,"", baseConfigDao.getAllConnectConfig());
    }

    /**
     * 获取指定服务器信息
     * @return
     */
    @Override
    public Result<ConnectConfigBean> getServerInfo(Integer serverCode, UserBean user) {
        Result<ConnectConfigBean> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }
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
    public Result<List<DatabaseNameBean>> getDbName(Integer serverCode, UserBean user) {
        Result<List<DatabaseNameBean>> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }

        DbOperation operation = createDbOperation(serverCode);
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
    public Result<List<TablesNameBean>> getTableList(Integer serverCode, String dbName, UserBean user) {
        Result<List<TablesNameBean>> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }
        DbOperation operation = createDbOperation(serverCode);
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
    public Result<List<ColumnsNameBean>> getColumnList(Integer serverCode, String dbName, String tableName, UserBean user) {
        Result<List<ColumnsNameBean>> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }
        
        DbOperation operation = createDbOperation(serverCode);
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
     * 或者指定库的 表列集合
     *
     * @param serverCode
     * @param dbName
     * @return
     */
    @Override
    public Result<Map<String, String[]>> getTableColumn(Integer serverCode, String dbName, UserBean user) {
        Result<Map<String, String[]>> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }
        DbOperation operation = createDbOperation(serverCode);
        if(null != operation){
            try {
                return new Result<>(true, "", operation.getTablesColumnsMap(dbName));
            } catch (SQLException e) {
                return new Result<>(false,"",null);
            }
        }
        else{
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
    public Result<List<TableIndexesBean>> getTableIndexes(Integer serverCode, String dbName, String tableName, UserBean user) {
        Result<List<TableIndexesBean>> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }
        DbOperation operation = createDbOperation(serverCode);
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
     * 获取指定库的视图列表
     *
     * @param serverCode
     * @param dbName
     * @return
     */
    @Override
    public Result<List<ViewNameBean>> getViewList(Integer serverCode, String dbName, UserBean user) {
        Result<List<ViewNameBean>> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }
        DbOperation operation = createDbOperation(serverCode);
        if(null != operation){
            try{
                return new Result<>(true,"", operation.getViewsList(dbName));
            } catch (Exception e) {
                e.printStackTrace();
                return new Result<>(false,"",null);
            }
        }else{
            return new Result<>(false,"",null);
        }
    }

    /**
     * 获取指定视图的创建语句
     *
     * @param serverCode
     * @param dbName
     * @param viewName
     * @return
     */
    @Override
    public Result<ViewNameBean> getViewByName(Integer serverCode, String dbName, String viewName, UserBean user) {
        Result<ViewNameBean> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }
        DbOperation operation = createDbOperation(serverCode);
        if(null != operation){
            try{
                return new Result<>(true,"", operation.getView(dbName, viewName));
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
    public Result<List<StoredProceduresBean>> getSpList(Integer serverCode, String dbName, UserBean user) {
        Result<List<StoredProceduresBean>> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }
        DbOperation operation = createDbOperation(serverCode);
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
    public Result<StoredProceduresBean> getSpByName(Integer serverCode, String dbName, String spName, UserBean user) {
        Result<StoredProceduresBean> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }
        DbOperation operation = createDbOperation(serverCode);
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
    public Result<Object> quereyDataBySql(Integer serverCode, String dbName, String sql, UserBean user, String userIp) {
        Result<Object> permissionCheck = validateServerPermission(serverCode, user);
        if (permissionCheck != null) {
            return permissionCheck;
        }
        ConnectConfigBean connectConfigBean = baseConfigDao.getConnectConfig(serverCode);
        String guardResult = ReadOnlySqlGuard.validate(sql, connectConfigBean == null ? "" : connectConfigBean.getDbServerType());
        if (guardResult != null) {
            return new Result<>(false, guardResult, null);
        }
        DbOperation operation = createDbOperation(serverCode);
        if(null != operation){
            try{
                String maskedSql = AuditSqlMaskingUtils.mask(sql);
                String saveSql = maskedSql.length() > SAVE_SQL_LENGTH_LIMIT
                        ? maskedSql.substring(0, SAVE_SQL_LENGTH_LIMIT)
                        : maskedSql;
                QueryLogBean queryLog = new QueryLogBean(userIp, user.getUserName(), dbName, serverCode, saveSql, new Date());
                baseConfigDao.saveQueryLog(queryLog);
                LOG.info(maskedSql);
                Long startTime = System.currentTimeMillis();
                Object[] result = operation.queryDatabaseBySql(dbName, sql, limit);
                Integer resultRowCount = resolveResultRowCount(result);
                String returnResult = Integer.parseInt(result[0].toString())>Integer.parseInt(result[1].toString())?
                        String.format("因程序限制只显示%s条数据",result[1].toString()):
                        "";
                Long endTime = System.currentTimeMillis()-startTime;
                baseConfigDao.updateQueryLogMetrics(queryLog.getCode(), endTime.intValue(), resultRowCount);
                saveQueryTargets(queryLog.getCode(), dbName, sql);
                return new Result<>(true, returnResult, result[2]);
            } catch (Exception e) {
                e.printStackTrace();
                return new Result<>(false,e.getMessage(),null);
            }
        }else{
            return new Result<>(false,"",null);
        }
    }

    /**
     * 检查所有服务器的健康状态
     *
     * @return
     */
    @Override
    public Result<String> serverHealth() {
        StringBuilder returnMessage = new StringBuilder();
        Integer serverCode = 1;
        Boolean serverHealth = true;
        for(DbOperation value : operationMap.values()){
            try {
                value.serverHealth();
                returnMessage.append(String.format("在健康检查中服务器[%d]正常\n",serverCode));
                LOG.info(String.format("在健康检查中服务器[%d]正常",serverCode));
            } catch (SQLException throwables) {
                returnMessage.append(String.format("在健康检查中服务器[%d]发生异常%s\n",serverCode,throwables.toString()));
                LOG.error(String.format("在健康检查中服务器[%d]发生异常%s",serverCode,throwables.toString()));
                throwables.printStackTrace();
                serverHealth = false;
            }
            serverCode++;
        }
        return new Result<String>(serverHealth,"",returnMessage.toString());
    }

    /**
     * 获取数据库分组
     *
     * @return
     */
    @Override
    public Result<List<String>> getDbGroup(UserBean user) {
        if(null == user){
            return new Result<>(false,"",null);
        }
        return new Result<>(true, "", baseConfigDao.getDbGroup(user.getCode()));
    }

    /**
     * 获取指定用户可以看的列表
     *
     * @param token
     * @return
     */
    @Override
    public Result<List<ConnectConfigBean>> getHavaPermConn(UserBean user) {
        if(null == user){
            return new Result<>(false,"",null);
        }
        return new Result<>(true, "", baseConfigDao.getHavePermConnConfig(user.getCode()));
    }

    /**
     * 获取完整的向导
     *
     * @return
     */
    @Override
    public Result<List<SqlGuidBean>> getAllGuid() {
        return new Result<>(true,"", baseConfigDao.getSqlGuidAll());
    }

    /**
     * 使用单例模式创建一个数据操作实例对象
     * @param serverCode
     * @return
     */
    private DbOperation createDbOperation(Integer serverCode){
        DbOperation dbOperation = operationMap.get(serverCode);
        if(null == dbOperation){
            synchronized (BaseDataServiceImpl.class) {
                if(null == operationMap.get(serverCode)){
                    ConnectConfigBean connConfigBean = baseConfigDao.getConnectConfig(serverCode);
                    try{
                        dbOperation = DbOperationFactory.createDbOperation(connConfigBean);
                        operationMap.put(serverCode,dbOperation);
                    } catch (Exception e){
                        e.printStackTrace();
                    }

                }
            }
        }
        return dbOperation;
    }

    private <T> Result<T> validateServerPermission(Integer serverCode, UserBean user) {
        if (user == null || user.getCode() == null) {
            return new Result<>(false, "未认证用户", null);
        }
        if (!Boolean.TRUE.equals(baseConfigDao.hasServerPermission(user.getCode(), serverCode))) {
            return new Result<>(false, "无权限访问该数据库服务器", null);
        }
        return null;
    }

    private Integer resolveResultRowCount(Object[] result) {
        if (result == null || result.length < 2) {
            return 0;
        }
        Integer totalRows = parseInteger(result[0]);
        Integer displayedRows = parseInteger(result[1]);
        return Math.max(totalRows, displayedRows);
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private void saveQueryTargets(Integer queryLogCode, String dbName, String sql) {
        List<QueryLogTargetBean> targets = new ArrayList<>(SqlTargetExtractor.extract(sql, dbName));
        for (QueryLogTargetBean target : targets) {
            target.setQueryLogCode(queryLogCode);
            queryLogTargetDao.saveTarget(target);
        }
    }
}
