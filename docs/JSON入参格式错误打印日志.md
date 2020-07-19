---
title: JSON入参格式错误打印日志
date: 2019-07-02
categories: 
- 开发日常
tags: 
- java
copyright: true
---

## JSON入参格式错误打印日志
最近接了个项目，其中涉及到一个很大的数据表单，系统是前后端分离的，在实际开发调试中老是出现前端传入JSON数据不能被后端正常解析，这种问题在调试期间是可以通过F12得到入参数据，然后对入参数据进行格式分析即可。
但是现在线上也偶尔会出现以下类似错误（当然这个可以看出来是json未正常结束，还有其他错误就不能直接用来排查问题）：
```
ERROR | 42907 | http-nio-8051-exec-2 | com.mhc.framework.common.exception.handler.RestExceptionHandler | [RestExceptionHandler.java:46] | com.alibaba.fastjson.JSONException: not close json text, token : }
	at com.alibaba.fastjson.parser.DefaultJSONParser.close(DefaultJSONParser.java:1526)
	at com.alibaba.fastjson.JSON.parseObject(JSON.java:387)
	at com.alibaba.fastjson.JSON.parseObject(JSON.java:448)
	at com.alibaba.fastjson.JSON.parseObject(JSON.java:556)
	at com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter.readType(FastJsonHttpMessageConverter.java:263)
	at com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter.read(FastJsonHttpMessageConverter.java:237)
	at org.springframework.web.servlet.mvc.method.annotation.AbstractMessageConverterMethodArgumentResolver.readWithMessageConverters(AbstractMessageConverterMethodArgumentResolver.java:201)
	at org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor.readWithMessageConverters(RequestResponseBodyMethodProcessor.java:150)
	at org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor.resolveArgument(RequestResponseBodyMethodProcessor.java:128)
	at org.springframework.web.method.support.HandlerMethodArgumentResolverComposite.resolveArgument(HandlerMethodArgumentResolverComposite.java:121)
	at org.springframework.web.method.support.InvocableHandlerMethod.getMethodArgumentValues(InvocableHandlerMethod.java:158)
	at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:128)
	at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:97)
	at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:827)
	at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:738)
	at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:85)
	at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:967)
	at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:901)
	at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:970)
	at org.springframework.web.servlet.FrameworkServlet.doPost(FrameworkServlet.java:872)
```

此时把问题告诉前端，数据格式有问题，那么前端有很多数据要封装到一个JSON中，而且是偶然出现的，所以他会给你要入参的数据，而此时后台也没有记录，因此问题的解决收到的阻碍。
因此下面的内容就是要介绍如何在入参不能被正常解析时打印对应的日志。
 ### 解决思路
本系统是SpringBoot项目，解析入参JSON数据用的是fastjson，通过配置增加了HttpMessageConverters转换器。
```
	@Bean
	public HttpMessageConverters fastJsonConfigure(){
		FastJsonHttpMessageConverter converter = new FastJsonHttpMessageConverter();
		FastJsonConfig fastJsonConfig = new FastJsonConfig();
		fastJsonConfig.setSerializerFeatures(SerializerFeature.WriteMapNullValue);
		converter.setFastJsonConfig(fastJsonConfig);
		converter.setSupportedMediaTypes(Arrays.asList(MediaType.APPLICATION_JSON_UTF8));
		return new HttpMessageConverters(converter);
	}
```
而错误日志显示是其中这个类的这一行打印出来的：
```
at com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter.read(FastJsonHttpMessageConverter.java:237)
```
此处源码：
```
    public Object read(Type type, //
                       Class<?> contextClass, //
                       HttpInputMessage inputMessage //
    ) throws IOException, HttpMessageNotReadableException {
        return readType(getType(type, contextClass), inputMessage);
    }
```
可以考虑继承FastJsonHttpMessageConverter，然后对此方法进行增强：
```
@Slf4j
public class FastJsonHttpMessageConverterProxy extends FastJsonHttpMessageConverter {

    @Override
    public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        try {
            return super.read(type, contextClass, inputMessage);
        } catch (Exception e) {
            // 解析入参异常时候，打印对应入参数据
            log.error("request body json parse error :[{}]", IoUtil.read(inputMessage.getBody(), StandardCharsets.UTF_8));
            throw e;
        }
    }
}
```
完工，跑一下看看，但是发现错误日志里面是没有任何数据：
```
 ERROR | 43188 | http-nio-8051-exec-2 | [FastJsonHttpMessageConverterProxy.java:28] | request body json parse error :[]
```
 这是什么原因？原来inputMessage是从HttpServletRequest过来的，这里的输入流限制只能读一次，不能进行reset。因此我们就需要让这个inputMessage支持多次读取。继续对其增强：
```
@Slf4j
public class FastJsonHttpMessageConverterProxy extends FastJsonHttpMessageConverter {

    @Override
    public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        try {
            return super.read(type, contextClass, inputMessage);
        } catch (Exception e) {
            log.error("request body json parse error :[{}]", IoUtil.read(inputMessage.getBody(), StandardCharsets.UTF_8));
            throw e;
        }
    }

    /**
     * 对入参消息体进行代理，使其支持可重复读
     */
    class HttpInputMessageProxy implements HttpInputMessage {
        private HttpHeaders headers;
        private byte[] body;
        public HttpInputMessageProxy(HttpInputMessage inputMessage) {
            this.headers = inputMessage.getHeaders();
            try {
                InputStream inputStream = inputMessage.getBody();
                body = IoUtil.readBytes(inputStream);
            } catch (IOException e) {
                //ignore
            }
        }
        @Override
        public InputStream getBody() throws IOException {
            return new ByteArrayInputStream(body);
        }
        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

    }
}
```
经测试在入参异常情况下打印出错误的入参数据。

### 内容扩展
有些情况我们需要对入参进行解密操作，也需要提前读取入参，这样可以通过Spring的ControllerAdvice解决：
```
@Slf4j
@ControllerAdvice
public class AuthRequestBodyAdvice implements RequestBodyAdvice {
    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }
    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }
    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        HttpInputMessageProxy httpInputMessageProxy = new HttpInputMessageProxy(inputMessage);
        // TODO 读取入参流，解密校验
        return httpInputMessageProxy;
    }
    class HttpInputMessageProxy implements HttpInputMessage {
        private HttpHeaders headers;
        private byte[] body;
        public HttpInputMessageProxy(HttpInputMessage inputMessage) {
            this.headers = inputMessage.getHeaders();
            try {
                InputStream inputStream = inputMessage.getBody();
                body = IoUtil.readBytes(inputStream);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        @Override
        public InputStream getBody() throws IOException {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }
}
```

## 参考
- https://www.52jingya.com/aid13401.html
- https://www.cnblogs.com/nickhan/p/9849693.html
