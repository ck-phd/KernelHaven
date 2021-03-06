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
package net.ssehub.kernel_haven.code_model.ast;

import java.io.File;
import java.util.NoSuchElementException;
import java.util.function.Function;

import net.ssehub.kernel_haven.code_model.AbstractCodeElementWithNesting;
import net.ssehub.kernel_haven.code_model.CodeElement;
import net.ssehub.kernel_haven.code_model.JsonCodeModelCache.CheckedFunction;
import net.ssehub.kernel_haven.util.FormatException;
import net.ssehub.kernel_haven.util.io.json.JsonElement;
import net.ssehub.kernel_haven.util.io.json.JsonObject;
import net.ssehub.kernel_haven.util.logic.Formula;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.util.null_checks.Nullable;

/**
 * A {@link AbstractSyntaxElementNoNesting} that has children.
 * 
 * @author Adam
 */
abstract class AbstractSyntaxElementWithNesting extends AbstractCodeElementWithNesting<ISyntaxElement>
        implements ISyntaxElement {

    private boolean containsErrorElement;
    
    /**
     * Creates this {@link AbstractSyntaxElementNoNesting} with the given presence
     * condition.
     * 
     * @param presenceCondition
     *            The presence condition of this element.
     */
    public AbstractSyntaxElementWithNesting(@NonNull Formula presenceCondition) {
        super(presenceCondition);
    }
    
    /**
     * De-serializes the given JSON to a {@link CodeElement}. This is the inverse operation to
     * {@link #serializeToJson(JsonObject, Function, Function)}.
     * 
     * @param json The JSON do de-serialize.
     * @param deserializeFunction The function to use for de-serializing secondary nested elements. Do not use this to
     *      de-serialize the {@link CodeElement}s in the primary nesting structure!
     *      (i.e. {@link #getNestedElement(int)})
     * 
     * @throws FormatException If the JSON does not have the expected format.
     */
    protected AbstractSyntaxElementWithNesting(@NonNull JsonObject json,
        @NonNull CheckedFunction<@NonNull JsonElement, @NonNull CodeElement<?>, FormatException> deserializeFunction)
        throws FormatException {
        super(json, deserializeFunction);
    }
    
    @Override
    public void replaceNestedElement(@NonNull ISyntaxElement oldElement, @NonNull ISyntaxElement newElement)
            throws NoSuchElementException {
        
        super.replaceNestedElement(oldElement, newElement);
    }
    
    @Override
    public void setSourceFile(@NonNull File sourceFile) {
        super.setSourceFile(sourceFile);
    }

    @Override
    public void setCondition(@Nullable Formula condition) {
        super.setCondition(condition);
    }

    @Override
    public void setPresenceCondition(@NonNull Formula presenceCondition) {
        super.setPresenceCondition(presenceCondition);
    }
    
    @Override
    public boolean containsErrorElement() {
        return containsErrorElement;
    }
    
    @Override
    public void setContainsErrorElement(boolean containsErrorElement) {
        this.containsErrorElement = containsErrorElement;
    }

    @Override
    public abstract void accept(@NonNull ISyntaxElementVisitor visitor);

}
