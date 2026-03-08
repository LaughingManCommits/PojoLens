package laughing.man.commits.benchmark;

import java.util.Date;

public class BenchmarkFoo {

    private String stringField;
    private Date dateField;
    private int integerField;

    public BenchmarkFoo() {
    }

    public BenchmarkFoo(String stringField, Date dateField, int integerField) {
        this.stringField = stringField;
        this.dateField = dateField;
        this.integerField = integerField;
    }

    public String getStringField() {
        return stringField;
    }

    public Date getDateField() {
        return dateField;
    }

    public int getIntegerField() {
        return integerField;
    }
}

