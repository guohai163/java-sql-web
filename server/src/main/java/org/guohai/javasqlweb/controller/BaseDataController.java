package org.guohai.javasqlweb.controller;

import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.config.AuthenticationInterceptor;
import org.guohai.javasqlweb.config.LoginRequired;
import org.guohai.javasqlweb.service.BaseDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 数据基础控制器
 * @author guohai
 */
@LoginRequired
@RestController
@RequestMapping(value = "/database")
@CrossOrigin
public class BaseDataController {
    private static final Logger LOG  = LoggerFactory.getLogger(BaseDataController.class);

    @Autowired
    BaseDataService baseDataService;


    @ResponseBody
    @RequestMapping(value = "/serverlist")
    public Result<List<ConnectConfigBean>> getAllConnect(HttpServletRequest request){
        return baseDataService.getHavaPermConn(getAuthenticatedUser(request));
    }

    /**
     * 获取指定服务器信息
     * @param serverCode
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/serverinfo/{serverCode}")
    public Result<ConnectConfigBean> getServerInfo(@PathVariable("serverCode") String serverCode,
                                                   HttpServletRequest request){
        return baseDataService.getServerInfo(Integer.parseInt(serverCode), getAuthenticatedUser(request));
    }
    /**
     * 通过DBCode获得所有库
     * @param serverCode
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/dblist/{serverCode}")
    public Result<List<DatabaseNameBean>> getAllDbName(@PathVariable("serverCode") String serverCode,
                                                       HttpServletRequest request){
        return baseDataService.getDbName(Integer.parseInt(serverCode), getAuthenticatedUser(request));
    }

    /**
     * 通过库编号+库名获得表列表
     * @param serverCode
     * @param dbName
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/tablelist/{serverCode}/{dbName}")
    public Result<List<TablesNameBean>> getTableName(@PathVariable("serverCode") String serverCode,
                                                     @PathVariable("dbName") String dbName,
                                                     HttpServletRequest request){
        return baseDataService.getTableList(Integer.parseInt(serverCode), dbName, getAuthenticatedUser(request));
    }

    /**
     * 获取指定库的所有表列集合
     * @param serverCode
     * @param dbName
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/tablecolumn/{serverCode}/{dbName}")
    public Result<Map<String, String[]>> getTableCouumn(@PathVariable("serverCode") String serverCode,
                                                        @PathVariable("dbName") String dbName,
                                                        HttpServletRequest request){
        return baseDataService.getTableColumn(Integer.parseInt(serverCode), dbName, getAuthenticatedUser(request));
    }

    /**
     * 查询 库的所有列名
     * @param serverCode
     * @param dbName
     * @param tableName
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/columnslist/{serverCode}/{dbName}/{tableName}")
    public Result<List<ColumnsNameBean>> getColumnsName(@PathVariable("serverCode") String serverCode,
                                                       @PathVariable("dbName") String dbName,
                                                       @PathVariable("tableName") String tableName,
                                                       HttpServletRequest request){
        return baseDataService.getColumnList(
                Integer.parseInt(serverCode),
                dbName,
                tableName,
                getAuthenticatedUser(request)
        );
    }

    /**
     * 查询表的所有列名
     * @param serverCode
     * @param dbName
     * @param tableName
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/indexeslist/{serverCode}/{dbName}/{tableName}")
    public Result<List<TableIndexesBean>> getIndexesName(@PathVariable("serverCode") String serverCode,
                                                         @PathVariable("dbName") String dbName,
                                                         @PathVariable("tableName") String tableName,
                                                         HttpServletRequest request){
        return baseDataService.getTableIndexes(
                Integer.parseInt(serverCode),
                dbName,
                tableName,
                getAuthenticatedUser(request)
        );
    }

    /**
     * 获取指定库的视图列表
     * @param serverCode
     * @param dbName
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/views/{serverCode}/{dbName}")
    public Result<List<ViewNameBean>> getViewList(@PathVariable("serverCode") String serverCode,
                                                  @PathVariable("dbName") String dbName,
                                                  HttpServletRequest request){
        return baseDataService.getViewList(Integer.parseInt(serverCode), dbName, getAuthenticatedUser(request));
    }

    /**
     * 获取指定库的视图
     * @param serverCode
     * @param dbName
     * @param viewName
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/views/{serverCode}/{dbName}/{viewName}")
    public Result<ViewNameBean> getView(@PathVariable("serverCode") String serverCode,
                                        @PathVariable("dbName") String dbName,
                                        @PathVariable("viewName") String viewName,
                                        HttpServletRequest request){
        return baseDataService.getViewByName(
                Integer.parseInt(serverCode),
                dbName,
                viewName,
                getAuthenticatedUser(request)
        );
    }


    /**
     * 获取存储过程列表
     * @param serverCode
     * @param dbName
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/storedprocedures/{serverCode}/{dbName}")
    public Result<List<StoredProceduresBean>> getSpList(@PathVariable("serverCode") String serverCode,
                                                        @PathVariable("dbName") String dbName,
                                                        HttpServletRequest request){
        return baseDataService.getSpList(Integer.parseInt(serverCode), dbName, getAuthenticatedUser(request));
    }

    /**
     * 获取指定存储过程
     * @param serverCode
     * @param dbName
     * @param spName
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/storedprocedures/{serverCode}/{dbName}/{spName}")
    public Result<StoredProceduresBean> getSpByName(@PathVariable("serverCode") String serverCode,
                                                    @PathVariable("dbName") String dbName,
                                                    @PathVariable("spName") String spName,
                                                    HttpServletRequest request){
        return baseDataService.getSpByName(
                Integer.parseInt(serverCode),
                dbName,
                spName,
                getAuthenticatedUser(request)
        );
    }
    /**
     * 执行业务查询
     * @param serverCode
     * @param dbName
     * @param sql
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/query/{serverCode}/{dbName}", method = RequestMethod.POST)
    public Result<Object> quereyData(@PathVariable("serverCode") String serverCode,
                                     @PathVariable("dbName") String dbName,
                                     HttpServletRequest request,
                                     @RequestBody String sql){
        return baseDataService.quereyDataBySql(
                Integer.parseInt(serverCode),
                dbName,
                sql,
                getAuthenticatedUser(request),
                request.getRemoteAddr()
        );
    }

    @ResponseBody
    @RequestMapping(value = "/server/group")
    public Result<List<String>> getDbGroup(HttpServletRequest request){
        return baseDataService.getDbGroup(getAuthenticatedUser(request));
    }

    private UserBean getAuthenticatedUser(HttpServletRequest request) {
        return (UserBean) request.getAttribute(AuthenticationInterceptor.AUTHENTICATED_USER_ATTR);
    }

}
