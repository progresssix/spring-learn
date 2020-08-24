package com.swh.demo.mvc.action;

import com.swh.demo.service.IDemoService;
import com.swh.mvcframework.annotation.SWHAutowired;
import com.swh.mvcframework.annotation.SWHController;
import com.swh.mvcframework.annotation.SWHRequestMapping;
import com.swh.mvcframework.annotation.SWHRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@SWHController
@SWHRequestMapping("/demo")
public class DemoAction {

    @SWHAutowired
    private IDemoService demoService;

    @SWHRequestMapping("/query")
    public void query(HttpServletRequest request, HttpServletResponse response, @SWHRequestParam("name") String name){
//        String result = demoService.get(name);
        String result = "My name is "+name;
        try {
            response.getWriter().write(result);
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    @SWHRequestMapping("/add")
    public void add(HttpServletRequest request,HttpServletResponse response, @SWHRequestParam("a") Integer a ,@SWHRequestParam("b") Integer b){
        try {
            response.getWriter().write(a + "+" + b + "=" + (a+b));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
