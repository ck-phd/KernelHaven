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
import java.util.Scanner;

/**
 * This class manages the actual communication between a KernelHaven {@link Client} and a KernelHaven {@link Server}. It
 * uses the respective {@link Socket} to provide methods for sending and receiving messages.
 * 
 * @author Christian Kroeher
 */
public class Connection {
    
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
     * Constructs a new connection instance. That instance manages the communication between the caller of this method
     * and the given {@link #target} by providing {@link #send(String)} and {@link #receive()} methods.
     * 
     * @param target the socket representing the target of this connection instance
     * @throws NetException if the target (socket) is <code>null</code>, not reachable or determining its input or
     *         output stream fails
     */
    private Connection(Socket target) throws NetException {
        if (target != null) {
            try {
                if (target.getInetAddress().isReachable(2000)) {                    
                    this.target = target;
                    try {                        
                        init();
                    } catch (NetException e) {
                        throw new NetException("Initializing the connection to " + this.target + " failed", e);
                    }
                } else {
                    throw new NetException("The target (socket) for this connection is not reachable");
                }
            } catch (IOException e) {
                throw new NetException("An I/O error occurs when trying the reach the target (socket) for this"
                        + "connection", e);
            }
        } else {
            throw new NetException("The target (socket) for this connection is \"null\""); 
        }
    }
    
    /**
     * Initializes a new connection instance by determining the {@link #target} input stream for that instance's
     * {@link #targetInputScanner} and by determining its output stream for its {@link #targetOutputWriter}.
     * 
     * @throws NetException if determining the {@link #target} input or output stream fails 
     */
    private void init() throws NetException {
        // Get target (socket) output writer
        try {
            targetOutputWriter = new PrintWriter(target.getOutputStream(), true);
        } catch (IOException e) {
            throw new NetException("Creating the target (socket) output stream failed or the client socket is not"
                    + "connected", e);
        }
        // Get target (socket) input scanner
        try {
            targetInputScanner = new Scanner(target.getInputStream());
        } catch (IOException e) {
            throw new NetException("Creating the target (socket) input scanner failed as that socket is closed, not" 
                    + " connected, or that socket input has been shutdown internally", e);
        }
    }
    
    /**
     * Sends the given message to the connected {@link #target}.
     * 
     * @param message the message to send to the connected {@link #target}
     */
    public void send(String message) {
        targetOutputWriter.println(message);
    }
    
    /**
     * Returns the output of the connected {@link #target}. That output is typically the return statement for a
     * previously {@link #send(String)} message.
     * 
     * @return the output of the connected {@link #target}
     * @throws NetException if reading the output fails
     */
    public String receive() throws NetException {
        return targetInputScanner.nextLine();
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
    
    /**
     * Creates a new connection instance. That instance manages the communication between the caller of this method and
     * the given {@link #target} by providing {@link #send(String)} and {@link #receive()} methods.
     * 
     * @param target the socket representing the target of this connection instance
     * @return a new connection instance
     * @throws NetException if the target (socket) is <code>null</code>, not reachable or determining its input or
     *         output stream fails
     */
    public static Connection connect(Socket target) throws NetException {
        return new Connection(target);
    }
    
}
