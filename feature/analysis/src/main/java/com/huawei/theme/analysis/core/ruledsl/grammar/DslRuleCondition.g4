grammar DslRuleCondition;

condition
    : logicExpr
    ;

logicExpr
    : '(' logicExpr ')'
    | logicExpr AND logicExpr
    | logicExpr OR logicExpr
    | NOT logicExpr
    | compareExpr
    ;

compareExpr
    : valueExpr EQ valueExpr
    | valueExpr NEQ valueExpr
    | valueExpr GT valueExpr
    | valueExpr LT valueExpr
    | valueExpr GEQ valueExpr
    | valueExpr LEQ valueExpr
    | valueExpr MATCHES literal
    | valueExpr IN setLiteral
    | valueExpr NOT IN setLiteral
    ;

valueExpr
    : elementAttr
    | literal
    | NULL
    ;

elementAttr
    : ELEMENT_ATTRS_OPEN STRING ']'
    | ELEMENT_TAG_NAME
    | ELEMENT_PARENT_TAG_NAME
    ;

setLiteral
    : '[' literal (',' literal)* ']'
    ;

literal
    : NUMBER
    | STRING
    ;

NUMBER              : [0-9]+ ('.' [0-9]+)? ;
STRING              : '\'' (~'\'' | '\\\'')* '\'' ;
NULL                : 'null' ;
NOT                 : 'NOT' ;
AND                 : 'AND' ;
OR                  : 'OR' ;
IN                  : 'IN' ;
MATCHES             : 'MATCHES' ;
EQ                  : '==' ;
NEQ                 : '!=' ;
GT                  : '>' ;
LT                  : '<' ;
GEQ                 : '>=' ;
LEQ                 : '<=' ;
ELEMENT_ATTRS_OPEN  : 'element.attrs[' ;
ELEMENT_TAG_NAME    : 'element.tagName' ;
ELEMENT_PARENT_TAG_NAME : 'element.parent.tagName' ;
WS                  : [ \t\r\n]+ -> skip ;
