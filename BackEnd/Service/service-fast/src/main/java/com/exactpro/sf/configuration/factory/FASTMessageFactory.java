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
package com.exactpro.sf.configuration.factory;

import java.util.Set;

import com.exactpro.sf.common.impl.messages.AbstractMessageFactory;
import com.exactpro.sf.services.fast.FASTMessageHelper;
import com.google.common.collect.ImmutableSet;

public class FASTMessageFactory extends AbstractMessageFactory {
    public static final Set<String> UNCHECKED_FIELDS = ImmutableSet.of(
        "SendingTime",
        FASTMessageHelper.TEMPLATE_ID_FIELD,
        FASTMessageHelper.MESSAGE_TYPE_FIELD
    );

    @Override
    public Set<String> getUncheckedFields() {
        return UNCHECKED_FIELDS;
    }

    @Override
    public String getProtocol() {
        return "FAST";
    }
}
