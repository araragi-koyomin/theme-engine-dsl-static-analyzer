grammar DslExpression;

// Entry rules: AstBuilder selects by attribute expressionKind
// Note: `expression` is reused by exprList/array index, so it has no EOF.
// AstBuilder checks for leftover tokens after parsing to reject partial matches.
expression            : comparisonExpr                          // generic (auto/null)
                      ;

stringExpression      : stringConcat EOF                       // string context
                      | numericExpression EOF                  // pure numeric value coerced to string
                      ;

stringConcat          : stringTerm ('+' stringTerm)* ;          // + is always concatenation in string context

stringTerm            : STRING                                  // quoted string literal
                      | atVarRef                                // @var string variable
                      | functionCall
                      | hashVarRef                              // bare #var / NUMBER embedded numeric
                      | NUMBER
                      | '{' numericExpression '}'              // braced numeric sub-expression
                      ;

numericExpression     : numericMultiplicative (('+'|'-') numericMultiplicative)* ;

numericMultiplicative : numericTerm (('*'|'/'|'%') numericTerm)* ;

numericTerm           : NUMBER
                      | hashVarRef                              // #var numeric variable
                      | functionCall
                      | '-' numericTerm
                      | '(' numericExpression ')'
                      ;

// Generic (auto/null) unified grammar
comparisonExpr        : additiveExpr (COMP_OP additiveExpr)? ;
additiveExpr          : multiplicativeExpr (('+'|'-') multiplicativeExpr)* ;
multiplicativeExpr    : primaryExpr (('*'|'/'|'%') primaryExpr)* ;
primaryExpr           : '-' primaryExpr
                       | functionCall
                       | variableRef
                       | literal
                       | '(' expression ')'
                       | '{' expression '}'
                       ;

functionCall          : ID '(' exprList? ')' ;
variableRef           : hashVarRef | atVarRef ;
hashVarRef            : '#' varName ('[' expression ']')? ;
atVarRef              : '@' varName ('[' expression ']')? ;
varName               : ID | VAR_ID ;
literal               : NUMBER | STRING ;
exprList              : expression (',' expression)* ;

COMP_OP  : '>' | '<' | '>=' | '<=' | '==' | '!=' ;
NUMBER  : [0-9]+ ('.' [0-9]+)? ;
STRING  : '\'' (~'\'' | '\\\'')* '\'' ;
ID      : [a-zA-Z_][a-zA-Z0-9_]* ;
VAR_ID  : [a-zA-Z_][a-zA-Z0-9_.]* ;
WS      : [ \t\r\n]+ -> skip ;
