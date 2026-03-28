package laughing.man.commits.domain;

import java.util.Date;

public class Foo {

    private String stringField;
    private Date dateField;
    private int integerField;

    public Foo() {
    }

    public Foo(String stringField, Date dateField, int integerField) {
        this.stringField = stringField;
        this.dateField = dateField;
        this.integerField = integerField;
    }

    /**
     * @return the str
     */
    public String getStringField() {
        return stringField;
    }

    /**
     * @param stringField the str to set
     */
    public void setStringField(String stringField) {
        this.stringField = stringField;
    }

    /**
     * @return the date
     */
    public Date getDateField() {
        return dateField;
    }

    /**
     * @param dateField the date to set
     */
    public void setDateField(Date dateField) {
        this.dateField = dateField;
    }

    /**
     * @return the i
     */
    public int getIntegerField() {
        return integerField;
    }

    /**
     * @param integerField the i to set
     */
    public void setIntegerField(int integerField) {
        this.integerField = integerField;
    }

    @Override
    public String toString() {
        return "Foo{" + "str=" + stringField + ", date=" + dateField + ", i=" + integerField + '}';
    }

}



