package org.guohai.javasqladmin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 基础控制器
 * @author guohai
 */
@Controller
public class HomeController {
    
    @RequestMapping(value = "/")
    public String home() {

        return "home";
    }
}
