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
package com.exactpro.sf.common.messages;

import java.util.Collections;
import java.util.Set;

import com.exactpro.sf.configuration.suri.SailfishURI;

public interface IMessageFactory
{
    void init(String namespace, SailfishURI dictionaryURI);

    /***
     * creates new message with the id parameter passed to its metadata
     * @return a message with specified metadata id
     */
    IMessage createMessage(long id, String name, String namespace);

	IMessage createMessage(String name, String namespace);

    IMessage createMessage(String name);
    
    IHumanMessage createHumanMessage(String name);

	void fillMessageType(IMessage message);

    /***
     * Set of fields will be ignored when fail unxpected used
     * @return set as described
     */
	Set<String> getUncheckedFields();

    /***
     * Set of fields which will be excluded from comparison when {@link com.exactpro.sf.comparison.MessageComparator} will be invoked
     * @return set of ignored fields
     */
	default Set<String> getIgnoredFields() {
	    return Collections.emptySet();
    }

    String getNamespace();

    SailfishURI getDictionaryURI();

    String getProtocol();

}
