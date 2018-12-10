package net.ssehub.kernel_haven.code_model.ast;

import static net.ssehub.kernel_haven.util.null_checks.NullHelpers.notNull;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.ssehub.kernel_haven.code_model.AbstractCodeElement;
import net.ssehub.kernel_haven.code_model.CodeElement;
import net.ssehub.kernel_haven.code_model.JsonCodeModelCache.CheckedFunction;
import net.ssehub.kernel_haven.util.FormatException;
import net.ssehub.kernel_haven.util.io.json.JsonElement;
import net.ssehub.kernel_haven.util.io.json.JsonList;
import net.ssehub.kernel_haven.util.io.json.JsonNumber;
import net.ssehub.kernel_haven.util.io.json.JsonObject;
import net.ssehub.kernel_haven.util.io.json.JsonString;
import net.ssehub.kernel_haven.util.logic.Formula;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.util.null_checks.Nullable;

/**
 * <p>
 * Represents an <tt>if</tt>, <tt>else</tt> or an <tt>else if</tt> block. The
 * children nested inside this element are the branch statement body.
 * </p>
 * <p>
 * It contains a list of siblings: each {@link BranchStatement} of an
 * if-elseif-else construct has references to all siblings in the same construct
 * (including itself). The ordering of these is always the same as in the
 * original source code.
 * </p>
 *
 * @author Adam
 * @author El-Sharkawy
 */
public class BranchStatement extends AbstractSyntaxElementWithNesting {

    /**
     * The type of {@link BranchStatement}.
     */
    public static enum Type {
        IF, ELSE, ELSE_IF;
    }

    private @NonNull Type type;

    private @Nullable ICode ifCondition;

    private @NonNull List<@NonNull BranchStatement> siblings;
    
    private @Nullable List<@NonNull Integer> serializationSiblingIds;

    /**
     * Creates a {@link BranchStatement}.
     * 
     * @param presenceCondition
     *            The presence condition for this element.
     * @param type
     *            Which {@link Type} of branching statement this is.
     * @param ifCondition
     *            The condition of an <tt>if</tt> or <tt>else if</tt> block;
     *            <tt>null</tt> if this is an <tt>else</tt> block (in this case
     *            {@link Type#ELSE} must be passed as <tt>type</tt>).
     */
    public BranchStatement(@NonNull Formula presenceCondition, @NonNull Type type, @Nullable ICode ifCondition) {

        super(presenceCondition);
        this.ifCondition = ifCondition;
        this.type = type;
        siblings = new LinkedList<>();
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
    protected BranchStatement(@NonNull JsonObject json,
        @NonNull CheckedFunction<@NonNull JsonElement, @NonNull CodeElement<?>, FormatException> deserializeFunction)
        throws FormatException {
        super(json, deserializeFunction);
        
        this.type = Type.valueOf(json.getString("branchType"));
        if (json.getElement("branchCondition") != null) {
            this.ifCondition = (ICode) deserializeFunction.apply(json.getObject("branchCondition"));
        }
        
        JsonList siblingIds = json.getList("branchSiblings");
        List<@NonNull Integer> serializationSiblingIds = new ArrayList<>(siblingIds.getSize());
        for (JsonElement siblingId : siblingIds) {
            serializationSiblingIds.add((Integer) ((JsonNumber) siblingId).getValue());
        }
        
        this.serializationSiblingIds = serializationSiblingIds;
        this.siblings = new LinkedList<>(); // will be filled in resolveIds()
    }

    /**
     * Adds another sibling to this {@link BranchStatement}. This should only be
     * called by the extractors that creates the AST. It should be ensured that
     * all siblings have a complete list of all siblings in a given
     * if-elseif-else construct (including themselves).
     * 
     * @param sibling
     *            The sibling to add.
     */
    public void addSibling(@NonNull BranchStatement sibling) {
        siblings.add(sibling);
    }

    /**
     * Returns the number of siblings this element has. This is at lest one
     * (this object itself).
     * 
     * @return The number of siblings.
     */
    public int getSiblingCount() {
        return siblings.size();
    }

    /**
     * Returns the sibling at the given index.
     * 
     * @param index
     *            The index to get the sibling for.
     * @return The sibling at the given index.
     * 
     * @throws IndexOutOfBoundsException
     *             If index is out of bounds.
     */
    public @NonNull BranchStatement getSibling(int index) throws IndexOutOfBoundsException {
        return notNull(siblings.get(index));
    }

    /**
     * Returns the condition of this {@link BranchStatement}. <code>null</code>
     * if this is an else block.
     * 
     * @return The condition of this {@link BranchStatement}.
     */
    public @Nullable ICode getIfCondition() {
        return ifCondition;
    }

    /**
     * Returns the type of this {@link BranchStatement}.
     * 
     * @return The type of branching statement.
     */
    public @NonNull Type getType() {
        return type;
    }

    @Override
    public @NonNull String elementToString(@NonNull String indentation) {
        String result = type.name() + " (" + getSiblingCount() + " siblings)\n";
        if (ifCondition != null) {
            result += ifCondition.toString(indentation + "\t");
        }
        return result;
    }

    @Override
    public void accept(@NonNull ISyntaxElementVisitor visitor) {
        visitor.visitBranchStatement(this);
    }
    
    @Override
    protected int hashCode(@NonNull CodeElementHasher hasher) {
        int result = 1;
        
        for (BranchStatement sibling : siblings) {
            result = 31 * result + hasher.hashCode(sibling);
        }
        
        return result + super.hashCode(hasher) + type.hashCode()
                + (ifCondition != null ? hasher.hashCode((AbstractCodeElement<?>) ifCondition) : 123);
    }

    @Override
    protected boolean equals(@NonNull AbstractCodeElement<?> other, @NonNull CodeElementEqualityChecker checker) {
        boolean equal = other instanceof BranchStatement && super.equals(other, checker);
        
        if (equal) {
            BranchStatement o = (BranchStatement) other;
            
            if (this.ifCondition != null && o.ifCondition != null) {
                equal = this.type == o.type && checker.isEqual((AbstractCodeElement<?>) this.ifCondition,
                        (AbstractCodeElement<?>) o.ifCondition);
            } else {
                equal = this.type == o.type && this.ifCondition == o.ifCondition;
            }
            
            equal &= this.siblings.size() == o.siblings.size();
            for (int i = 0; equal && i < this.siblings.size(); i++) {
                equal &= checker.isEqual(this.siblings.get(i), o.siblings.get(i));
            }
        }
        
        return equal;
    }

    @Override
    public void serializeToJson(JsonObject result,
            @NonNull Function<@NonNull CodeElement<?>, @NonNull JsonElement> serializeFunction,
            @NonNull Function<@NonNull CodeElement<?>, @NonNull Integer> idFunction) {
        super.serializeToJson(result, serializeFunction, idFunction);

        result.putElement("branchType", new JsonString(notNull(type.name())));
        if (ifCondition != null) {
            result.putElement("branchCondition", serializeFunction.apply(ifCondition));
        }

        JsonList siblingIds = new JsonList();
        for (BranchStatement sibling : siblings) {
            siblingIds.addElement(new JsonNumber(idFunction.apply(sibling)));
        }

        result.putElement("branchSiblings", siblingIds);
    }
    
    @Override
    public void resolveIds(Map<Integer, CodeElement<?>> mapping) throws FormatException {
        super.resolveIds(mapping);
        
        List<@NonNull Integer> serializationSiblingIds = this.serializationSiblingIds;
        this.serializationSiblingIds = null;
        if (serializationSiblingIds == null) {
            throw new FormatException("Did not get de-erialization IDs");
        }
        
        for (Integer id : serializationSiblingIds) {
            BranchStatement sibling = (BranchStatement) mapping.get(id);
            if (sibling == null) {
                throw new FormatException("Unknown ID: " + id);
            }
            this.siblings.add(sibling);
        }
    }

}
