/*
 * Copyright 2017-2019 University of Hildesheim, Software Systems Engineering
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
package net.ssehub.kernel_haven.util.logic;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayOutputStream;

import org.junit.Assert;
import org.junit.Test;

import net.ssehub.kernel_haven.util.Logger;

/**
 * Tests the {@link DisjunctionQueue}.
 * @author El-Sharkawy
 *
 */
public class DisjunctionQueueTests {

    /**
     * Tests the general ability to create disjunctions, without any simplifications.
     */
    @Test
    public void  testCreateDisjunction() {
        DisjunctionQueue queue = new DisjunctionQueue(false);
        
        // 1st test with constant values
        queue.add(True.INSTANCE);
        queue.add(False.INSTANCE);
        Formula f = queue.getDisjunction();
        Assert.assertEquals(new Disjunction(True.INSTANCE, False.INSTANCE), f);
        
        // 2nd test with variable
        Variable varA = new Variable("A");
        queue.add(varA);
        queue.add(varA);
        f = queue.getDisjunction();
        Assert.assertEquals(new Disjunction(varA, varA), f);
    }
    
    /**
     * Tests if can simplify constantly true parts.
     */
    @Test
    public void  testSimplifyTrue() {
        DisjunctionQueue queue = new DisjunctionQueue(true);
        
        // 1st test: Add true first
        queue.add(True.INSTANCE);
        queue.add(False.INSTANCE);
        Formula f = queue.getDisjunction();
        Assert.assertEquals(True.INSTANCE, f);
        
        // 2nd test: Add true last
        queue.add(False.INSTANCE);
        queue.add(True.INSTANCE);
        f = queue.getDisjunction();
        Assert.assertEquals(True.INSTANCE, f);
    }
    
    /**
     * Tests if can simplify constantly true parts.
     */
    @Test
    public void  testFlaseConstraint() {
        DisjunctionQueue queue = new DisjunctionQueue(true);
        
        // 1st test: Add false first
        queue.add(False.INSTANCE);
        queue.add(False.INSTANCE);
        Formula f = queue.getDisjunction();
        Assert.assertEquals(False.INSTANCE, f);
    }
    
    /**
     * Test that it avoids insertion of the same element twice.
     */
    @Test
    public void  testAvoidDoubledElements() {
        DisjunctionQueue queue = new DisjunctionQueue(true);
        
        Variable varA = new Variable("A");
        queue.add(varA);
        queue.add(varA);
        Formula f = queue.getDisjunction();
        Assert.assertEquals(varA, f);
    }
    
    /**
     * Tests if a simplifier can be used.
     */
    @Test
    public void  testUseSimplifier() {
        /* 
         * A simplifier mock (will change the result to a fixed expression),
         * not a real simplifier but sufficient for testing
         */
        Variable varB = new Variable("B");
        DisjunctionQueue queue = new DisjunctionQueue(f -> varB);
        
        Variable varA = new Variable("A");
        queue.add(varA);
        Formula f = queue.getDisjunction();
        Assert.assertSame(varB, f);
    }
    
    /**
     * Tests that the {@link DisjunctionQueue} produces no error log in case of correct optimizations.
     * Based on detect bug.
     */
    @Test
    public void testNoErrorLogWhenSimplifyingTrue() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Logger.get().addTarget(buffer);
        
        DisjunctionQueue queue = new DisjunctionQueue(true);
        queue.add(new Variable("X"));
        queue.add(True.INSTANCE);
        queue.getDisjunction("Test Case");
        
        String log = buffer.toString();
        Assert.assertTrue("Error log produced even if there was no error: " + log, log.isEmpty());
        
        Logger.get().removeTarget(Logger.get().getTargets().size() - 1); // remove the buffer target again.
    }
    
    /**
     * Tests that an empty queue returns false.
     */
    @Test
    public void testEmptyIsFalse() {
        DisjunctionQueue queue = new DisjunctionQueue(true);
        assertThat(queue.getDisjunction(), is(False.INSTANCE));
        
        queue = new DisjunctionQueue(false);
        assertThat(queue.getDisjunction(), is(False.INSTANCE));
    }
    
    /**
     * Test that adding null is ignored.
     */
    @Test
    public void  testAddNull() {
        DisjunctionQueue queue = new DisjunctionQueue(false);
        
        Variable varA = new Variable("A");
        queue.add(varA);
        queue.add(null);
        Formula f = queue.getDisjunction();
        Assert.assertEquals(varA, f);
    }
    
}
