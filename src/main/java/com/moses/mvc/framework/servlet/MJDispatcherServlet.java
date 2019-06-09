package com.moses.mvc.framework.servlet;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.moses.mvc.framework.annotation.MJController;
import com.moses.mvc.framework.annotation.MJRequestMapping;
import com.moses.mvc.framework.annotation.MJRequestParam;
import com.moses.mvc.framework.context.MJApplicationContext;
import com.moses.mvc.framework.context.MJModelAndView;

public class MJDispatcherServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private static final String LOCATION = "contextConfigLocation";
	
//	private Map<Pattern, Handler> handlerMapping = new HashMap<>();			//Spring MVC is using List
	private List<Handler> handlerMapping = new ArrayList<>();
	
	private Map<Handler, HandlerAdapter> adapterMapping = new HashMap<>();		//同handlerMapping这里也可以改为List
	
	private List<ViewResolver> viewResolvers = new ArrayList<>();
	
	// initialize IOC container
	@Override
	public void init(ServletConfig config) throws ServletException {
		System.out.println("MJ Spring MVC Mocking init...");
		MJApplicationContext context = new MJApplicationContext(config.getInitParameter(LOCATION));
		Map<String, Object> ioc = context.getAll();
		System.out.println(ioc);
		System.out.println(ioc.get("firstController"));
		
		initMultipartResolver(context);
		initLocaleResolver(context);
		initThemeResolver(context);
		
		//================= important ===================
		initHandlerMappings(context);
		initHandlerAdapters(context);
		//================= important ===================
		
		initHandlerExceptionResolvers(context);
		initRequestToViewNameTranslator(context);
		
		//view resolving
		initViewResolvers(context);
		initFlashMapManager(context);
	}
	
	private void initMultipartResolver(MJApplicationContext context) {
	}
	
	private void initLocaleResolver(MJApplicationContext context) {
	}
	
	private void initThemeResolver(MJApplicationContext context) {
	}
	
	private void initHandlerMappings(MJApplicationContext context) {
		Map<String, Object> ioc = context.getAll();
		if(ioc.isEmpty()) {
			return;
		}
		
		//find all methods within the class annotated by @MJController
		//The method should be annotated with @MJRequestMapping, otherwise it cannot be used.
		for(Entry<String, Object> entry : ioc.entrySet()) {
			Class<?> clazz = entry.getValue().getClass();
			if(!clazz.isAnnotationPresent(MJController.class)) {continue;}
			
			String url = "";
			if(clazz.isAnnotationPresent(MJRequestMapping.class)) {
				MJRequestMapping requestMapping = clazz.getAnnotation(MJRequestMapping.class);
				url = requestMapping.value();
			}
			
			//scan all methods under Controller class
			Method[] methods = clazz.getMethods();
			for(Method method: methods) {
				if(!method.isAnnotationPresent(MJRequestMapping.class)) {continue;}
				MJRequestMapping methodMp = method.getAnnotation(MJRequestMapping.class);
				
				//支持正则URL改造
				String regex = (url + methodMp.value()).replaceAll("/+", "/");
				Pattern pattern = Pattern.compile(regex);
				
				//@MJRequestMapping contains URL, means, one URL mapping to a method. So this mapping should be saved in Map
				handlerMapping.add(new Handler(pattern, entry.getValue(), method));
				System.out.println("initHandlerMappings == Mapping: " + pattern + " <==> " + method.toString());
			}
		}
	}
	
	//dynamic adapt to the parameters
	private void initHandlerAdapters(MJApplicationContext context) {
		if(handlerMapping.isEmpty()) {return;}
		//(Param type)/(value in MJRequestParam) as key, Param's index as value
		Map<String, Integer> paramMapping = new HashMap<>();
		
		//Need to get specific method from Handler
		for(Handler handler: handlerMapping) {
			//Get all parameters types from the method.
			Class<?>[] paramsTypes = handler.method.getParameterTypes();
			//ordered. But using reflection we cannot get param name.
			for(int i=0; i< paramsTypes.length; i++) {
				Class<?> type = paramsTypes[i];
				
				//Match request and response
				if(type == HttpServletRequest.class || type == HttpServletResponse.class) {
					paramMapping.put(type.getName(), i);
				}
			}
				
			//Match custom param list
			Annotation[][] pa = handler.method.getParameterAnnotations();
			for(int i=0; i<pa.length; i++) {
				for(Annotation a : pa[i]) {
					if(a instanceof MJRequestParam) {
						String paramName = ((MJRequestParam)a).value();
						if(!"".equals(paramName.trim())) {
							paramMapping.put(paramName, i);
						}
					}
				}
			}
			
			adapterMapping.put(handler, new HandlerAdapter(paramMapping));
		}
	}
	
	private void initHandlerExceptionResolvers(MJApplicationContext context) {
	}
	
	private void initRequestToViewNameTranslator(MJApplicationContext context) {
	}
	
	private void initViewResolvers(MJApplicationContext context) {
		//模板一般不放在webapp，而是放在WEB-INF下，或者class下。这样可以避免用户直接请求到模板框架
		//加载模板的个数，存储到缓存中，检查模板中语法错误
		String templateRoot = context.getConfig().getProperty("templateRoot");
		String rootPath = this.getClass().getClassLoader().getResource(templateRoot).getFile();
		File rootDir = new File(rootPath);
		for(File template: rootDir.listFiles()) {
			viewResolvers.add(new ViewResolver(template.getName(), template));
		}
	}
	
	private void initFlashMapManager(MJApplicationContext context) {
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		this.doPost(req, resp);
	}

	// trigger controller methods
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			doDispatch(req, resp);
		} catch (Exception e) {
			resp.getWriter().write("500 Exception, Msg: " + Arrays.toString(e.getStackTrace()));
		}
	}
	
	private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception{
		//get hanlder from HandlerMapping
		Handler handler = getHandler(req);
		if(handler == null) {
			resp.getWriter().write("404 not found");
			return;
		}
		
		//get adapter based on handler
		HandlerAdapter ha = getHandlerAdapter(handler);
		
		//use adapter to trigger actual business method.
		MJModelAndView mv = ha.handle(req, resp, handler);
		
		//写一个模板框架 - View
		//使用 @{name}
		applyDefaultViewName(resp, mv);
		
	}
	
	private void applyDefaultViewName(HttpServletResponse resp, MJModelAndView mv) throws Exception {
		if(null == mv) return;
		if(viewResolvers.isEmpty()) return;
		for(ViewResolver resolver: viewResolvers) {
			if(!mv.getView().equals(resolver.getViewName())) {
				continue;
			}
			String r = resolver.parse(mv);
			if(r != null) {
				resp.getWriter().write(r);
				break;
			}
		}
	}

	private Handler getHandler(HttpServletRequest req) {
		if(handlerMapping.isEmpty()) {return null;}
		
		//use Regex to match
		String url = req.getRequestURI();
		String contextPath = req.getContextPath();
		url = url.replace(contextPath, "").replaceAll("/+", "/");
//		return handlerMapping.get(url);		// String URL as key, initial version
		
		for(Handler handler : handlerMapping) {
			Matcher matcher = handler.pattern.matcher(url);
			if(!matcher.matches()) {continue;}
			return handler;
		}
		return null;
	}
	
	private HandlerAdapter getHandlerAdapter(Handler handler) {
		if(adapterMapping.isEmpty()) {return null;}
		
		return adapterMapping.get(handler);
	}
	
	/**
	 * Handler Mapping
	 * @author Moses
	 *
	 */
	private class Handler{
		protected Object controller;
		protected Method method;
		protected Pattern pattern;
		
		protected Handler(Pattern pattern, Object controller, Method method){
			this.controller = controller;
			this.method = method;
			this.pattern = pattern;
		}
	}
	
	/**
	 * Method Adapter
	 * @author Moses
	 *
	 */
	private class HandlerAdapter{
		//Method (param type)/(RequestParam value) as key, index as value.
		private Map<String, Integer> paramMapping;
		
		public HandlerAdapter(Map<String, Integer> paramMapping) {
			this.paramMapping = paramMapping;
		}

		//User reflection to trigger method corresponding to the URL
		public MJModelAndView handle(HttpServletRequest req, HttpServletResponse resp, Handler handler) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			Class<?>[] paramTypes = handler.method.getParameterTypes();
			
			//要想给参数赋值，只能通过索引号来找到具体的某个参数
			Object[] paramValues = new Object[paramTypes.length];
			Map<String, String[]> params = req.getParameterMap();
			for(Entry<String, String[]> param: params.entrySet()) {
				String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
				if(!this.paramMapping.containsKey(param.getKey())) {continue;}
				int index = paramMapping.get(param.getKey());
				
				//prepare all parameter actual values
				paramValues[index] = castStringValue(value, paramTypes[index]);
			}
			
			String reqName = HttpServletRequest.class.getName();
			if(paramMapping.containsKey(reqName)) {
				int reqIndex = this.paramMapping.get(reqName);
				paramValues[reqIndex] = req;
			}
			
			String respName = HttpServletResponse.class.getName();
			if(paramMapping.containsKey(respName)) {
				int respIndex = this.paramMapping.get(respName);
				paramValues[respIndex] = resp;
			}
			
			boolean isModelAndView = handler.method.getReturnType() == MJModelAndView.class;
			Object r = handler.method.invoke(handler.controller, paramValues);
			if(isModelAndView) {
				return (MJModelAndView)r;
			} else {
				return null;
			}
			
		}

		private Object castStringValue(String value, Class<?> clazz) {
			if(clazz == String.class) {
				return value;
			} else if(clazz == Integer.class || clazz == int.class) {
				return Integer.valueOf(value);
			} else {
				return null;
			}
		}
	}
	
	private class ViewResolver{
		private String viewName;
		private File file;
		
		protected ViewResolver(String viewName, File file) {
			this.viewName = viewName;
			this.file = file;
		}
		
		protected String parse(MJModelAndView mv) throws Exception {
			StringBuffer sb = new StringBuffer();
			RandomAccessFile ra = new RandomAccessFile(this.file, "r");
			
			try {
				String line = null;
				while(null != (line=ra.readLine())) {
					Matcher m = matcher(line);
					while(m.find()) {
						for(int i=1; i<=m.groupCount(); i++) {
							String paramName = m.group(i);
							Object paramValue = mv.getModel().get(paramName);
							if(null == paramValue) {continue;}
							line = line.replaceAll("@\\{" + paramName + "\\}", paramValue.toString());
						}
					}
					sb.append(line);
				}
			} finally {
				ra.close();
			}
			return sb.toString();
		}
		
		private Matcher matcher(String str) {
			Pattern pattern = Pattern.compile("@\\{(.+?)\\}", Pattern.CASE_INSENSITIVE);
			Matcher matcher = pattern.matcher(str);
			return matcher;
		}

		public String getViewName() {
			return viewName;
		}

		public File getFile() {
			return file;
		}
		
	}

}
