package org.guohai.javasqlweb.controller;


import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.SqlGuidBean;
import org.guohai.javasqlweb.service.BaseDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 基础控制器
 * @author guohai
 */
@RestController
public class HomeController {

    @Value("${project.version}")
    private String version;

    @Autowired
    BaseDataService baseService;

    @CrossOrigin
    @RequestMapping(value = "/version", method = RequestMethod.GET)
    public Result<String > version(){
        return new Result<>(true,"", version) ;
    }

    @CrossOrigin
    @RequestMapping(value = "/health", method = RequestMethod.GET)
    public Result<String> serverHealth(HttpServletResponse response) {
        Result<String> result = baseService.serverHealth();
        if (!result.getStatus()) {
            response.setStatus(500);
        }
        return result;
    }

    @CrossOrigin
    @RequestMapping(value = "/sql/guid", method = RequestMethod.GET)
    public Result<List<SqlGuidBean>> getAllGuid() {
        return baseService.getAllGuid();
    }

}
