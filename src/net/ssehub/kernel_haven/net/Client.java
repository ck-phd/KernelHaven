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

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

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
     * The IP of the server this client instance is currently connected to.
     */
    private String ip;
    
    /**
     * The port of the server this client instance is currently connected to.
     */
    private int port;
    
    /**
     * The current connection this client instance uses to communicate with a server.
     */
    private Connection connection;

    /**
     * Constructs a new client instance. That instance is automatically connected to the server specified by the
     * given IP and port parameters.
     * 
     * @param ip the IP of the server this client instance should be connected to
     * @param port the port of the server this client instance should be connected to
     * @throws NetException if initializing the client or connecting to the desired server fails
     */
    private Client(String ip, int port) throws NetException {
        this.ip = ip;
        this.port = port;
        try {
            init();
            LOGGER.logDebug("Client connected to " + connection);
        } catch (NetException e) {
            throw new NetException("Initializing client failed", e);
        }
    }
    
    /**
     * Initializes a new client instance by establishing a {@link Connection} to the server specified by the 
     * {@link #ip} and {@link #port} of that instance.
     * 
     * @throws NetException if connecting to the desired server fails
     */
    private void init() throws NetException {
        try {
            Socket serverSocket = new Socket(this.ip, this.port);
            connection = Connection.connect(serverSocket);
        } catch (UnknownHostException e) {
            throw new NetException("The IP address of the server \"" + ip + "::" + port + "\" could not be determined",
                    e);
        } catch (IOException e) {
            throw new NetException("An I/O error occurs when creating the socket for the server \"" + ip + "::" + port
                    + "\"", e);
        } catch (SecurityException e) {
            throw new NetException("A security manager does not allow creating the socket for the server \"" + ip + "::"
                    + port + "\"", e);
        } catch (IllegalArgumentException e) {
            throw new NetException("The port parameter of the server \"" + ip + "::" + port 
                    + "\" is outside the specified range of valid port values, which is between 0 and 65535, inclusive",
                    e);
        } catch (NetException e) {
            throw new NetException("A connection error occurs when establishing the server connection", e);
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
        }
    }
    
    /**
     * Sends the given message to the connected server of this client instance and returns its corresponding answer.
     * 
     * @param message the message to send to the connected server
     * @return the answer of the server; may be <code>null</code>
     * @throws NetException if receiving the answer of the server fails
     */
    public String send(String message) throws NetException {
        LOGGER.logDebug("Sending message to " + connection);
        connection.send(message);
        LOGGER.logDebug("Waiting for reply from " + connection);
        String reply = connection.receive();
        LOGGER.logDebug("Receiving reply from " + connection);
        return reply;
    }
    
    /**
     * Creates a new client instance. That instance is automatically connected to the server specified by the
     * given server network address. It enables sending a message to that server, if the connection is successfully
     * established.
     * 
     * @param serverNetworkAddress the network address of the server this client instance should be connected to; that
     *        string must be of the form "SERVER_IP::SERVER_PORT"
     * @return a new client instance connected to the server specified by the given network address
     * @throws NetException if the server network address is not specified (correctly), initializing the client fails,
     *         or connecting to the desired server fails
     */
    public static Client connect(String serverNetworkAddress) throws NetException {
        if (serverNetworkAddress != null && !serverNetworkAddress.isBlank()) {
            String[] splittedSeverNetworkAdress = serverNetworkAddress.split("::");
            if (splittedSeverNetworkAdress.length == 2) {
                try {               
                    String ip = splittedSeverNetworkAdress[0];
                    int port = Integer.parseInt(splittedSeverNetworkAdress[1]);
                    return new Client(ip, port);
                } catch (NumberFormatException e) {
                    throw new NetException("Parsing server port number \"" + splittedSeverNetworkAdress[1]
                            + "\" failed", e);
                }
            } else {
                throw new NetException("Server network address \"" + serverNetworkAddress 
                        + "\" does not match format \"<IP>::<PORT>\", e.g. \"127.0.0.1::3141\"");
            }
        } else {
            throw new NetException("Server network address not specified, e.g. \"--client=127.0.0.1::3141\"");
        }
    }
    
}
