package simpledb.parse;

/**
 * 词法单元类型枚举。
 * 涵盖关键字、标识符、常量、运算符、分隔符以及特殊符号。
 */
public enum TokenType {
    // 关键字
    SELECT, FROM, WHERE, AND, OR, NOT,
    INSERT, INTO, VALUES,
    DELETE, UPDATE, SET,
    CREATE, TABLE, VIEW, AS, INDEX, ON, ORDER, BY,
    INT, VARCHAR,
    NULL,

    // 字面量
    INT_CONST,      // 整数常量，如 42
    STRING_CONST,   // 字符串常量，如 'Alice'
    STAR,           // *

    // 标识符
    IDENTIFIER,     // 表名、列名等

    // 比较运算符
    EQUALS,         // =
    NOT_EQUALS,     // !=
    LESS,           // <
    LESS_EQUALS,    // <=
    GREATER,        // >
    GREATER_EQUALS, // >=

    // 算术运算符
    PLUS,           // +
    MINUS,          // -
    DIVIDE,         // /

    // 分隔符
    LPAREN,         // (
    RPAREN,         // )
    COMMA,          // ,
    SEMICOLON,      // ;

    // 特殊
    EOF,            // 输入结束
    ERROR           // 非法 token
}
