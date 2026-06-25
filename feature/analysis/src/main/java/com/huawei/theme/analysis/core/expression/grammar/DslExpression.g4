grammar DslExpression;

expression
    : additiveExpr
    ;

additiveExpr
    : multiplicativeExpr (('+' | '-') multiplicativeExpr)*
    ;

multiplicativeExpr
    : primaryExpr (('*' | '/' | '%') primaryExpr)*
    ;

primaryExpr
    : '-' primaryExpr
    | functionCall
    | variableRef
    | literal
    | '(' expression ')'
    ;

functionCall
    : ID '(' exprList? ')'
    ;

variableRef
    : '#' varName ('[' expression ']')?
    | '@' varName ('[' expression ']')?
    ;

varName
    : ID | VAR_ID
    ;

literal
    : NUMBER
    | STRING
    ;

exprList
    : expression (',' expression)*
    ;

NUMBER  : [0-9]+ ('.' [0-9]+)? ;
STRING  : '\'' (~'\'' | '\\\'')* '\'' ;
ID      : [a-zA-Z_][a-zA-Z0-9_]* ;
VAR_ID  : [a-zA-Z_][a-zA-Z0-9_.]* ;
WS      : [ \t\r\n]+ -> skip ;
