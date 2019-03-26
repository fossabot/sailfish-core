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

package com.exactpro.sf.services.fast.blockstream;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import org.openfast.MessageBlockReader;
import org.openfast.session.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MulticastProxyConnection implements Connection {
    private static final Logger logger = LoggerFactory.getLogger(DatagramConnection.class);
    private final int port;
    private final String host;
    private Socket socket;
    private MulticastProxyInputStream inputStream;

    public MulticastProxyConnection(String host, int port, IPacketHandler iPacketHandler) {
        this.port = port;
        this.host = host;
        try {
            this.socket = new Socket(this.host, this.port);
            this.inputStream = new MulticastProxyInputStream(socket, iPacketHandler);
        } catch (IOException e) {
          logger.error(e.getMessage(), e);
        }

    }



    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public MulticastProxyInputStream getInputStream() {
        return inputStream;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return socket.getOutputStream();
    }

    public MessageBlockReader getBlockReader() {
        return inputStream;
    }
}
