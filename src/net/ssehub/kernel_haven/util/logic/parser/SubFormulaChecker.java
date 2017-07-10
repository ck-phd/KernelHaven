package net.ssehub.kernel_haven.util.logic.parser;

import net.ssehub.kernel_haven.util.logic.Conjunction;
import net.ssehub.kernel_haven.util.logic.Disjunction;
import net.ssehub.kernel_haven.util.logic.False;
import net.ssehub.kernel_haven.util.logic.Formula;
import net.ssehub.kernel_haven.util.logic.IFormulaVisitor;
import net.ssehub.kernel_haven.util.logic.Negation;
import net.ssehub.kernel_haven.util.logic.True;
import net.ssehub.kernel_haven.util.logic.Variable;

/**
 * Checks if the given formula is contained in the visited formula.
 * @author El-Sharkawy
 *
 */
public class SubFormulaChecker implements IFormulaVisitor {
    
    private Formula nestedFormula;
    private boolean isNested;
    private int formulaSize;
    
    /**
     * Sole constructor for this visitor.
     * The accept method must still be called.
     * @param nestedFormula The formula to check if it is nested inside the visited formula.
     */
    public SubFormulaChecker(Formula nestedFormula) {
        this.nestedFormula = nestedFormula;
        // Size as a small optimization
        formulaSize = nestedFormula.getLiteralSize();
        isNested = false;
    }

    @Override
    public void visitFalse(False falseConstant) {
        visitLeaf(falseConstant);
    }

    @Override
    public void visitTrue(True trueConstant) {
        visitLeaf(trueConstant);
    }

    @Override
    public void visitVariable(Variable variable) {
        visitLeaf(variable);
    }
    
    /**
     * Checks if a visited leaf is equal to the given formula.
     * @param leaf a visited constant or variable.
     */
    private void visitLeaf(Formula leaf) {
        isNested = leaf.equals(nestedFormula);
    }

    @Override
    public void visitNegation(Negation formula) {
        if (!isNested) {
            isNested = formula.equals(nestedFormula);
            
            if (!isNested) {
                // Doesn't change the size of the formula, for this reason no additional size check
                // However, this means that a toplevel negation will also be checked even if it is already to short
                formula.getFormula().accept(this);
            }
        }
    }

    @Override
    public void visitDisjunction(Disjunction formula) {
        if (!isNested) {
            isNested = formula.equals(nestedFormula);
            
            if (!isNested) {
                Formula leftFormula = formula.getLeft();
                if (leftFormula.getLiteralSize() >= formulaSize) {
                    leftFormula.accept(this);
                }
            }
            
            if (!isNested) {
                Formula rightFormula = formula.getRight();
                if (rightFormula.getLiteralSize() >= formulaSize) {
                    rightFormula.accept(this);
                }
            }
        }
    }

    @Override
    public void visitConjunction(Conjunction formula) {
        if (!isNested) {
            isNested = formula.equals(nestedFormula);
            
            if (!isNested) {
                Formula leftFormula = formula.getLeft();
                if (leftFormula.getLiteralSize() >= formulaSize) {
                    leftFormula.accept(this);
                }
            }
            
            if (!isNested) {
                Formula rightFormula = formula.getRight();
                if (rightFormula.getLiteralSize() >= formulaSize) {
                    rightFormula.accept(this);
                }
            }
        }
    }

    /**
     * Returns if the given formula (from the constructor) is nested or equal to the visited formula.
     * @return <tt>true</tt> the given formula is nested (or equal) in the visited formula,
     * <tt>false</tt> otherwise.
     */
    public boolean isNested() {
        return isNested;
    }
}
