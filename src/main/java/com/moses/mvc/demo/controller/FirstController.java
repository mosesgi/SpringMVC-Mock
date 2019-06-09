package com.moses.mvc.demo.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.moses.mvc.demo.service.INamedService;
import com.moses.mvc.demo.service.ITestService;
import com.moses.mvc.framework.annotation.MJAutowired;
import com.moses.mvc.framework.annotation.MJController;
import com.moses.mvc.framework.annotation.MJRequestMapping;
import com.moses.mvc.framework.annotation.MJRequestParam;
import com.moses.mvc.framework.context.MJModelAndView;

@MJController
@MJRequestMapping("/web")
public class FirstController {
	@MJAutowired
	private ITestService testService;
	
	@MJAutowired("customName")
	private INamedService namedService;
	
	@MJRequestMapping("/query/.*.json")
//	@MJResponseBody
	public MJModelAndView query(HttpServletRequest request, HttpServletResponse response, 
				@MJRequestParam("name") String name, @MJRequestParam("addr") String address) {
//		out(response, "get params name = " + name);
		Map<String, Object> model = new HashMap<>();
		model.put("name", name);
		model.put("addr", address);
		return new MJModelAndView("first.mjml", model);
	}
	
	@MJRequestMapping("/add.json")
	public MJModelAndView add(HttpServletRequest request, HttpServletResponse response) {
		out(response, "This is json string");
		return null;
	}
	
	public void out(HttpServletResponse response, String str) {
		try {
			response.getWriter().write(str);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
