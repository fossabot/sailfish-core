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
package com.exactpro.sf.testwebgui;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;
import javax.faces.convert.ConverterException;
import javax.faces.convert.FacesConverter;

import com.exactpro.sf.configuration.suri.SailfishURI;
import com.exactpro.sf.configuration.suri.SailfishURIException;
import com.exactpro.sf.configuration.suri.SailfishURIRule;

@FacesConverter("utilityURIConverter")
public class SailfishUtilityURIConverter implements Converter {
    @Override
    public Object getAsObject(FacesContext paramFacesContext, UIComponent paramUIComponent, String paramString) {
        try {
            return SailfishURI.parse(paramString, SailfishURIRule.REQUIRE_PLUGIN, SailfishURIRule.REQUIRE_CLASS);
        } catch(SailfishURIException e) {
            throw new ConverterException(e);
        }
    }

    @Override
    public String getAsString(FacesContext paramFacesContext, UIComponent paramUIComponent, Object paramObject) {
        if(paramObject instanceof SailfishURI) {
            return ((SailfishURI)paramObject).toString();
        }

        throw new ConverterException("paramObject is not an instance of " + SailfishURI.class.getSimpleName());
    }
}
