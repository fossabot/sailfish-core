/******************************************************************************
 * Copyright 2009-2019 Exactpro (Exactpro Systems Limited)
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
package com.exactpro.sf.scriptrunner.impl.jsonreport;

import com.exactpro.sf.scriptrunner.impl.jsonreport.beans.*;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import java.util.Arrays;
import java.util.Collection;

@JsonTypeInfo(
        use = Id.NAME,
        include = As.PROPERTY,
        property = "actionNodeType")
@JsonSubTypes({
        @Type(value = Action.class, name = "action"),
        @Type(value = CustomMessage.class, name = "customMessage"),
        @Type(value = Verification.class, name = "verification"),
        @Type(value = BugCategory.class, name = "category"),
        @Type(value = Bug.class, name = "bug"),
        @Type(value = CustomLink.class, name = "link"),
        @Type(value = CustomTable.class, name = "table"),
        @Type(value = LogEntry.class, name = "logEntry"),
        @Type(value = Message.class, name = "message"),
        @Type(value = ReportException.class, name = "reportException"),
        @Type(value = CustomTable.class, name = "table"),
        @Type(value = TestCase.class, name = "testCase")
})
public interface IJsonReportNode {
    default void addSubNodes(IJsonReportNode... nodes) {
        addSubNodes(Arrays.asList(nodes));
    }

    default void addSubNodes(Collection<? extends IJsonReportNode> nodes) {
        throw new IllegalArgumentException("not supported.");
    }

    default void addException(Throwable t) {
        throw new IllegalArgumentException("not supported.");
    }
}
