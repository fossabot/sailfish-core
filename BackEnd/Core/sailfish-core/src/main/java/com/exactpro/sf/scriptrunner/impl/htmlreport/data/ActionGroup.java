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
package com.exactpro.sf.scriptrunner.impl.htmlreport.data;

import com.exactpro.sf.scriptrunner.StatusType;

public class ActionGroup extends BaseEntity {
    private StatusType status;
    private String linkToReport;

    public ActionGroup copyWithStatus(StatusType status) {
        ActionGroup copy = new ActionGroup();
        copy.setName(getName());
        copy.setDescription(getDescription());
        copy.setStatus(status);
        copy.setLinkToReport(linkToReport);
        for(Object element : getElements()) {
            copy.addElement(element);
        }
        return copy;
    }

    public StatusType getStatus() {
        return status;
    }

    public void setStatus(StatusType status) {
        this.status = status;
    }

    public String getLinkToReport() {
        return linkToReport;
    }

    public void setLinkToReport(String linkToReport) {
        this.linkToReport = linkToReport;
    }
}
