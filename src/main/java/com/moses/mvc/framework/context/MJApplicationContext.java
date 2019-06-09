package com.moses.mvc.framework.context;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import com.moses.mvc.framework.annotation.MJAutowired;
import com.moses.mvc.framework.annotation.MJController;
import com.moses.mvc.framework.annotation.MJService;

public class MJApplicationContext {

	private Map<String, Object> instanceMapping = new ConcurrentHashMap<>();
	private List<String> classCache = new ArrayList<>();
	
	private Properties config = new Properties();

	public MJApplicationContext(String location) {
		// load config properties
		InputStream is = null;
		try {
			// locate
			is = this.getClass().getClassLoader().getResourceAsStream(location);
			// load
			config.load(is);

			// register, find all classes
			String packageName = config.getProperty("scanPackage");
			doRegister(packageName);

			// initialize, by iterating classes
			doCreateBean();

			// Injection
			populateBean();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		System.out.println("IOC container initialized successfully!");
	}

	// find all qualified classes, register in cache.
	private void doRegister(String packageName) {
		URL url = this.getClass().getClassLoader().getResource("/" + packageName.replace(".", "/"));
		File dir = new File(url.getFile());
		for (File file : dir.listFiles()) {
			// recurse if it's directory
			if (file.isDirectory()) {
				doRegister(packageName + "." + file.getName());
			} else {
				classCache.add(packageName + "." + file.getName().replace(".class", "").trim());
			}
		}
	}

	private void doCreateBean() {
		// check register info
		if (classCache.size() == 0)
			return;

		try {
			for (String className : classCache) {
				Class<?> clazz = Class.forName(className);

				// initiate classes with @Service @Controller
				if (clazz.isAnnotationPresent(MJController.class)) {
					String id = lowerFirstChar(clazz.getSimpleName());
					instanceMapping.put(id, clazz.newInstance());
				} else if (clazz.isAnnotationPresent(MJService.class)) {
					MJService service = clazz.getAnnotation(MJService.class);
					
					//use custom name if value is defined.
					String id = service.value();
					if("".equals(id.trim())) {
						instanceMapping.put(id, clazz.newInstance());
						continue;
					}
					
					//Use default rule if value is empty
					//1. lowercase first letter of class name
					//2. if it's interface, use type to find class
					Class<?>[] interfaces = clazz.getInterfaces();
					//If class implemented interface, use interface type as key.
					for(Class<?> i: interfaces) {
						instanceMapping.put(i.getName(), clazz.newInstance());
					}
				} else {
					continue;
				}

			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private String lowerFirstChar(String name) {
		char[] chars = name.toCharArray();
		chars[0] += 32;
		return String.valueOf(chars);
	}

	private void populateBean() {
		//Check if there is valid content in IOC container.
		if(instanceMapping.isEmpty()) {return;}
		for(Entry<String, Object> entry : instanceMapping.entrySet()) {
			//get all fields within the class, including private fields.
			Field[] fields = entry.getValue().getClass().getDeclaredFields();
			for(Field field: fields) {
				if(!field.isAnnotationPresent(MJAutowired.class)) {
					continue;
				}
				MJAutowired autowired = field.getAnnotation(MJAutowired.class);
				String id = autowired.value().trim();
				//if Id is empty, means the custom name is not set, use default type to do the injection.
				if("".equals(id)) {
					id = field.getType().getName();
				}
				field.setAccessible(true);	//make private field as accessible.
				
				try {
					field.set(entry.getValue(), instanceMapping.get(id));
				} catch(Exception e) {
					e.printStackTrace();
					continue;
				}
			}
		}
	}

	public Object getBean(String name) {
		return null;
	}

	public Map<String, Object> getAll() {
		return instanceMapping;
	}

	public Properties getConfig() {
		return config;
	}
	
}
