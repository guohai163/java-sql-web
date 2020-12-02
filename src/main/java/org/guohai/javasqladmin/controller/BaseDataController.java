package org.guohai.javasqladmin.controller;

import org.guohai.javasqladmin.beans.*;
import org.guohai.javasqladmin.service.BaseDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据基础控制器
 */
@RestController
@RequestMapping(value = "/database")
public class BaseDataController {
    private static final Logger LOG  = LoggerFactory.getLogger(BaseDataController.class);

    @Autowired
    BaseDataService baseDataService;

    @ResponseBody
    @RequestMapping(value = "/infoall")
    public Result<List<ConnectConfigBean>> getAllConnect(){
        return baseDataService.getAllDataConnect();
    }

    /**
     * 通过DBCode获得所有库
     * @param serverCode
     * @return
     */
    @ResponseBody
    @RequestMapping(value = "/dblist/{serverCode}")
    public Result<List<DatabaseNameBean>> getAllDBName(@PathVariable("serverCode") String serverCode){
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
     *
     */
    @ResponseBody
    @RequestMapping(value = "/columnlist/{serverCode}/{dbName}/{tableName}")
    public Result<List<ColumnsNameBean>> getColumnName(@PathVariable("serverCode") String serverCode,
                                                       @PathVariable("dbName") String dbName,
                                                       @PathVariable("tableName") String tableName){
        return baseDataService.getColumnList(Integer.parseInt(serverCode), dbName, tableName);
    }

    @ResponseBody
    @RequestMapping(value = "/query/{serverCode}/{dbName}", method = RequestMethod.POST)
    public Result<Object> quereyData(@PathVariable("serverCode") String serverCode,
                                     @PathVariable("dbName") String dbName,
                                     @RequestBody String sql){
        return baseDataService.quereyDataBySql(Integer.parseInt(serverCode), dbName, sql);
    }
}
