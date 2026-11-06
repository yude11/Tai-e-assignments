/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.analysis.dataflow.analysis.constprop;

import pascal.taie.analysis.dataflow.analysis.AbstractDataflowAnalysis;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.*;
import pascal.taie.ir.stmt.DefinitionStmt;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.type.PrimitiveType;
import pascal.taie.language.type.Type;
import pascal.taie.util.AnalysisException;

public class ConstantPropagation extends
        AbstractDataflowAnalysis<Stmt, CPFact> {

    public static final String ID = "constprop";

    public ConstantPropagation(AnalysisConfig config) {
        super(config);
    }

    @Override
    public boolean isForward() {
        return true;
    }

    @Override
    public CPFact newBoundaryFact(CFG<Stmt> cfg) {
        // 将当前函数IR中所有变量初始化为NAC
        CPFact fact = new CPFact();
        for (Var param : cfg.getIR().getParams()) {
            // 忽略不是int的类型
            if (canHoldInt(param)) {
                fact.update(param, Value.getNAC());
            }
        }
        return fact;
    }

    @Override
    public CPFact newInitialFact() {
        return new CPFact();
    }

    @Override
    public void meetInto(CPFact fact, CPFact target) {
        // 遍历fact，fact中有的var与target中的元素做meet操作
        fact.forEach((var, value) ->
                target.update(var, meetValue(value, target.get(var))));
    }

    /**
     * Meets two Values.
     */
    public Value meetValue(Value v1, Value v2) {
        // 只要有NAC就返回NAC
        if (v1.isNAC() || v2.isNAC()) {
            return Value.getNAC();
        }
        // 如果有Undef就返回另一个
        if (v1.isUndef()) {
            return v2;
        }
        if (v2.isUndef()) {
            return v1;
        }
        // 如果都是常数且相等，就返回原值
        if (v1.equals(v2)) {
            return Value.makeConstant(v1.getConstant());
        }
        return Value.getNAC();
    }

    @Override
    public boolean transferNode(Stmt stmt, CPFact in, CPFact out) {
        CPFact newOut = in.copy();
        // int a = 1; 和 a = b; 都算是DefinitionStmt
        if (stmt instanceof DefinitionStmt<?, ?> def) {
            // 限制了进行分析的变量为INT的大类, 左操作数可以是a.b,所以要忽略这个
            if (def.getLValue() instanceof Var var && canHoldInt(var)) {
                newOut.update(var, evaluate(def.getRValue(), in));
            }
        }
        // 和上一轮的out相比有没有变化
        return out.copyFrom(newOut);
    }

    /**
     * @return true if the given variable can hold integer value, otherwise false.
     */
    public static boolean canHoldInt(Var var) {
        Type type = var.getType();
        if (type instanceof PrimitiveType) {
            switch ((PrimitiveType) type) {
                case BYTE:
                case SHORT:
                case INT:
                case CHAR:
                case BOOLEAN:
                    return true;
            }
        }
        return false;
    }

    /**
     * Evaluates the {@link Value} of given expression.
     *
     * @param exp the expression to be evaluated
     * @param in  IN fact of the statement
     * @return the resulting {@link Value}
     */
    public static Value evaluate(Exp exp, CPFact in) {
        // 如果是常量直接返回
        if (exp instanceof IntLiteral lit) {
            return Value.makeConstant(lit.getValue());
        }
        // 如果是一个变量，根据变量的属性返回，同时不接受非整数类型，返回NAC
        if (exp instanceof Var var) {
            return canHoldInt(var) ? in.get(var) : Value.getNAC();
        }
        // 如果是二元表达式, 算出其对应的值
        if (exp instanceof BinaryExp bin) {
            BinaryExp.Op op = bin.getOperator();
            Value v1 = evaluate(bin.getOperand1(), in);
            Value v2 = evaluate(bin.getOperand2(), in);
            // 首先要排除除零的情况，不管左操作数是什么，除零都要返回Undef
            if ((op == ArithmeticExp.Op.DIV || op == ArithmeticExp.Op.REM)
                    && v2.isConstant() && v2.getConstant() == 0) {
                return Value.getUndef();
            }
            if (v1.isConstant() && v2.isConstant()) {
                int i1 = v1.getConstant();
                int i2 = v2.getConstant();
                if (bin instanceof ArithmeticExp ari) {
                    return switch (ari.getOperator()) {
                        // 算数运算
                        case DIV -> Value.makeConstant(i1 / i2);
                        case REM -> Value.makeConstant(i1 % i2);
                        case ADD -> Value.makeConstant(i1 + i2);
                        case SUB -> Value.makeConstant(i1 - i2);
                        case MUL -> Value.makeConstant(i1 * i2);
                    };
                }
                if (bin instanceof ConditionExp condition) {
                    return switch (condition.getOperator()) {
                        case EQ -> Value.makeConstant(i1 == i2 ? 1 : 0);
                        case GE -> Value.makeConstant(i1 >= i2 ? 1 : 0);
                        case LT -> Value.makeConstant(i1 < i2 ? 1 : 0);
                        case LE -> Value.makeConstant(i1 <= i2 ? 1 : 0);
                        case GT -> Value.makeConstant(i1 > i2 ? 1 : 0);
                        case NE -> Value.makeConstant(i1 != i2 ? 1 : 0);
                    };
                }
                if (bin instanceof BitwiseExp bit) {
                    return switch (bit.getOperator()) {
                        case AND -> Value.makeConstant(i1 & i2);
                        case OR -> Value.makeConstant(i1 | i2);
                        case XOR -> Value.makeConstant(i1 ^ i2);
                    };
                }
                if (bin instanceof ShiftExp sh) {
                    return switch (sh.getOperator()) {
                        case SHL -> Value.makeConstant(i1 << i2);
                        case SHR -> Value.makeConstant(i1 >> i2);
                        case USHR -> Value.makeConstant(i1 >>> i2);
                    };
                }
            }
            if (v1.isNAC() || v2.isNAC()) {
                return Value.getNAC();
            }
            return Value.getUndef();
        }
        // NeverReachHere
        return Value.getNAC();
    }
}
