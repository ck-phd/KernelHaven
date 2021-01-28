/*
 * Copyright 2021 University of Hildesheim, Software Systems Engineering
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.ssehub.kernel_haven.net;

import net.ssehub.kernel_haven.util.Logger;

/**
 * This class realizes the KernelHaven client for sending messages to the KernelHaven {@link Server}. That client-
 * server-infrastructure enables the incremental execution of analyses.
 * 
 * @author Christian Kroeher
 */
public class Client {
    
    /**
     * The {@link Logger} for logging messages of this class.
     */
    private static final Logger LOGGER = Logger.get();
    
    /**
     * The current connection this client instance uses to communicate with a server.
     */
    private Connection connection;
    
    /**
     * Constructs a new client instance. This instance is automatically connected to the server specified by the
     * given server network address.
     * 
     * @param serverNetworkAddress the network address of the server this client instance should be connected to; this
     *        string must be of the form "SERVER_IP::SERVER_PORT", e.g. "127.0.0.1::3141"
     * @throws NetException if initializing this client instance fails
     */
    public Client(String serverNetworkAddress) throws NetException {
        try {
            init(serverNetworkAddress);
            LOGGER.logDebug("Client connected to " + connection);
        } catch (NetException e) {
            throw new NetException("Initializing client fails", e);
        }
    }

    /**
     * Initializes this client instance by establishing a {@link Connection} to the server specified by the given
     * server network address.
     * 
     * @param serverNetworkAddress the network address of the server this client instance should be connected to; this
     *        string must be of the form "SERVER_IP::SERVER_PORT", e.g. "127.0.0.1::3141"
     * @throws NetException if the given server network address does not match the expected form or connecting to the
     *         specified server (network address) fails
     */
    private void init(String serverNetworkAddress) throws NetException {
        if (serverNetworkAddress != null && !serverNetworkAddress.isBlank()) {
            String[] splittedSeverNetworkAdress = serverNetworkAddress.split("::");
            if (splittedSeverNetworkAdress.length == 2) {
                try {               
                    String ip = splittedSeverNetworkAdress[0];
                    int port = Integer.parseInt(splittedSeverNetworkAdress[1]);
                    connection = new Connection(ip, port);
                } catch (NumberFormatException e) {
                    throw new NetException("Parsing server port number \"" + splittedSeverNetworkAdress[1]
                            + "\" failed", e);
                } catch (NetException e) {
                    throw new NetException("A connection error occurs when establishing the server connection", e);
                }
            } else {
                throw new NetException("Server network address \"" + serverNetworkAddress 
                        + "\" does not match format \"<IP>::<PORT>\", e.g. \"127.0.0.1::3141\"");
            }
        } else {
            throw new NetException("Server network address not specified, e.g. \"--client=127.0.0.1::3141\"");
        }
    }
    
    /**
     * Closes the {@link Connection} of this client instance.
     * 
     * @throws NetException if closing the connection fails
     */
    public void close() throws NetException {
        if (connection != null) {            
            connection.close();
            connection = null;
        }
    }
    
    /**
     * Sends the given message to the connected server of this client instance and returns its corresponding reply. The
     * internal {@link Connection} of this client instance will be closed at the end of this method (or if an exception
     * occurs). Hence, calling this method a second time results in a return value of <code>null</code>.
     * 
     * @param message the message to send to the connected server
     * @return the reply of the server (which may be <i>empty</i>) or <code>null</code>, if the connection to the server
     *         is already closed
     */
    public String send(String message) {
        String serverReply = null;
        if (connection != null) {            
            LOGGER.logDebug("Client sending message via " + connection);
            connection.send(message);            
            LOGGER.logDebug("Client waiting for server reply via " + connection);
            serverReply = connection.receive();
            LOGGER.logDebug("Client received server reply via " + connection);
        }
        return serverReply;
    }
    
}
