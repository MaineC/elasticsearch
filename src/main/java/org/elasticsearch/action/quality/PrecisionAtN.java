/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.action.quality;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import org.elasticsearch.action.quality.PrecisionAtN.Precision;
import org.elasticsearch.search.SearchHit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import javax.naming.directory.SearchResult;

/**
 * Evaluate Precision at N, N being the number of search results to consider for precision calculation.
 * 
 * Documents of unkonwn quality are ignored in the precision at n computation and returned by document id.
 * */
public class PrecisionAtN implements RankedListQualityMetric<String, Precision> {
    
    /** Number of results to check against a given set of relevant results. */
    private final int n;
     
    /**
     * @param n number of top results to check against a given set of relevant results.
     * */
    public PrecisionAtN(int n) {
        this.n= n;
    }

    /** Compute precisionAtN based on provided relevant document IDs.
     * @param relevantDocIds set of docIds considered relevant for some query.
     * @param hits hits as returned for some query
     * @return precision at n for above {@link SearchResult} list.
     **/
    public Precision evaluate(Map<String, String> ratedDocIds, SearchHit[] hits) {
        Collection<String> relevantDocIds = Maps.filterEntries(ratedDocIds, new Predicate<Entry<String, String>> () {
            @Override
            public boolean apply(Entry<String, String> input) {
                return Rating.RELEVANT.equals(Rating.valueOf(input.getValue()));
            }
        }).keySet();
        
        Collection<String> irrelevantDocIds = Maps.filterEntries(ratedDocIds, new Predicate<Entry<String, String>> () {
            @Override
            public boolean apply(Entry<String, String> input) {
                return Rating.IRRELEVANT.equals(Rating.valueOf(input.getValue()));
            }
        }).keySet();

        int good = 0;
        int bad = 0;
        Collection<String> unknownDocIds = new ArrayList<String>();
        for (int i = 0; (i < n && i < hits.length); i++) {
            String id = hits[i].getId();
            if (relevantDocIds.contains(id)) {
                good++;
            } else if (irrelevantDocIds.contains(id)) {
                bad++;
            } else {
                unknownDocIds.add(id);
            }
        }

        double precision = (double) good / (good + bad);

        return new Precision(precision, unknownDocIds);
    }

    /** Used for rating documents:
     * Relevant: Documents expected for result sets returned for this search intent.
     * Irrelevant: Documents not expected or unrelated to this search intent. */
    public enum Rating {
        RELEVANT, IRRELEVANT;
    }
    
    /** Returned for each search intent and search specification combination. Summarises the document ids found that were not
     * annotated and the average precision of result sets in each particular combination based on the annotations given. 
     * 
     * TODO add support for tracking unknown docs per query intent id
     * */
    public class Precision {
        private double precision;
        
        private Collection<String> unknownDocs;

        public Precision (double precision, Collection<String> unknownDocs) {
            this.precision = precision;
            this.unknownDocs = unknownDocs;
        }
        
        public Collection<String> getUnknownDocs() {
            return unknownDocs;
        }

        public double getPrecision() {
            return precision;
        }
    }
}
