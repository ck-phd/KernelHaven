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

/**
 * This interface enables an implementing class to execute its specific actions every time the KernelHaven
 * {@link Server} receives a message from a client. That server calls {@link #execute(String)} passing the content of
 * the received client message and directly calls {@link #getExecutionSummary()} after that execution is finished. The
 * execution summary is send back to the client as the server answer to its message.
 * 
 * @author Christian Kroeher
 */
public interface IServerTask {

    /**
     * Executes this task.
     * 
     * @param input the input this task requires for execution; may be <code>null</code>, if the implementing class does
     *        not require any input
     * @see {@link IServerTask#getExecutionSummary()} for receiving details on the success of executing this task
     */
    public void execute(String input);
    
    /**
     * Provides a textual description (simple result value, explanation, summary, etc.) of the execution result of this
     * task.
     *  
     * @return a textual description of the execution result of this task
     */
    public String getExecutionSummary();
    
}
