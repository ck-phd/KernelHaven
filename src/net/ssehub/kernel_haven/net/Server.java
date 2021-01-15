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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

import net.ssehub.kernel_haven.util.Logger;

/**
 * This class realizes the KernelHaven server. That server initializes KernelHaven as usual via a given configuration,
 * but postpones the actual analysis. The server executes that analysis every time it receives a message from a client
 * and as long as that message does not solely include the server's {@link #SHUT_DOWN_COMMAND}. 
 *
 * In general, a client message is considered a commit describing changes to the software specified by
 * <i>source_tree</i> in the KernelHaven configuration file. That commit is further processed to reduce the analysis to
 * only those elements affected by the changes. Hence, the server is a fundamental component of incremental KernelHaven.
 * 
 * @author Christian Kroeher
 */
public class Server {
    
    /**
     * The string to send, if a server instance should shut down.
     */
    public static final String SHUT_DOWN_COMMAND = "shutdown";
    
    /**
     * The {@link Logger} for logging messages of this class.
     */
    private static final Logger LOGGER = Logger.get();
    
    /**
     * The default IP of a server instance, if no custom IP is defined by the user.
     */
    private static final String DEFAULT_IP = "127.0.0.1";
    
    /**
     * The default port of a server instance, if no custom port is defined by the user.
     */
    private static final int DEFAULT_PORT = 3141;
    
    /**
     * The default server backlog of a server instance, which is the implementation-specific default.
     * 
     * @see ServerSocket
     */
    private static final int DEFAULT_BACKLOG = 0;
    
    /**
     * The current server instance.
     */
    private static Server instance;
    
    /**
     * The task this server instance executes every time it receives a message from a client.
     */
    private IServerTask serverTask;
    
    /**
     * The socket of this server instance. 
     */
    private ServerSocket serverSocket;
    
    /**
     * The indicator for shutting down this server instance. The default value is <code>false</code>, which keeps the
     * server alive. If a message solely contains the {@link #SHUT_DOWN_COMMAND}, that value changes to
     * <code>true</code> and the server closes. 
     */
    private boolean shutdown;
    
    /**
     * Constructs the single instance of the KernelHaven server.
     * 
     * @param ip the IP at which this server will be available
     * @param port the port at which this server will be available
     * @param backlog the requested maximum length of the queue of incoming connections of this server
     * @param serverTask the task this server executes every time it receives a message from a client
     * @throws NetException if initializing the server fails
     */
    private Server(String ip, int port, int backlog, IServerTask serverTask) throws NetException {
        shutdown = false;
        serverSocket = null;
        this.serverTask = serverTask;
        try {
            init(ip, port, backlog);
        } catch (NetException e) {
            throw new NetException("Initializing server failed", e);
        }
    }
    
    /**
     * Initializes the single server instance by checking the availability of the server task and creating the server
     * socket.
     * 
     * @param ip the IP at which this server will be available
     * @param port the port at which this server will be available
     * @param backlog the requested maximum length of the queue of incoming connections of this server
     * @throws NetException if the given server task is <code>null</code> or creating the server socket fails
     */
    private void init(String ip, int port, int backlog) throws NetException {
        if (serverTask == null) {
            throw new NetException("The task to execute by the server is \"null\"");
        }
        try {
            InetAddress inetAdress = InetAddress.getByName(ip);
            serverSocket = new ServerSocket(port, backlog, inetAdress);
        } catch (UnknownHostException e) {
            throw new NetException("No IP address for the host \"" + ip + "::" + port 
                    + "\" could be found, or a scope_id was specified for a global IPv6 address", e);
        } catch (IOException e) {
            throw new NetException("An I/O error occurs when opening the server socket \"" + ip + "::" + port + "\"",
                    e);
        } catch (IllegalArgumentException e) {
            throw new NetException("The port parameter \"" + port + "\" is outside the specified range of valid port" 
                    + " values, which is between 0 and 65535, inclusive", e);
        }
    }
    
    /**
     * Executes the main server activities as long as it does not receive the {@link #SHUT_DOWN_COMMAND}.
     */
    private void run() {
        // Print server information
        LOGGER.logInfo("Running " + this);
        // Handle incoming connections until shut down
        while (!shutdown) {            
            try {
                handleConnection();
            } catch (NetException e) {
                LOGGER.logExceptionInfo("An error occurs when handling connections of " + this, e);
            }
        }
        try {
            close();
        } catch (NetException e) {
            LOGGER.logException("An error occurs when closing the socket of " + this, e);
        }
    }
    
    /**
     * Manages incoming client messages. This method waits until the {@link #serverSocket} accepts the next incoming
     * connection. For each accepted connection, this method reads the corresponding client message. If that message
     * solely contains the {@link #SHUT_DOWN_COMMAND}, it initiates closing the server. In all other cases, this method
     * passes the message to the {@link #serverTask} for execution and replies to the client using that task's execution
     * summary.
     * 
     * @throws NetException if the connection fails
     */
    private void handleConnection() throws NetException {
        Connection connection = null;
        try {
            connection = Connection.connect(serverSocket.accept());
            LOGGER.logInfo("Receiving message from " + connection);
            String clientMessage = connection.receive();
            if (clientMessage.strip().equals(SHUT_DOWN_COMMAND)) {
                LOGGER.logInfo("Receiving shutdown message from " + connection);
                connection.send("Shutting down");
                shutdown = true;
            } else {
                serverTask.execute(clientMessage);
                connection.send(serverTask.getExecutionSummary());
            }        
        } catch (IOException e) {
            throw new NetException("An I/O error occurs when waiting for a client connection", e);
        } catch (NetException e) {
            throw new NetException("A connection error occurs when establishing the client connection", e);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (NetException e) {
                    throw new NetException("A connection error occurs when establishing the client connection", e);
                }
            }
        }
    }
    
    /**
     * Closes this server.
     * 
     * @throws NetException if closing this server fails
     */
    private void close() throws NetException {
        if (serverSocket != null) {            
            try {
                serverSocket.close();
            } catch (IOException e) {
                throw new NetException("An I/O error occurs when closing the server socket", e);
            }
        } else {
            throw new NetException("No server socket to close");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "Server@" + serverSocket.toString();
    }
    
    /**
     * Starts the single instance of the KernelHaven server. That server uses the given network address and executes the
     * given server task every time it receives a message from a client.
     * 
     * @param networkAddress the network address at which this server will be available; valid values must be of the
     *        form <i>IP::PORT</i>, e.g. 127.0.0.1::3141
     * @param serverTask the task this server executes every time it receives a message from a client
     * @throws NetException if creating or starting the server fails
     */
    public static void start(String networkAddress, IServerTask serverTask) throws NetException {
        // networkAddress = "<IP>::<PORT>"
        if (networkAddress != null && !networkAddress.isBlank()) {
            String[] splittedNetworkAdress = networkAddress.split("::");
            if (splittedNetworkAdress.length == 2) {
                try {                        
                    String ip = splittedNetworkAdress[0];
                    int port = Integer.parseInt(splittedNetworkAdress[1]);
                    start(ip, port, DEFAULT_BACKLOG, serverTask);
                } catch (NumberFormatException e) {
                    throw new NetException("Parsing custom port number \"" + splittedNetworkAdress[1] + "\" failed", e);
                }
            } else {
                throw new NetException("Custom network address \"" + networkAddress 
                        + "\" does not match format \"<IP>::<PORT>\", e.g. \"127.0.0.1::3141\"");
            }
        }        
    }
    
    /**
     * Starts the single instance of the KernelHaven server. That server uses the {@link #DEFAULT_IP} and the
     * {@link #DEFAULT_PORT} as its network address and executes the given server task every time it receives a message
     * from a client.
     * 
     * @param serverTask the task this server executes every time it receives a message from a client
     * @throws NetException if creating or starting the server fails
     */
    public static void start(IServerTask serverTask) throws NetException {
        start(DEFAULT_IP, DEFAULT_PORT, DEFAULT_BACKLOG, serverTask);
    }
    
    /**
     * Starts the single instance of the KernelHaven server. That server uses the given ip, port, and backlog as its
     * network address and executes the given server task every time it receives a message from a client.
     * 
     * @param ip the IP at which this server will be available
     * @param port the port at which this server will be available
     * @param backlog the requested maximum length of the queue of incoming connections of this server
     * @param serverTask the task this server executes every time it receives a message from a client
     * @throws NetException if creating or starting the server fails
     */
    private static void start(String ip, int port, int backlog, IServerTask serverTask) throws NetException {
        if (instance == null) {
            instance = new Server(ip, port, backlog, serverTask);
            new Thread(
                    new Runnable() {
                        @Override
                        public void run() {
                            instance.run();
                        }
                    }
                    ).start();
        } else {
            throw new NetException("Already running " + instance);
        }
    }
    
    /**
     * Stops the single instance of the KernelHaven server.
     * 
     * @throws NetException if stopping the server fails
     */
    public static void stop() throws NetException {
        instance.close();
    }
    
}
