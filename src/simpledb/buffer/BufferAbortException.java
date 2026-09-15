package simpledb.buffer;

//没有空闲池可用超过十秒，自动抛异常
@SuppressWarnings("serial")
public class BufferAbortException extends RuntimeException {}
