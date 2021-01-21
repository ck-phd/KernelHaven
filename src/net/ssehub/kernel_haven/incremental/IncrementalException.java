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
package net.ssehub.kernel_haven.incremental;

/**
 * A package-specific exception type that indicates problems of the incremental analysis capabilities of KernelHaven.
 * 
 * @author Christian Kroeher
 */
public class IncrementalException extends Exception {

    /**
     * The generated serial version UID of this exception type.
     */
    private static final long serialVersionUID = -6086491538636780820L;
    
    /**
     * Constructs a new exception instance with the given message.
     * 
     * @param message the error message of this exception instance
     */
    public IncrementalException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception instance with the given message and the underlying cause of this exception.
     * 
     * @param message the error message of this exception instance
     * @param cause the underlying cause of this exception instance
     */
    public IncrementalException(String message, Throwable cause) {
        super(message, cause);
    }

}
