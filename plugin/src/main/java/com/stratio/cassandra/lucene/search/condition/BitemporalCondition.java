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

package com.stratio.cassandra.lucene.search.condition;

import com.stratio.cassandra.lucene.IndexException;
import com.stratio.cassandra.lucene.schema.mapping.BitemporalMapper;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.BooleanQuery;

import org.apache.lucene.search.Query;
import org.apache.lucene.spatial.prefix.NumberRangePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.DateRangePrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialOperation;

import static com.stratio.cassandra.lucene.schema.mapping.BitemporalMapper.BitemporalDateTime;
import static org.apache.lucene.search.BooleanClause.Occur.MUST;
import static org.apache.lucene.search.BooleanClause.Occur.SHOULD;
import static org.apache.lucene.search.NumericRangeQuery.newLongRange;
import static org.apache.lucene.search.NumericRangeQuery.newIntRange;

import java.util.Objects;

/**
 * A {@link Condition} implementation that matches bi-temporal (four) fields within two range of values.
 *
 * @author Eduardo Alonso {@literal <eduardoalonso@stratio.com>}
 */
public class BitemporalCondition extends SingleMapperCondition<BitemporalMapper> {

    /** The default operation. */
    public static final String DEFAULT_OPERATION = "intersects";

    /** The default from value for vtFrom and ttFrom. */
    public static final Long DEFAULT_FROM = 0L;

    /** The default to value for vtTo and ttTo. */
    public static final Long DEFAULT_TO = Long.MAX_VALUE;

    /** The Valid Time Start. */
    public final Object vtFrom;

    /** The Valid Time End. */
    public final Object vtTo;

    /** The Transaction Time Start. */
    public final Object ttFrom;

    /** The Transaction Time End. */
    public final Object ttTo;

    /**
     * Constructs a query selecting all fields that intersects with valid time and transaction time ranges including
     * limits.
     *
     * @param boost     The boost for this query clause. Documents matching this clause will (in addition to the normal
     *                  weightings) have their score multiplied by {@code boost}.
     * @param field     The name of the field to be matched.
     * @param vtFrom    The Valid Time Start.
     * @param vtTo      The Valid Time End.
     * @param ttFrom    The Transaction Time Start.
     * @param ttTo      The Transaction Time End.
     *
     */
    public BitemporalCondition(Float boost,
                               String field,
                               Object vtFrom,
                               Object vtTo,
                               Object ttFrom,
                               Object ttTo) {
        super(boost, field, BitemporalMapper.class);
        this.vtFrom = vtFrom;
        this.vtTo = vtTo;
        this.ttFrom = ttFrom;
        this.ttTo = ttTo;
    }

    private static boolean isNow(Long time) {
        return (Objects.equals(time, BitemporalDateTime.MAX.toDate().getTime()));
    }
    private static boolean isMin(Long time) {
        return (Objects.equals(time, BitemporalDateTime.MIN.toDate().getTime()));
    }
    private static boolean isMax(Long time) {
        return isNow(time);
    }
    @Override
    public Query query(BitemporalMapper mapper, Analyzer analyzer) {

        Long vt_from = this.vtFrom == null ?
                                     new BitemporalDateTime(DEFAULT_FROM).toDate().getTime() :
                                     mapper.parseBitemporalDate(this.vtFrom).toDate().getTime();
        Long vt_to = this.vtTo == null ?
                                   new BitemporalDateTime(DEFAULT_TO).toDate().getTime() :
                                   mapper.parseBitemporalDate(this.vtTo).toDate().getTime();
        Long tt_from = this.ttFrom == null ?
                                     new BitemporalDateTime(DEFAULT_FROM).toDate().getTime() :
                                     mapper.parseBitemporalDate(this.ttFrom).toDate().getTime();
        Long tt_to = this.ttTo == null ?
                                   new BitemporalDateTime(DEFAULT_TO).toDate().getTime() :
                                   mapper.parseBitemporalDate(this.ttTo).toDate().getTime();

        Long MIN=BitemporalDateTime.MIN.toDate().getTime();
        Long MAX=BitemporalDateTime.MAX.toDate().getTime();

        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        if (!isNow(tt_from) && (tt_to.compareTo(vt_from) >= 0)) {
            //R1,R2,R3,R4
            Query vQueryT1 = newLongRange(field + ".T1.vtFrom", MIN, vt_to, true, true);
            Query tQueryT1 = newLongRange(field + ".T1.ttFrom", MIN, tt_to, true, true);

            BooleanQuery.Builder t1Query = new BooleanQuery.Builder();
            t1Query.add(vQueryT1, MUST);
            t1Query.add(tQueryT1, MUST);
            builder.add(t1Query.build(), SHOULD);

            /***********************************/

            Query vQueryT2_1=newLongRange(field + ".T2.vtFrom", vt_from, vt_to, true, true);

            Query vQueryT2_2=newLongRange(field + ".T2.vtTo", vt_from, vt_to, true, true);

            BooleanQuery.Builder vQueryT2_3Builder= new BooleanQuery.Builder();

            vQueryT2_3Builder.add(newLongRange(field + ".T2.vtFrom", MIN, vt_from,true, true),MUST);
            vQueryT2_3Builder.add(newLongRange(field + ".T2.vtTo", vt_to,MAX, true,
                    true),MUST);

            BooleanQuery.Builder vQueryT2Builder = new BooleanQuery.Builder();
            vQueryT2Builder.add(vQueryT2_1,SHOULD);
            vQueryT2Builder.add(vQueryT2_2,SHOULD);
            vQueryT2Builder.add(vQueryT2_3Builder.build(),SHOULD);

            Query tQueryT2 = newLongRange(field + ".T2.ttFrom", MIN, tt_to, true, true);

            BooleanQuery.Builder t2Query = new BooleanQuery.Builder();
            t2Query.add(vQueryT2Builder.build(), MUST);
            t2Query.add(tQueryT2, MUST);
            builder.add(t2Query.build(), SHOULD);

            /***********************************/
            Query vQueryT3 = newLongRange(field + ".T3.vtFrom", MIN, vt_to, true, true);
            Query tQueryT3_1=newLongRange(field + ".T3.ttFrom", Math.max(tt_from,vt_from), tt_to, true, true);
            Query tQueryT3_2=newLongRange(field + ".T3.ttTo", Math.max(tt_from, vt_from), tt_to, true, true);

            BooleanQuery.Builder tQueryT3_3Builder= new BooleanQuery.Builder();

            tQueryT3_3Builder.add(newLongRange(field + ".T3.ttFrom", MIN, Math.max(tt_from, vt_from), true, true),MUST);
            tQueryT3_3Builder.add(newLongRange(field + ".T3.ttTo", tt_to,MAX, true, true),MUST);


            BooleanQuery.Builder tQueryT3Builder = new BooleanQuery.Builder();
            tQueryT3Builder.add(tQueryT3_1,SHOULD);
            tQueryT3Builder.add(tQueryT3_2,SHOULD);
            tQueryT3Builder.add(tQueryT3_3Builder.build(),SHOULD);


            BooleanQuery.Builder t3Query = new BooleanQuery.Builder();
            t3Query.add(vQueryT3, MUST);
            t3Query.add(tQueryT3Builder.build(), MUST);
            builder.add(t3Query.build(), SHOULD);

            /***********************************/
            Query vQueryT4_1=newLongRange(field + ".T4.vtFrom", vt_from, vt_to , true, true);
            Query vQueryT4_2=newLongRange(field + ".T4.vtTo", vt_from, vt_to , true, true);

            BooleanQuery.Builder vQueryT4_3Builder= new BooleanQuery.Builder();

            vQueryT4_3Builder.add(newLongRange(field + ".T4.vtFrom", MIN, vt_from, true, true),MUST);
            vQueryT4_3Builder.add(newLongRange(field + ".T4.vtTo", vt_to, MAX, true, true),MUST);


            BooleanQuery.Builder vQueryT4Builder = new BooleanQuery.Builder();
            vQueryT4Builder.add(vQueryT4_1,SHOULD);
            vQueryT4Builder.add(vQueryT4_2,SHOULD);
            vQueryT4Builder.add(vQueryT4_3Builder.build(),SHOULD);


            Query tQueryT4_1=newLongRange(field + ".T4.ttFrom", tt_from, tt_to, true, true);
            Query tQueryT4_2=newLongRange(field + ".T4.ttTo", tt_from, tt_to, true, true);

            BooleanQuery.Builder tQueryT4_3Builder= new BooleanQuery.Builder();

            tQueryT4_3Builder.add(newLongRange(field + ".T4.ttFrom", MIN, tt_from, true, true),MUST);
            tQueryT4_3Builder.add(newLongRange(field + ".T4.ttTo", tt_to, MAX, true, true),MUST);


            BooleanQuery.Builder tQueryT4Builder = new BooleanQuery.Builder();
            tQueryT4Builder.add(tQueryT4_1,SHOULD);
            tQueryT4Builder.add(tQueryT4_2,SHOULD);
            tQueryT4Builder.add(tQueryT4_3Builder.build(),SHOULD);


            BooleanQuery.Builder t4Query = new BooleanQuery.Builder();
            t4Query.add(vQueryT4Builder.build(), MUST);
            t4Query.add(tQueryT4Builder.build(), MUST);
            builder.add(t4Query.build(), SHOULD);

        } else if ((!isNow(tt_from)) && (tt_to.compareTo(vt_from) < 0)) {
            //R2,R4
            Query vQueryT2_1=newLongRange(field + ".T2.vtFrom", vt_from, vt_to, true, true);

            Query vQueryT2_2=newLongRange(field + ".T2.vtTo", vt_from, vt_to, true, true);

            BooleanQuery.Builder vQueryT2_3Builder= new BooleanQuery.Builder();

            vQueryT2_3Builder.add(newLongRange(field + ".T2.vtFrom", MIN, vt_from, true, true),MUST);
            vQueryT2_3Builder.add(newLongRange(field + ".T2.vtTo", vt_to, MAX, true, true),MUST);


            BooleanQuery.Builder vQueryT2Builder = new BooleanQuery.Builder();
            vQueryT2Builder.add(vQueryT2_1,SHOULD);
            vQueryT2Builder.add(vQueryT2_2,SHOULD);
            vQueryT2Builder.add(vQueryT2_3Builder.build(),SHOULD);

            Query tQueryT2 = newLongRange(field + ".T2.ttFrom", MIN, tt_to, true, true);

            BooleanQuery.Builder t2Query = new BooleanQuery.Builder();
            t2Query.add(vQueryT2Builder.build(), MUST);
            t2Query.add(tQueryT2, MUST);
            builder.add(t2Query.build(), SHOULD);

            Query vQueryT4_1=newLongRange(field + ".T4.vtFrom", vt_from, vt_to, true, true);

            Query vQueryT4_2=newLongRange(field + ".T4.vtTo", vt_from, vt_to, true, true);

            BooleanQuery.Builder vQueryT4_3Builder= new BooleanQuery.Builder();

            vQueryT4_3Builder.add(newLongRange(field + ".T4.vtFrom", MIN, vt_from,true, true),MUST);
            vQueryT4_3Builder.add(newLongRange(field + ".T4.vtTo", vt_to, MAX, true, true),MUST);


            BooleanQuery.Builder vQueryT4Builder = new BooleanQuery.Builder();
            vQueryT4Builder.add(vQueryT4_1,SHOULD);
            vQueryT4Builder.add(vQueryT4_2,SHOULD);
            vQueryT4Builder.add(vQueryT4_3Builder.build(),SHOULD);

            Query tQueryT4_1=newLongRange(field + ".T4.ttFrom", tt_from, tt_to, true, true);

            Query tQueryT4_2=newLongRange(field + ".T4.ttTo", tt_from, tt_to, true, true);

            BooleanQuery.Builder tQueryT4_3Builder= new BooleanQuery.Builder();

            tQueryT4_3Builder.add(newLongRange(field + ".T4.ttFrom", MIN, tt_from, true, true),MUST);
            tQueryT4_3Builder.add(newLongRange(field + ".T4.ttTo", tt_to, MAX, true, true),MUST);


            BooleanQuery.Builder tQueryT4Builder = new BooleanQuery.Builder();
            tQueryT4Builder.add(tQueryT4_1,SHOULD);
            tQueryT4Builder.add(tQueryT4_2,SHOULD);
            tQueryT4Builder.add(tQueryT4_3Builder.build(),SHOULD);

            BooleanQuery.Builder t4Query = new BooleanQuery.Builder();
            t4Query.add(vQueryT4Builder.build(), MUST);
            t4Query.add(tQueryT4Builder.build(), MUST);
            builder.add(t4Query.build(), SHOULD);

        } else if (isNow(tt_from)) {
            if (((!isMin(vt_from)) || (!isMax(vt_to))) && (tt_to.compareTo(vt_from) >= 0)) {
                //R1,R2
                Query vQueryT1 = newLongRange(field + ".T1.vtFrom", MIN, vt_to, true, true);
                Query tQueryT1 = newLongRange(field + ".T1.ttFrom", MIN, tt_to, true, true);

                BooleanQuery.Builder t1Query = new BooleanQuery.Builder();
                t1Query.add(vQueryT1, MUST);
                t1Query.add(tQueryT1, MUST);
                builder.add(t1Query.build(), SHOULD);

                Query vQueryT2_1=newLongRange(field + ".T2.vtFrom", vt_from, vt_to, true, true);

                Query vQueryT2_2=newLongRange(field + ".T2.vtTo", vt_from, vt_to, true, true);

                BooleanQuery.Builder vQueryT2_3Builder= new BooleanQuery.Builder();

                vQueryT2_3Builder.add(newLongRange(field + ".T2.vtFrom", MIN, vt_from, true, true),MUST);
                vQueryT2_3Builder.add(newLongRange(field + ".T2.vtTo", vt_to, MAX, true, true),MUST);

                BooleanQuery.Builder vQueryT2Builder = new BooleanQuery.Builder();
                vQueryT2Builder.add(vQueryT2_1,SHOULD);
                vQueryT2Builder.add(vQueryT2_2,SHOULD);
                vQueryT2Builder.add(vQueryT2_3Builder.build(),SHOULD);

                Query tQueryT2 = newLongRange(field + ".T2.ttFrom", MIN, tt_to, true, true);

                BooleanQuery.Builder t2Query = new BooleanQuery.Builder();
                t2Query.add(vQueryT2Builder.build(), MUST);
                t2Query.add(tQueryT2, MUST);
                builder.add(t2Query.build(), SHOULD);
            } else if (((!isMin(vt_from)) || (!isMax(vt_to))) && (tt_to.compareTo(vt_from) < 0)) {
                //R2
                Query vQueryT2_1=newLongRange(field + ".T2.vtFrom", vt_from, vt_to, true, true);
                Query vQueryT2_2=newLongRange(field + ".T2.vtTo", vt_from, vt_to, true, true);

                BooleanQuery.Builder vQueryT2_3Builder= new BooleanQuery.Builder();

                vQueryT2_3Builder.add(newLongRange(field + ".T2.vtFrom", MIN, vt_from, true, true),MUST);
                vQueryT2_3Builder.add(newLongRange(field + ".T2.vtTo", vt_to, MAX, true, true),MUST);


                BooleanQuery.Builder vQueryT2Builder = new BooleanQuery.Builder();
                vQueryT2Builder.add(vQueryT2_1,SHOULD);
                vQueryT2Builder.add(vQueryT2_2,SHOULD);
                vQueryT2Builder.add(vQueryT2_3Builder.build(),SHOULD);

                Query tQueryT2 = newLongRange(field + ".T2.ttFrom", MIN, tt_to, true, true);
                BooleanQuery.Builder t2Query = new BooleanQuery.Builder();
                t2Query.add(vQueryT2Builder.build(), MUST);
                t2Query.add(tQueryT2, MUST);
                builder.add(t2Query.build(), SHOULD);
            } else if ((isMin(vt_from)) && (isMax(vt_to))) { // [vtFrom, vtTo]==[tmin,tmax]])
                //R1UR2
                return newIntRange(mapper.getT1UT2FieldName(), 1, 1, true, true);
            }
        }
        Query query = builder.build();
        query.setBoost(boost);
        return query;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return toStringHelper(this).add("vtFrom", vtFrom)
                                   .add("vtTo", vtTo)
                                   .add("ttFrom", ttFrom)
                                   .add("ttTo", ttTo)
                                   .toString();
    }
}
