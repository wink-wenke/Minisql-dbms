package simpledb.storage;

/**
 * 页的唯一标识。由文件名和页号组成。
 * 保留现有 simpledb.file.BlockId 的功能，统一命名。
 */
public class PageId {
    private final String fileName;
    private final int pageNumber;

    public PageId(String fileName, int pageNumber) {
        this.fileName = fileName;
        this.pageNumber = pageNumber;
    }

    public String fileName() {
        return fileName;
    }

    public int pageNumber() {
        return pageNumber;
    }

    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PageId)) return false;
        PageId other = (PageId) obj;
        return pageNumber == other.pageNumber && fileName.equals(other.fileName);
    }

    public int hashCode() {
        return toString().hashCode();
    }

    public String toString() {
        return "[file " + fileName + ", page " + pageNumber + "]";
    }
}
