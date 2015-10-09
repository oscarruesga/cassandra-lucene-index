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

package com.stratio.cassandra.lucene.search.condition.builder;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import com.stratio.cassandra.lucene.search.condition.BitemporalDateCondition;

/**
 * {@link ConditionBuilder} for building a new {@link BitemporalDateCondition}.
 *
 * @author Eduardo Alonso {@literal <eduardoalonso@stratio.com>}
 */

public class BitemporalDateConditionBuilder
        extends ConditionBuilder<BitemporalDateCondition, BitemporalDateConditionBuilder> {

    /** The name of the filed to be matched. */
    @JsonProperty("field")
    private final String field;

    /** The valid time start. */
    @JsonProperty("vt_from")
    private Object vtFrom;

    /** The valid time end. */
    @JsonProperty("vt_to")
    private Object vtTo;

    /** The transaction time start. */
    @JsonProperty("tt_from")
    private Object ttFrom;

    /** The transaction time end. */
    @JsonProperty("tt_to")
    private Object ttTo;

    /**
     * Returns a new {@link BitemporalDateConditionBuilder} with the specified field reference point.
     *
     * @param field The name of the field to be matched.
     */
    @JsonCreator
    public BitemporalDateConditionBuilder(@JsonProperty("field") String field) {
        this.field = field;
    }

    /**
     * Sets the valid time start.
     *
     * @param vtFrom The valid time start to be set.
     * @return This.
     */
    public BitemporalDateConditionBuilder vtFrom(Object vtFrom) {
        this.vtFrom = vtFrom;
        return this;
    }

    /**
     * Sets the valid time end.
     *
     * @param vtTo The valid time end to be set.
     * @return This.
     */
    public BitemporalDateConditionBuilder vtTo(Object vtTo) {
        this.vtTo = vtTo;
        return this;
    }

    /**
     * Sets the transaction time start.
     *
     * @param ttFrom The transaction time start to be set.
     * @return This.
     */
    public BitemporalDateConditionBuilder ttFrom(Object ttFrom) {
        this.ttFrom = ttFrom;
        return this;
    }

    /**
     * Sets the transaction time end.
     *
     * @param ttTo The transaction time end to be set.
     * @return This.
     */
    public BitemporalDateConditionBuilder ttTo(Object ttTo) {
        this.ttTo = ttTo;
        return this;
    }

    /**
     * Returns the {@link BitemporalDateCondition} represented by this builder.
     *
     * @return The {@link BitemporalDateCondition} represented by this builder.
     */
    @Override
    public BitemporalDateCondition build() {
        return new BitemporalDateCondition(boost, field, vtFrom, vtTo, ttFrom,
                ttTo);
    }
}
