package simpledb.buffer;


@SuppressWarnings("serial")

//pin等待超过十秒仍无可用缓冲区抛出异常
public class BufferAbortException extends RuntimeException {}
