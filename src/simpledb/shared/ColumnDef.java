package simpledb.shared;

/**
 * Column definition shared by compiler and engine modules.
 */
public class ColumnDef {
   private final String name;
   private final ColumnType type;
   private final int length;

   public ColumnDef(String name, ColumnType type, int length) {
      this.name = name;
      this.type = type;
      this.length = length;
   }

   public String name() {
      return name;
   }

   public ColumnType type() {
      return type;
   }

   public int length() {
      return length;
   }

   public String toString() {
      if (type == ColumnType.VARCHAR)
         return name + " VARCHAR(" + length + ")";
      return name + " INT";
   }
}
