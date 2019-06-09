package com.moses.mvc.framework.context;

import java.util.Map;

public class MJModelAndView {
	private String view;
	private Map<String, Object> model;
	
	public MJModelAndView(String view) {
		this.view = view;
	}
	
	public MJModelAndView(String view, Map<String, Object> model) {
		this.view = view;
		this.model = model;
	}

	public String getView() {
		return view;
	}

	public void setView(String view) {
		this.view = view;
	}

	public Map<String, Object> getModel() {
		return model;
	}

	public void setModel(Map<String, Object> model) {
		this.model = model;
	}

}
