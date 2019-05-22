/******************************************************************************
 * Copyright 2009-2018 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.exactpro.sf;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.exactpro.sf.Service.Status;

public class ServiceImportResult {
	
	private String serviceName;
	private String problem;
	private Status status;

    public String getServiceName() {
		return serviceName;
	}

	protected void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	public String getProblem() {
		return problem;
	}

	protected void setProblem(String problem) {
		this.problem = problem;
	}

	public Status getStatus() {
		return status;
	}

	protected void setStatus(Status status) {
		this.status = status;
	}
	
	// Static members
	
	protected static ServiceImportResult fromXml(Node n) {
		Element el = (Element)n;
		ServiceImportResult res = new ServiceImportResult();
		
		res.serviceName = Util.getTextContent(el, "name");
		res.status = Enum.valueOf(Status.class, Util.getTextContent(el, "status"));
		if (el.getElementsByTagName("problem").getLength() > 0) {
			res.problem = Util.getTextContent(el, "problem");
		}
		
		return res;
	}
	
}
