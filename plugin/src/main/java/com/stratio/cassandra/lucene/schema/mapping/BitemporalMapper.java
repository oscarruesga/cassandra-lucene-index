/*
 * Licensed to STRATIO (C) under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.  The STRATIO (C) licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.stratio.cassandra.lucene.schema.mapping;

import com.google.common.base.Objects;
import com.spatial4j.core.context.SpatialContext;
import com.spatial4j.core.shape.Rectangle;
import com.spatial4j.core.shape.Shape;
import com.stratio.cassandra.lucene.IndexException;
import com.stratio.cassandra.lucene.schema.column.Column;
import com.stratio.cassandra.lucene.schema.column.Columns;
import com.stratio.cassandra.lucene.util.DateParser;
import org.apache.cassandra.db.marshal.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SortField;
import org.apache.lucene.spatial.prefix.NumberRangePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.DateRangePrefixTree;
import org.apache.lucene.spatial.prefix.tree.NumberRangePrefixTree.NRShape;
import org.apache.lucene.spatial.prefix.tree.NumberRangePrefixTree.UnitNRShape;
import org.apache.lucene.spatial.prefix.tree.QuadPrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;

import java.util.Arrays;
import java.util.Date;

/**
 * A {@link Mapper} to map bitemporal DateRanges.
 *
 * @author Eduardo Alonso {@literal <eduardoalonso@stratio.com>}
 */
public class BitemporalMapper extends Mapper {

    /** The {@link DateParser} pattern. */
    public final String pattern;

    /** The {@link DateParser}. */
    private final DateParser dateParser;

    /** The name of the column containing the valid time start. **/
    public final String vtFrom;

    /** The name of the column containing the valid time stop. **/
    public final String vtTo;

    /** The name of the column containing the transaction time start. **/
    public final String ttFrom;

    /** The name of the column containing the transaction time stop. **/
    public final String ttTo;

    /** The NOW Value. **/
    public final Long nowValue;

    private static final String T1UT2_FIELD_SUFFIX = ".T1UT2";




    /**
     * Builds a new {@link BitemporalMapper}.
     *
     * @param field    the name of the field.
     * @param vtFrom   The column name containing the valid time start.
     * @param vtTo     The column name containing the valid time stop.
     * @param ttFrom   The column name containing the transaction time start.
     * @param ttTo     The column name containing the transaction time stop.
     * @param pattern  The date format pattern to be used.
     * @param nowValue The value representing now.
     */
    public BitemporalMapper(String field,
                            String vtFrom,
                            String vtTo,
                            String ttFrom,
                            String ttTo,
                            String pattern,
                            Object nowValue) {

        super(field,
              true,
              false,
              null,
              Arrays.asList(vtFrom, vtTo, ttFrom, ttTo),
              AsciiType.instance,
              UTF8Type.instance,
              Int32Type.instance,
              LongType.instance,
              IntegerType.instance,
              FloatType.instance,
              DoubleType.instance,
              DecimalType.instance,
              TimestampType.instance);

        if (StringUtils.isBlank(vtFrom)) {
            throw new IndexException("vt_from column name is required");
        }

        if (StringUtils.isBlank(vtTo)) {
            throw new IndexException("vt_to column name is required");
        }

        if (StringUtils.isBlank(ttFrom)) {
            throw new IndexException("tt_from column name is required");
        }

        if (StringUtils.isBlank(ttTo)) {
            throw new IndexException("tt_to column name is required");
        }

        this.pattern = pattern == null ? DateParser.DEFAULT_PATTERN : pattern;
        this.dateParser = new DateParser(this.pattern);

        this.vtFrom = vtFrom;
        this.vtTo = vtTo;
        this.ttFrom = ttFrom;
        this.ttTo = ttTo;

        // Validate pattern

        // ttTo=now vtTo=now 2 DateRangePrefixTree

        this.nowValue = (nowValue == null) ? Long.MAX_VALUE : dateParser.parse(nowValue).getTime();
    }

    public String getT1UT2FieldName() {
        return field + T1UT2_FIELD_SUFFIX;

    }

    /**
     * Build a {@link NRShape}.
     *
     * @param tree  The {@link DateRangePrefixTree} tree.
     * @param start The {@link BitemporalDateTime} start of the range.
     * @param stop  The {@link BitemporalDateTime} stop of the range.
     * @return A built {@link NRShape}.
     */
    public NRShape makeShape(DateRangePrefixTree tree, BitemporalDateTime start, BitemporalDateTime stop) {
        UnitNRShape startShape = tree.toUnitShape(start.toDate());
        UnitNRShape stopShape = tree.toUnitShape(stop.toDate());
        return tree.toRangeShape(startShape, stopShape);
    }

    /** {@inheritDoc} */
    @Override
    public void addFields(Document document, Columns columns) {

        BitemporalDateTime vtFrom = readBitemporalDate(columns, this.vtFrom);
        BitemporalDateTime vtTo = readBitemporalDate(columns, this.vtTo);
        BitemporalDateTime ttFrom = readBitemporalDate(columns, this.ttFrom);
        BitemporalDateTime ttTo = readBitemporalDate(columns, this.ttTo);

        if (vtFrom == null && vtTo == null && ttFrom == null && ttTo == null) {
            return;
        }

        validate(vtFrom, vtTo, ttFrom, ttTo);

        if (ttTo.isNow() && vtTo.isNow()) { // T1

            document.add(new LongField(this.field+".T1.vtFrom", vtFrom.toDate().getTime(), STORE));
            document.add(new LongField(this.field+".T1.ttFrom", ttFrom.toDate().getTime(), STORE));
            document.add(new IntField(getT1UT2FieldName(), 1, STORE));

        } else if (ttTo.isNow()) { // T2

            document.add(new LongField(this.field+".T2.vtFrom", vtFrom.toDate().getTime(), STORE));
            document.add(new LongField(this.field+".T2.vtTo", vtTo.toDate().getTime(), STORE));
            document.add(new LongField(this.field+".T2.ttFrom", ttFrom.toDate().getTime(), STORE));
            document.add(new IntField(getT1UT2FieldName(), 1, STORE));

        } else if (vtTo.isNow()) {// T3

            document.add(new LongField(this.field+".T3.vtFrom", vtFrom.toDate().getTime(), STORE));
            document.add(new LongField(this.field+".T3.ttFrom", ttFrom.toDate().getTime(), STORE));
            document.add(new LongField(this.field+".T3.ttTo", ttTo.toDate().getTime(), STORE));
            document.add(new IntField(getT1UT2FieldName(), 0, STORE));

        } else { // T4

            document.add(new LongField(this.field+".T4.vtFrom", vtFrom.toDate().getTime(), STORE));
            document.add(new LongField(this.field+".T4.vtTo", vtTo.toDate().getTime(), STORE));
            document.add(new LongField(this.field+".T4.ttFrom", ttFrom.toDate().getTime(), STORE));
            document.add(new LongField(this.field+".T4.ttTo", ttTo.toDate().getTime(), STORE));
            document.add(new IntField(getT1UT2FieldName(), 0, STORE));
        }
    }


    private void validate(BitemporalDateTime vtFrom, BitemporalDateTime vtTo, BitemporalDateTime ttFrom,
                          BitemporalDateTime ttTo) {
        if (vtFrom == null) {
            throw new IndexException("vt_from column required");
        }
        if (vtTo == null) {
            throw new IndexException("vt_to column required");
        }
        if (ttFrom == null) {
            throw new IndexException("tt_from column required");
        }
        if (ttTo == null) {
            throw new IndexException("tt_to column required");
        }
        if (vtFrom.after(vtTo)) {
            throw new IndexException("vt_from:'%s' is after vt_to:'%s'",
                                     vtTo.toString(dateParser),
                                     vtFrom.toString(dateParser));
        }
        if (ttFrom.after(ttTo)) {
            throw new IndexException("tt_from:'%s' is after tt_to:'%s'",
                                     ttTo.toString(dateParser),
                                     ttFrom.toString(dateParser));
        }
    }

    /**
     * returns a {@link BitemporalDateTime} read from columns
     *
     * @param columns   the {@link Columns} where it is the data
     * @param fieldName the filed Name to read from {@link Columns}
     * @return a {@link BitemporalDateTime} read from columns
     */
    BitemporalDateTime readBitemporalDate(Columns columns, String fieldName) {
        Column<?> column = columns.getColumnsByName(fieldName).getFirst();
        if (column == null) {
            return null;
        }
        return parseBitemporalDate(column.getComposedValue());
    }

    private BitemporalDateTime checkIfNow(Long in) {
        if (in > nowValue) {
            throw new IndexException("BitemporalDateTime value '%s' exceeds Max Value: '%s'", in, nowValue);
        } else if (in < nowValue) {
            return new BitemporalDateTime(in);
        } else {
            return new BitemporalDateTime(Long.MAX_VALUE);
        }
    }

    /**
     * Parses an {@link Object} into a {@link BitemporalDateTime}. It parses {@link Long} and {@link String} format
     * values based in pattern.
     *
     * @param value The object to be parsed.
     * @return a parsed {@link BitemporalDateTime} from an {@link Object}. it parses {@link Long} and {@link String}
     * format values based in pattern.
     */
    public BitemporalDateTime parseBitemporalDate(Object value) {
        Date opt = dateParser.parse(value);
        if (opt != null) {
            return checkIfNow(opt.getTime());
        } else {
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public SortField sortField(String name, boolean reverse) {
        throw new IndexException(String.format("Bitemporal mapper '%s' does not support sorting", name));
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                      .add("field", field)
                      .add("vtFrom", vtFrom)
                      .add("vtTo", vtTo)
                      .add("ttFrom", ttFrom)
                      .add("ttTo", ttTo)
                      .add("pattern", pattern)
                      .add("nowValue", nowValue)
                      .toString();
    }

    public static class BitemporalDateTime implements Comparable<BitemporalDateTime> {

        public static final BitemporalDateTime MAX = new BitemporalDateTime(Long.MAX_VALUE);
        public static final BitemporalDateTime MIN = new BitemporalDateTime(0L);

        private final Long timestamp;
        private final Date date;

        /**
         * @param date A date.
         */
        public BitemporalDateTime(Date date) {
            timestamp = date.getTime();
            this.date = date;
        }

        /**
         * @param timestamp A timestamp.
         */
        public BitemporalDateTime(Long timestamp) {
            if (timestamp < 0L) {
                throw new IndexException("Cannot build a BitemporalDateTime with a negative unix time");
            }
            this.timestamp = timestamp;
            date = new Date(timestamp);
        }

        public boolean isNow() {
            return timestamp.equals(MAX.timestamp);
        }

        public boolean isMax() {
            return timestamp.equals(MAX.timestamp);
        }

        public boolean isMin() {
            return timestamp.equals(0L);
        }

        public Date toDate() {
            return date;
        }

        public boolean after(BitemporalDateTime time) {
            return date.after(time.date);
        }

        @Override
        public int compareTo(BitemporalDateTime other) {
            return timestamp.compareTo(other.timestamp);
        }

        public static BitemporalDateTime max(BitemporalDateTime bt1, BitemporalDateTime bt2) {
            int result = bt1.compareTo(bt2);
            if (result <= 0) {
                return bt2;
            } else {
                return bt1;
            }
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return timestamp.toString();
        }

        public String toString(DateParser dateParser) {
            return dateParser.toString(date);
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            BitemporalDateTime that = (BitemporalDateTime) o;
            return timestamp.equals(that.timestamp);
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            return timestamp.hashCode();
        }
    }
}

