package org.guohai.javasqlweb.controller;

import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.config.LoginRequired;
import org.guohai.javasqlweb.service.BaseDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

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
    public Result<List<ConnectConfigBean>> getAllConnect(@RequestHeader(value = "User-Token", required =  false) String token){
        return baseDataService.getHavaPermConn(token);
    }

    /**
     * 获取指定服务器信息
     * @param serverCode
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/serverinfo/{serverCode}")
    public Result<ConnectConfigBean> getServerInfo(@PathVariable("serverCode") String serverCode){
        return baseDataService.getServerInfo(Integer.parseInt(serverCode));
    }
    /**
     * 通过DBCode获得所有库
     * @param serverCode
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/dblist/{serverCode}")
    public Result<List<DatabaseNameBean>> getAllDbName(@PathVariable("serverCode") String serverCode){
        return baseDataService.getDbName(Integer.parseInt(serverCode));
    }

    /**
     * 通过库编号+库名获得表列表
     * @param serverCode
     * @param dbName
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/tablelist/{serverCode}/{dbName}")
    public Result<List<TablesNameBean>> getTableName(@PathVariable("serverCode") String serverCode, @PathVariable("dbName") String dbName){
        return baseDataService.getTableList(Integer.parseInt(serverCode),dbName);
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
                                                       @PathVariable("tableName") String tableName){
        return baseDataService.getColumnList(Integer.parseInt(serverCode), dbName, tableName);
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
                                                         @PathVariable("tableName") String tableName){
        return baseDataService.getTableIndexes(Integer.parseInt(serverCode), dbName, tableName);
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
                                                  @PathVariable("dbName") String dbName){
        return baseDataService.getViewList(Integer.parseInt(serverCode), dbName);
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
                                        @PathVariable("viewName") String viewName){
        return baseDataService.getViewByName(Integer.parseInt(serverCode), dbName, viewName);
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
                                                        @PathVariable("dbName") String dbName){
        return baseDataService.getSpList(Integer.parseInt(serverCode), dbName);
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
                                                    @PathVariable("spName") String spName){
        return baseDataService.getSpByName(Integer.parseInt(serverCode), dbName, spName);
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
                                     @RequestHeader(value = "User-Token", required =  false) String token,
                                     HttpServletRequest request,
                                     @RequestBody String sql){
        return baseDataService.quereyDataBySql(Integer.parseInt(serverCode), dbName, sql, token, request.getRemoteAddr());
    }

    @ResponseBody
    @RequestMapping(value = "/server/group")
    public Result<List<String>> getDbGroup(@RequestHeader(value = "User-Token", required =  false) String token){
        return baseDataService.getDbGroup(token);
    }


}
