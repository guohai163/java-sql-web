package org.guohai.javasqlweb.controller;


import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.SqlGuidBean;
import org.guohai.javasqlweb.service.BaseDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 基础控制器
 * @author guohai
 */
@Controller
public class HomeController {

    private static final Logger LOG  = LoggerFactory.getLogger(HomeController.class);

    @Value("${project.version}")
    private String version;

    @Autowired
    BaseDataService baseService;

    @RequestMapping(value = {"/","/login","/admin","/guid"}, method = RequestMethod.GET)
    public String home() {

        return "index.html";
    }


    @CrossOrigin
    @ResponseBody
    @RequestMapping(value = "/version", method = RequestMethod.GET)
    public Result<String > version(){
        return new Result<>(true,"", version) ;
    }

    @CrossOrigin
    @ResponseBody
    @RequestMapping(value = "/health", method = RequestMethod.GET)
    public Result<String> serverHealth(HttpServletResponse response) {
        Result<String> result = baseService.serverHealth();
        if (!result.getStatus()) {
            response.setStatus(500);
        }
        return result;
    }

    @CrossOrigin
    @ResponseBody
    @RequestMapping(value = "/sql/guid", method = RequestMethod.GET)
    public Result<List<SqlGuidBean>> getAllGuid() {
        return baseService.getAllGuid();
    }

}
