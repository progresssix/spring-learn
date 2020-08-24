package com.swh.mvcframework.v3.servlet;

import com.swh.mvcframework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;


public class SWHDispatcherServlet extends HttpServlet {

    private Properties contextConfig = new Properties();

    private List<String> classNames = new ArrayList<String>();

    private Map<String,Object> ioc = new HashMap<String, Object>();

//    private Map<String,Method> handlerMapping = new HashMap<String, Method>();

    private List<HandlerMapping> handlerMapping = new ArrayList<HandlerMapping>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatcher(req,resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception,Detail :" + Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispatcher(HttpServletRequest req, HttpServletResponse resp) throws Exception{
        HandlerMapping mapping = getHandler(req);
        if (mapping == null){
            resp.getWriter().write("404 not found");
            return;
        }
        //获得方法的形参列表
        Class<?> [] paramTypes = mapping.getParamTypes();

        Object [] paramValues = new Object[paramTypes.length];

        Map<String,String[]> params = req.getParameterMap();
        for (Map.Entry<String, String[]> parm : params.entrySet()) {
            String value = Arrays.toString(parm.getValue()).replaceAll("\\[|\\]","")
                    .replaceAll("\\s",",");

            if(!mapping.paramIndexMapping.containsKey(parm.getKey())){continue;}

            int index = mapping.paramIndexMapping.get(parm.getKey());
            paramValues[index] = convert(paramTypes[index],value);
        }

        if(mapping.paramIndexMapping.containsKey(HttpServletRequest.class.getName())) {
            int reqIndex = mapping.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = req;
        }

        if(mapping.paramIndexMapping.containsKey(HttpServletResponse.class.getName())) {
            int respIndex = mapping.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[respIndex] = resp;
        }

        Object returnValue = mapping.method.invoke(mapping.controller,paramValues);
        if(returnValue == null || returnValue instanceof Void){ return; }
        resp.getWriter().write(returnValue.toString());
    }

    private HandlerMapping getHandler(HttpServletRequest req) {
        if (handlerMapping.isEmpty()){return null;}
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath, "").replaceAll("/+", "/");

        for (HandlerMapping mapping : this.handlerMapping) {
            if (mapping.getUrl().equals(url)){
                return mapping;
            }
        }
        return null;
    }


    private Object convert(Class<?> type,String value){
        if (Integer.class==type){
            return Integer.valueOf(value);
        }else if (String.class==type){
            return value;
        }
        return value;
    }


    @Override
    public void init(ServletConfig config) throws ServletException {
        //1.加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        //2.扫描相关的类
        doScanner(contextConfig.getProperty("scanPackage"));

        //3.初始化扫描到的类，并将他们放入ioc容器中
        doInstance();

        //4.完成依赖注入
        doAutowired();

        //5.初始化HandleMapping
        initHandleMapping();

        System.out.println("SWH Spring framework is init");
    }

    private void initHandleMapping() {
        if (ioc.isEmpty()){
            return;
        }

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();

            if (!clazz.isAnnotationPresent(SWHController.class)){
                continue;
            }
            String baseUrl = "";
            if (clazz.isAnnotationPresent(SWHRequestMapping.class)){
                SWHRequestMapping mapping = clazz.getAnnotation(SWHRequestMapping.class);
                baseUrl = mapping.value();
            }
            for (Method method : clazz.getMethods()) {
                if (!method.isAnnotationPresent(SWHRequestMapping.class)){
                    continue;
                }
                SWHRequestMapping mapping = method.getAnnotation(SWHRequestMapping.class);
                String url = (baseUrl + "/" + mapping.value()).replaceAll("/+","/");

//                handlerMapping.put(url,method);
                handlerMapping.add(new HandlerMapping(url,entry.getValue(),method));

                System.out.println("Mapped :" + url + "," + method);
            }
        }
    }

    private void doAutowired() {
        if (ioc.isEmpty()){
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(SWHAutowired.class)){
                    SWHAutowired autowired = field.getAnnotation(SWHAutowired.class);
                    String beanName = autowired.value().trim();

                    if ("".equals(beanName)){
                        beanName = field.getType().getName();
                    }

                    field.setAccessible(true);

                    try {
                        field.set(entry.getValue(),ioc.get(beanName));
                    }catch (IllegalAccessException e){
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void doInstance() {
        if (classNames.isEmpty()){
            return;
        }
        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);

                if (clazz.isAnnotationPresent(SWHController.class)){
                    Object instance = clazz.newInstance();
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,instance);
                }else if (clazz.isAnnotationPresent(SWHService.class)){
                    SWHService service = clazz.getAnnotation(SWHService.class);
                    String beanName = service.value();

                    if ("".equals(beanName.trim())){
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName,instance);

                    for (Class<?> i : clazz.getInterfaces()) {
                        if (ioc.containsKey(i.getName())){
                            throw new Exception("The " + i.getName() + "is exists!!");
                        }
                        ioc.put(i.getName(),instance);
                    }
                }else {
                    continue;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private String toLowerFirstCase(String simpleName) {
        char [] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    private void doScanner(String scanPackage) {
        //scanPackage = xxx.xxx.xxx 包路径
        //转换为文件路径  .替换为/
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.","/"));
        File classPath = new File(url.getFile());
        for (File file : classPath.listFiles()) {
            if (file.isDirectory()){
                doScanner(scanPackage + "." + file.getName());
            }else {
                if (!file.getName().endsWith(".class")){
                    continue;
                }
                String className = (scanPackage + "." + file.getName().replace(".class",""));
                classNames.add(className);
            }
        }
    }

    private void doLoadConfig(String contextConfigLocation) {
        InputStream is = null;
        try {
            is = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
            contextConfig.load(is);
        }catch (IOException e){
            e.printStackTrace();
        }finally {
            if (null == is){
                try {
                    is.close();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
    }


    public class HandlerMapping{
        private String url;
        private Method method;
        private Object controller;
        private Class<?> [] paramTypes;

        private Map<String ,Integer> paramIndexMapping;

        public Class<?>[] getParamTypes() {
            return paramTypes;
        }

        public String getUrl() {
            return url;
        }

        public Method getMethod() {
            return method;
        }

        public Object getController() {
            return controller;
        }

        public HandlerMapping(String url, Object controller, Method method){
            this.url = url;
            this.method = method;
            this.controller = controller;

            paramTypes = method.getParameterTypes();

            paramIndexMapping = new HashMap<String, Integer>();
            putparamIndexMapping(method);
        }

        private void putparamIndexMapping(Method method) {
            Annotation[][] annotations = method.getParameterAnnotations();
            for (int i = 0; i < annotations.length; i++) {
                for (Annotation annotation : annotations[i]) {
                    if (annotation instanceof SWHRequestParam){
                        String paramName = ((SWHRequestParam) annotation).value();
                        if (!"".equals(paramName)){
                            paramIndexMapping.put(paramName,i);
                        }
                    }
                }
            }

            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> type = parameterTypes[i];
                if (type == HttpServletRequest.class || type == HttpServletResponse.class){
                    paramIndexMapping.put(type.getName(),i);
                }
            }
        }
    }
}
