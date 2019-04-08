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
package com.exactpro.sf.scriptrunner.actionmanager;

import java.util.HashMap;

import com.exactpro.sf.common.impl.messages.BaseMessage;
import com.exactpro.sf.common.messages.IMessage;
import com.exactpro.sf.scriptrunner.actionmanager.actioncontext.IActionContext;
import com.exactpro.sf.scriptrunner.actionmanager.exceptions.ActionCallException;
import com.exactpro.sf.scriptrunner.actionmanager.exceptions.ActionNotFoundException;

public interface IActionCaller {
    public <T> T call(String actionName, IActionContext actionContext) throws ActionCallException, ActionNotFoundException, InterruptedException;
    public <T> T call(String actionName, IActionContext actionContext, IMessage iMessage) throws ActionCallException, ActionNotFoundException, InterruptedException;
    public <T> T call(String actionName, IActionContext actionContext, BaseMessage baseMessage) throws ActionCallException, ActionNotFoundException, InterruptedException;
    public <T> T call(String actionName, IActionContext actionContext, Object message) throws ActionCallException, ActionNotFoundException, InterruptedException;
    public <T> T call(String actionName, IActionContext actionContext, HashMap<?,?> hashMap) throws ActionCallException, ActionNotFoundException, InterruptedException;

    interface ConsumerAction<T extends IActionCaller> {
        void accept(T actionClass, IActionContext actionContext) throws Exception;
    }

    interface FunctionAction<T extends IActionCaller, R> {
        R apply(T actionClass, IActionContext actionContext) throws Exception;
    }

    interface ConsumerActionWithParameters<T extends IActionCaller, P> {
        void accept(T actionClass, IActionContext actionContext, P parameters) throws Exception;
    }

    interface FunctionActionWithParameters<T extends IActionCaller, P, R> {
        R apply(T actionClass, IActionContext actionContext, P parameters) throws Exception;
    }
}
