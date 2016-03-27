package com.yotouch.core.entity.fv;

import com.yotouch.core.entity.MetaField;

public class IntFieldValueImpl extends AbstractFieldValue<Integer> implements FieldValue<Integer> {

    public IntFieldValueImpl(MetaField<Integer> mf, Object value) {
        super(mf, value);
    }

    @Override
    protected Integer parseValue(Object v) {
        if (v == null) {
            return null;
        } else if (v instanceof Integer || v instanceof Long) {
            return (Integer) v;
        } else if (v instanceof Double || v instanceof Float) {
            return Math.round((float) v);
        } else {
            return Integer.parseInt(v.toString());
        }
    }

}