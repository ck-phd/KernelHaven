package net.ssehub.kernel_haven.util.logic;

/**
 * A boolean disjunction operator (OR).
 * 
 * @author Adam (from KernelMiner project)
 * @author Sascha El-sharkawy
 */
public final class Disjunction extends Formula {

    private static final long serialVersionUID = 8416793994383200822L;

    private Formula left;
    
    private Formula right;
    
    /**
    * Creates a boolean disjunction (OR).
    * 
    * @param left The left operand.
    * @param right The right operand.
    */
    public Disjunction(Formula left, Formula right) {
        this.left = left;
        this.right = right;
    }
    
    /**
     * Returns the formula that is nested on the left side of this disjunction.
     * 
     * @return The left operand.
     */
    public Formula getLeft() {
        return left;
    }
    
    /**
     * Returns the formula that is nested on the right side of this disjunction.
     * 
     * @return The right operand.
     */
    public Formula getRight() {
        return right;
    }
    
    @Override
    public boolean evaluate() {
        return left.evaluate() || right.evaluate();
    }

    @Override
    public String toString() {
        String leftStr = left.toString();
        if (!(left instanceof Disjunction) && left.getPrecedence() <= this.getPrecedence()) {
            leftStr = '(' + leftStr + ')';
        }
        
        String rightStr = right.toString();
        if (!(right instanceof Disjunction) && right.getPrecedence() <= this.getPrecedence()) {
            rightStr = '(' + rightStr + ')';
        }
        
        return leftStr + " || " + rightStr;
    }
    
    @Override
    public void toString(StringBuffer result) {
        if (!(left instanceof Disjunction) && left.getPrecedence() <= this.getPrecedence()) {
            result.append('(');
            left.toString(result);
            result.append(')');
        } else {
            left.toString(result);
        }
        
        result.append(" || ");
                
        if (!(right instanceof Disjunction) && right.getPrecedence() <= this.getPrecedence()) {
            result.append('(');
            right.toString(result);
            result.append(')');
        } else {
            right.toString(result);
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Disjunction) {
            Disjunction other = (Disjunction) obj;
            return left.equals(other.getLeft()) && right.equals(other.getRight());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (left.hashCode() + right.hashCode()) * 213;
    }
    
    @Override
    public int getLiteralSize() {
        return left.getLiteralSize() + right.getLiteralSize();
    }
    
    @Override
    protected <T> T accept(IFormulaVisitor<T> visitor) {
        return visitor.visitDisjunction(this);
    }
    
    @Override
    protected void accept(IVoidFormulaVisitor visitor) {
        visitor.visitDisjunction(this);
    }
    
    @Override
    protected int getPrecedence() {
        return 1;
    }
    
}
