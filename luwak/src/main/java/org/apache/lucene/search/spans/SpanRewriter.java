package org.apache.lucene.search.spans;
/*
 *   Copyright (c) 2015 Lemur Consulting Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

import java.lang.reflect.Field;
import java.util.ArrayList;

import org.apache.lucene.index.PrefixCodedTerms;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.TermsQuery;
import org.apache.lucene.search.*;
import org.apache.lucene.util.BytesRef;

public class SpanRewriter {

    public static final SpanRewriter INSTANCE = new SpanRewriter();

    public Query rewrite(Query in) {
        if (in instanceof SpanQuery)
            return in;
        if (in instanceof TermQuery)
            return rewriteTermQuery((TermQuery)in);
        if (in instanceof BooleanQuery)
            return rewriteBoolean((BooleanQuery) in);
        if (in instanceof MultiTermQuery)
            return rewriteMultiTermQuery((MultiTermQuery)in);
        if (in instanceof DisjunctionMaxQuery)
            return rewriteDisjunctionMaxQuery((DisjunctionMaxQuery) in);
        if (in instanceof TermsQuery)
            return rewriteTermsQuery((TermsQuery) in);

        return rewriteUnknown(in);
    }

    protected Query rewriteTermQuery(TermQuery tq) {
        return new SpanTermQuery(tq.getTerm());
    }

    protected Query rewriteBoolean(BooleanQuery bq) {
        BooleanQuery.Builder newbq = new BooleanQuery.Builder();
        for (BooleanClause clause : bq) {
            newbq.add(rewrite(clause.getQuery()), clause.getOccur());
        }
        return new ForceNoBulkScoringQuery(newbq.build());
    }

    protected Query rewriteMultiTermQuery(MultiTermQuery mtq) {
        return new SpanMultiTermQueryWrapper<>(mtq);
    }

    protected Query rewriteDisjunctionMaxQuery(DisjunctionMaxQuery disjunctionMaxQuery) {
        ArrayList<Query> subQueries = new ArrayList<>();
        for (Query subQuery : disjunctionMaxQuery) {
            subQueries.add(rewrite(subQuery));
        }
        return new DisjunctionMaxQuery(subQueries, disjunctionMaxQuery.getTieBreakerMultiplier());
    }

    protected Query rewriteTermsQuery(TermsQuery query) {

        try {
            Field termsField = TermsQuery.class.getDeclaredField("termData");
            termsField.setAccessible(true);
            PrefixCodedTerms terms = (PrefixCodedTerms) termsField.get(query);
            SpanTermQuery[] spanterms = new SpanTermQuery[(int)terms.size()];
            PrefixCodedTerms.TermIterator it = terms.iterator();
            for (int i = 0; i < terms.size(); i++) {
                BytesRef term = BytesRef.deepCopyOf(it.next());
                spanterms[i] = new SpanTermQuery(new Term(it.field(), term));
            }
            return new SpanOrQuery(spanterms);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

    }

    protected Query rewriteUnknown(Query query) {
        throw new IllegalArgumentException("Don't know how to rewrite " + query.getClass());
    }

}
