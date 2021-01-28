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
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

import net.ssehub.kernel_haven.util.Logger;

/**
 * This class manages the actual communication between a KernelHaven {@link Client} and a KernelHaven {@link Server}. It
 * uses the respective {@link Socket} to provide methods for sending and receiving messages.
 * 
 * @author Christian Kroeher
 */
public class Connection {
    
    /**
     * The {@link Logger} for logging messages of this class.
     */
    private static final Logger LOGGER = Logger.get();
    
    /**
     * The string marking the end of a message. If a message has to be send, this string will be added
     * to the end of that message, while reading a line of a received message, which ends with this string, terminates
     * the reading process. 
     * 
     * @see #send(String)
     * @see #receive()
     */
    private static final String END_OF_MESSAGE = "[<EOM>]";
    
    /**
     * The socket representing the target of this connection instance. The target is the client or server to send
     * messages to or receive messages from.
     */
    private Socket target;
    
    /**
     * The writer for sending messages to the {@link target} via its output stream.
     */
    private PrintWriter targetOutputWriter;
    
    /**
     * The scanner for receiving messages from the {@link target} via its input stream.
     */
    private Scanner targetInputScanner;
    
    /**
     * Constructs a new connection instance.
     * 
     * @param ip the IP of the host this connection instance should be connected to
     * @param port the port of the host this connection instance should be connected to
     * @throws NetException if creating the host socket or initializing this connection instance fails
     */
    Connection(String ip, int port) throws NetException {
        try {
            setSocket(ip, port);
        } catch (NetException e) {
            throw new NetException("Creating the host socket for a new connection fails", e);
        }
        try {
            init();
        } catch (NetException e) {
            throw new NetException("Initializing " + this + " fails", e);
        }
    }

    /**
     * Constructs a new connection instance.
     * 
     * @param target the socket representing the target host of this connection instance
     * @throws NetException if setting the given host socket or initializing this connection instance fails
     */
    Connection(Socket target) throws NetException {
        setSocket(target);
        init();
    }
    
    /**
     * Creates a new socket based on the given IP and port number. If this creation is successful, this method sets the
     * created socket via {@link #setSocket(Socket)} as the target (socket) of this connection instance.
     * 
     * @param ip the IP of the host this connection instance should be connected to
     * @param port the port of the host this connection instance should be connected to
     * @throws NetException if creating the target (socket) fails
     */
    private void setSocket(String ip, int port) throws NetException {
        try {
            setSocket(new Socket(ip, port));
        } catch (UnknownHostException e) {
            throw new NetException("The IP address of the host \"" + ip + "::" + port + "\" could not be determined",
                    e);
        } catch (IOException e) {
            throw new NetException("An I/O error occurs when creating the socket for the host \"" + ip + "::" + port
                    + "\"", e);
        } catch (SecurityException e) {
            throw new NetException("A security manager does not allow creating the socket for the host \"" + ip + "::"
                    + port + "\"", e);
        } catch (IllegalArgumentException e) {
            throw new NetException("The port parameter of the host \"" + ip + "::" + port 
                    + "\" is outside the specified range of valid port values, which is between 0 and 65535, inclusive",
                    e);
        }
    }
    
    /**
     * Sets the given socket as the target (socket) of this connection instance.
     * 
     * @param socket the socket to set as target (socket) of this connection instance
     * @throws NetException if the given socket is <code>null</code> or the host specified by the given socket is not
     *         reachable within a timeout of 2 seconds
     */
    private void setSocket(Socket socket) throws NetException {
        if (socket != null) {
            try {
                if (socket.getInetAddress().isReachable(2000)) {                    
                    this.target = socket;
                } else {
                    throw new NetException("The target (socket) " + socket.toString()
                            + " for this connection is not reachable");
                }
            } catch (IOException e) {
                throw new NetException("An I/O error occurs when trying to reach the target (socket) "
                        + socket.toString() + " for this connection", e);
            }
        } else {
            throw new NetException("The target (socket) for this connection is \"null\""); 
        }
    }
    
    /**
     * Initializes a new connection instance by determining the target (socket) input stream for that instance's
     * {@link #targetInputScanner} and its output stream for its {@link #targetOutputWriter}.
     * 
     * @throws NetException if determining the target (socket) input or output stream fails 
     */
    private void init() throws NetException {
        // Get target (socket) output writer
        try {
            targetOutputWriter = new PrintWriter(target.getOutputStream(), true);
        } catch (IOException e) {
            throw new NetException("Creating the target (socket) output stream for " + this 
                    + " failed or that socket is not connected", e);
        }
        // Get target (socket) input scanner
        try {
            targetInputScanner = new Scanner(target.getInputStream());
        } catch (IOException e) {
            throw new NetException("Creating the target (socket) input scanner for " + this
                    + " failed as that socket is closed, not connected, or that socket input has been shutdown"
                    + " internally", e);
        }
    }
    
    /**
     * Sends the given message to the connected target (socket).
     * 
     * @param message the message to send to the connected target (socket)
     */
    public void send(String message) {
        message = message.concat(END_OF_MESSAGE);
        LOGGER.logDebug(this + " sending message:", message);
        targetOutputWriter.println(message);
        // No need to flush as auto-flush is active; see init()
        LOGGER.logDebug(this + " sending done");
    }
    
    /**
     * Returns the received message from the connected target (socket).
     * 
     * @return the message received from the connected target (socket); never <code>null</code>, but may be <i>empty</i>
     */
    public String receive() {
        StringBuilder messageBuilder = new StringBuilder();
        String messageLine = "";
        LOGGER.logDebug(this + " receiving message");
        while (targetInputScanner.hasNextLine()) {
            messageLine = targetInputScanner.nextLine();
            LOGGER.logDebug(this + " current message line: " + messageLine);
            if (messageLine.endsWith(END_OF_MESSAGE)) {
                LOGGER.logDebug(this + " end of message detected");
                messageBuilder.append(messageLine.substring(0, messageLine.length() - END_OF_MESSAGE.length()));
                break;
            }
            messageBuilder.append(messageLine);
            messageBuilder.append(System.lineSeparator());
        }
        LOGGER.logDebug(this + " receiving done");
        return messageBuilder.toString();
    }
    
    /**
     * Closes the target (socket) of this connection as well as the associated output writer and input scanner.
     * 
     * @throws NetException if closing the target (socket) fails
     */
    public void close() throws NetException {
        if (targetOutputWriter != null) {
            targetOutputWriter.flush();
            targetOutputWriter.close();
        }
        if (targetInputScanner != null) {
            targetInputScanner.close();
        }
        if (target != null) {
            try {
                target.close();
            } catch (IOException e) {
                throw new NetException("An I/O error occurs when closing the target (socket)", e);
            }
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "Connection@" + target.toString();
    }
    
}
