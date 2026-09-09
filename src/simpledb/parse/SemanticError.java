package simpledb.parse;

/**
 * 语义错误异常。
 * 在语义分析阶段检测到的错误（表不存在、列不存在、类型不匹配等）。
 */
@SuppressWarnings("serial")
public class SemanticError extends RuntimeException {
    public SemanticError(String message) {
        super(message);
    }

    /**
     * 格式化输出：SemanticError: 消息
     */
    @Override
    public String getMessage() {
        return "SemanticError: " + super.getMessage();
    }
}
