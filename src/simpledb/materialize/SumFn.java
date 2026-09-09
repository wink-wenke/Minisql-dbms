package simpledb.materialize;

import simpledb.query.*;

/**
 * The <i>sum</i> aggregation function.
 */
public class SumFn implements AggregationFn {
    private String fldname;
    private int sum;

    public SumFn(String fldname) {
        this.fldname = fldname;
    }

    public void processFirst(Scan s) {
        sum = s.getVal(fldname).asInt();
    }

    public void processNext(Scan s) {
        sum += s.getVal(fldname).asInt();
    }

    public String fieldName() {
        return "sumof" + fldname;
    }

    public Constant value() {
        return new Constant(sum);
    }
}
