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
package com.exactpro.sf.services.tcpip;

public class MessageParseException extends Exception {
    private static final long serialVersionUID = 4798323133341909811L;
    private String rawMessage;

    public MessageParseException() {
    }

    public MessageParseException(String message) {
        super(message);
    }

    public MessageParseException(Throwable cause) {
        super(cause);
    }

    public MessageParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public MessageParseException(String message, String rawMessage) {
        super(message);
        this.rawMessage = rawMessage;
    }

    public MessageParseException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public String getRawMessage() {
        return rawMessage;
    }
}
