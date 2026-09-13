package simpledb.engine;

/**
 * Error raised by the DB engine.
 *
 * The course rubric asks every failure to report a type, a subject and a
 * reason, so that a caller can tell which stage rejected the statement.
 * This exception carries all three and renders them through
 * {@link #formatted()}.
 */
public class EngineException extends RuntimeException {
   private static final long serialVersionUID = 1L;

   public enum ErrorType {
      TABLE_NOT_FOUND("table does not exist"),
      TABLE_EXISTS("table already exists"),
      COLUMN_NOT_FOUND("column does not exist"),
      TYPE_MISMATCH("value type does not match column type"),
      ARITY_MISMATCH("column count does not match value count"),
      EMPTY_SCHEMA("statement has no column definition"),
      PLAN_CONVERSION("logical plan cannot be converted to a query plan"),
      STORAGE("storage engine failure");

      private final String description;

      ErrorType(String description) {
         this.description = description;
      }

      public String description() {
         return description;
      }
   }

   private final ErrorType type;
   private final String subject;
   private final String reason;

   public EngineException(ErrorType type, String subject, String reason) {
      super("EngineError[" + type + "] " + subject + ": " + reason);
      this.type = type;
      this.subject = subject;
      this.reason = reason;
   }

   public static EngineException tableNotFound(String tableName) {
      return new EngineException(ErrorType.TABLE_NOT_FOUND, "table '" + tableName + "'",
            ErrorType.TABLE_NOT_FOUND.description());
   }

   public static EngineException tableExists(String tableName) {
      return new EngineException(ErrorType.TABLE_EXISTS, "table '" + tableName + "'",
            ErrorType.TABLE_EXISTS.description());
   }

   public static EngineException columnNotFound(String tableName, String columnName) {
      return new EngineException(ErrorType.COLUMN_NOT_FOUND,
            "column '" + columnName + "' of table '" + tableName + "'",
            ErrorType.COLUMN_NOT_FOUND.description());
   }

   public static EngineException typeMismatch(String tableName, String columnName, String detail) {
      return new EngineException(ErrorType.TYPE_MISMATCH,
            "column '" + columnName + "' of table '" + tableName + "'", detail);
   }

   public static EngineException arityMismatch(String tableName, int columns, int values) {
      return new EngineException(ErrorType.ARITY_MISMATCH, "table '" + tableName + "'",
            ErrorType.ARITY_MISMATCH.description() + " (columns=" + columns
                  + ", values=" + values + ")");
   }

   public static EngineException emptySchema(String tableName) {
      return new EngineException(ErrorType.EMPTY_SCHEMA, "table '" + tableName + "'",
            ErrorType.EMPTY_SCHEMA.description());
   }

   public static EngineException planConversion(String planName) {
      return new EngineException(ErrorType.PLAN_CONVERSION, "plan '" + planName + "'",
            ErrorType.PLAN_CONVERSION.description());
   }

   public static EngineException storage(String subject, String detail) {
      return new EngineException(ErrorType.STORAGE, subject, detail);
   }

   public ErrorType getType() {
      return type;
   }

   public String getSubject() {
      return subject;
   }

   public String getReason() {
      return reason;
   }

   /**
    * Renders the error the way the CLI is expected to show it:
    * type, subject and reason on one line.
    */
   public String formatted() {
      return "EngineError[" + type + "] " + subject + " - " + reason;
   }
}
