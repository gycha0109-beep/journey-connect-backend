package com.jc.intelligence.contract.v1.search.serialization;

import com.jc.intelligence.contract.support.ContractJsonWireV1;
import com.jc.intelligence.contract.support.StrictContractJsonParserV1;
import com.jc.intelligence.contract.v1.explanation.ExplanationAudience;
import com.jc.intelligence.contract.v1.identity.EntityRef;
import com.jc.intelligence.contract.v1.identity.IdentitySchemeId;
import com.jc.intelligence.contract.v1.identity.RunRef;
import com.jc.intelligence.contract.v1.identity.SnapshotRef;
import com.jc.intelligence.contract.v1.identity.SubjectRef;
import com.jc.intelligence.contract.v1.replay.ReplayClass;
import com.jc.intelligence.contract.v1.replay.ReplayEvidenceDescriptorV1;
import com.jc.intelligence.contract.v1.run.IntelligenceRunStatus;
import com.jc.intelligence.contract.v1.search.RetrievalSource;
import com.jc.intelligence.contract.v1.search.SearchEligibilityState;
import com.jc.intelligence.contract.v1.search.SearchEntityScope;
import com.jc.intelligence.contract.v1.search.SearchEntityType;
import com.jc.intelligence.contract.v1.search.SearchExplanationReason;
import com.jc.intelligence.contract.v1.search.SearchFailureCode;
import com.jc.intelligence.contract.v1.search.SearchFallbackCode;
import com.jc.intelligence.contract.v1.search.SearchFilterSource;
import com.jc.intelligence.contract.v1.search.SearchFilterType;
import com.jc.intelligence.contract.v1.search.SearchQueryMode;
import com.jc.intelligence.contract.v1.search.SearchSortType;
import com.jc.intelligence.contract.v1.search.SearchSurface;
import com.jc.intelligence.contract.v1.search.SearchVisibilityState;
import com.jc.intelligence.contract.v1.search.cursor.SearchCursorV1;
import com.jc.intelligence.contract.v1.search.cursor.SearchOrderingTupleV1;
import com.jc.intelligence.contract.v1.search.explanation.SearchExplanationV1;
import com.jc.intelligence.contract.v1.search.failure.SearchFailureV1;
import com.jc.intelligence.contract.v1.search.failure.SearchFallbackV1;
import com.jc.intelligence.contract.v1.search.query.SearchContextV1;
import com.jc.intelligence.contract.v1.search.query.SearchFilterV1;
import com.jc.intelligence.contract.v1.search.query.SearchPageRequestV1;
import com.jc.intelligence.contract.v1.search.query.SearchQueryV1;
import com.jc.intelligence.contract.v1.search.query.SearchRequestV1;
import com.jc.intelligence.contract.v1.search.query.SearchSortV1;
import com.jc.intelligence.contract.v1.search.retrieval.RetrievalCandidateV1;
import com.jc.intelligence.contract.v1.search.run.SearchRunV1;
import com.jc.intelligence.contract.v1.snapshot.PrivacyClass;
import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.FeatureDefinitionVersion;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SearchContractJsonCodecV1 {
    private SearchContractJsonCodecV1() { }

    public static String writeQuery(SearchQueryV1 value) { return ContractJsonWireV1.stringify(queryMap(value)); }
    public static SearchQueryV1 readQuery(String json) { return readQuery(object(json)); }

    public static String writeRequest(SearchRequestV1 value) {
        LinkedHashMap<String,Object> map=base(value.contractVersion().value());
        put(map,"requestId",value.requestId()); put(map,"correlationId",value.correlationId());
        map.put("query",queryMap(value.query())); map.put("context",contextMap(value.context()));
        map.put("filters",filterMaps(value.filters())); map.put("sort",sortMap(value.sort()));
        map.put("pageRequest",pageMap(value.pageRequest())); map.put("schemaVersion",value.schemaVersion().value());
        map.put("queryNormalizationVersion",value.queryNormalizationVersion().value());
        map.put("rankingPolicyVersion",value.rankingPolicyVersion().value());
        map.put("featureDefinitionVersion",value.featureDefinitionVersion().value());
        return ContractJsonWireV1.stringify(map);
    }
    public static SearchRequestV1 readRequest(String json) {
        Map<String,Object> m=object(json);
        return new SearchRequestV1(new ContractId(req(m,"contractVersion")),opt(m,"requestId"),opt(m,"correlationId"),
                readQuery(obj(m,"query")),readContext(obj(m,"context")),readFilters(list(m,"filters")),
                readSort(obj(m,"sort")),readPage(obj(m,"pageRequest")),new SchemaVersion(req(m,"schemaVersion")),
                new SchemaVersion(req(m,"queryNormalizationVersion")),new PolicyVersion(req(m,"rankingPolicyVersion")),
                new FeatureDefinitionVersion(req(m,"featureDefinitionVersion")));
    }

    public static String writeCursor(SearchCursorV1 v) {
        LinkedHashMap<String,Object> m=new LinkedHashMap<>();
        m.put("cursorVersion",v.cursorVersion().value()); m.put("searchRunId",v.searchRunId().value());
        m.put("resultSnapshotRef",v.resultSnapshotRef().value()); m.put("queryFingerprint",v.queryFingerprint());
        m.put("filterFingerprint",v.filterFingerprint()); m.put("sortPolicyVersion",v.sortPolicyVersion().value());
        m.put("rankingPolicyVersion",v.rankingPolicyVersion().value()); m.put("referenceTime",v.referenceTime().toString());
        m.put("nextRank",v.nextRank()); m.put("lastOrderingTuple",v.lastOrderingTuple().components());
        m.put("surface",v.surface().wireValue()); m.put("entityScope",v.entityScope().wireValue());
        put(m,"subjectBindingRef",v.subjectBindingRef()==null?null:v.subjectBindingRef().value());
        put(m,"identitySchemeId",v.subjectBindingRef()==null?null:v.subjectBindingRef().schemeId().wireValue());
        put(m,"sessionBindingRef",v.sessionBindingRef()); m.put("issuedAt",v.issuedAt().toString());
        m.put("expiresAt",v.expiresAt().toString()); m.put("checksum",v.checksum());
        return ContractJsonWireV1.stringify(m);
    }
    public static SearchCursorV1 readCursor(String json) {
        Map<String,Object> m=object(json); SubjectRef subject=subject(m,"subjectBindingRef","identitySchemeId");
        return new SearchCursorV1(new SchemaVersion(req(m,"cursorVersion")),new RunRef(req(m,"searchRunId")),
                new SnapshotRef(req(m,"resultSnapshotRef")),req(m,"queryFingerprint"),req(m,"filterFingerprint"),
                new PolicyVersion(req(m,"sortPolicyVersion")),new PolicyVersion(req(m,"rankingPolicyVersion")),
                Instant.parse(req(m,"referenceTime")),intValue(m,"nextRank"),new SearchOrderingTupleV1(strings(m,"lastOrderingTuple")),
                SearchSurface.fromWire(req(m,"surface")),SearchEntityScope.fromWire(req(m,"entityScope")),subject,
                opt(m,"sessionBindingRef"),Instant.parse(req(m,"issuedAt")),Instant.parse(req(m,"expiresAt")),req(m,"checksum"));
    }

    public static String writeRetrievalCandidate(RetrievalCandidateV1 v) {
        LinkedHashMap<String,Object> m=base(v.contractVersion().value()); m.put("entityRef",v.entityRef().value());
        m.put("entityType",v.entityType().wireValue()); m.put("sourceId",v.sourceId());
        m.put("retrievalSource",v.retrievalSource().wireValue()); put(m,"retrievalScore",v.retrievalScore());
        put(m,"sourceRank",v.sourceRank()); m.put("retrievedAt",v.retrievedAt().toString());
        m.put("sourceSnapshotRef",v.sourceSnapshotRef().value()); m.put("eligibilityState",v.eligibilityState().wireValue());
        m.put("visibilityState",v.visibilityState().wireValue()); put(m,"candidateMetadataRef",v.candidateMetadataRef());
        m.put("retrievalStrategyVersion",v.retrievalStrategyVersion().value()); return ContractJsonWireV1.stringify(m);
    }
    public static RetrievalCandidateV1 readRetrievalCandidate(String json) {
        Map<String,Object> m=object(json); return new RetrievalCandidateV1(new ContractId(req(m,"contractVersion")),
                new EntityRef(req(m,"entityRef")),SearchEntityType.fromWire(req(m,"entityType")),req(m,"sourceId"),
                RetrievalSource.fromWire(req(m,"retrievalSource")),doubleOpt(m,"retrievalScore"),integerOpt(m,"sourceRank"),
                Instant.parse(req(m,"retrievedAt")),new SnapshotRef(req(m,"sourceSnapshotRef")),
                SearchEligibilityState.fromWire(req(m,"eligibilityState")),SearchVisibilityState.fromWire(req(m,"visibilityState")),
                opt(m,"candidateMetadataRef"),new SchemaVersion(req(m,"retrievalStrategyVersion")));
    }

    public static String writeFailure(SearchFailureV1 v) {
        LinkedHashMap<String,Object> m=base(v.contractVersion().value()); put(m,"runId",v.runId()==null?null:v.runId().value());
        m.put("failureCode",v.failureCode().wireValue()); put(m,"evidenceRef",v.evidenceRef());
        m.put("retryable",v.retryable()); m.put("occurredAt",v.occurredAt().toString()); return ContractJsonWireV1.stringify(m);
    }
    public static SearchFailureV1 readFailure(String json) { Map<String,Object> m=object(json); return new SearchFailureV1(
            new ContractId(req(m,"contractVersion")),opt(m,"runId")==null?null:new RunRef(opt(m,"runId")),
            SearchFailureCode.fromWire(req(m,"failureCode")),opt(m,"evidenceRef"),bool(m,"retryable"),Instant.parse(req(m,"occurredAt"))); }

    public static String writeFallback(SearchFallbackV1 v) {
        LinkedHashMap<String,Object> m=base(v.contractVersion().value()); m.put("runId",v.runId().value());
        m.put("fallbackCode",v.fallbackCode().wireValue()); m.put("fallbackStrategyVersion",v.fallbackStrategyVersion().value());
        m.put("primaryFailureEvidenceRef",v.primaryFailureEvidenceRef()); m.put("orderingContractRef",v.orderingContractRef());
        m.put("createdAt",v.createdAt().toString()); return ContractJsonWireV1.stringify(m);
    }
    public static SearchFallbackV1 readFallback(String json) { Map<String,Object> m=object(json); return new SearchFallbackV1(
            new ContractId(req(m,"contractVersion")),new RunRef(req(m,"runId")),SearchFallbackCode.fromWire(req(m,"fallbackCode")),
            new SchemaVersion(req(m,"fallbackStrategyVersion")),req(m,"primaryFailureEvidenceRef"),req(m,"orderingContractRef"),
            Instant.parse(req(m,"createdAt"))); }

    public static String writeRun(SearchRunV1 v) {
        LinkedHashMap<String,Object> m=base(v.contractVersion().value()); m.put("runId",v.runId().value());
        m.put("status",v.status().wireValue()); put(m,"requestId",v.requestId()); put(m,"correlationId",v.correlationId());
        put(m,"subjectRef",v.subjectRef()==null?null:v.subjectRef().value());
        put(m,"identitySchemeId",v.subjectRef()==null?null:v.subjectRef().schemeId().wireValue()); put(m,"sessionRef",v.sessionRef());
        m.put("surface",v.surface().wireValue()); m.put("entityScope",v.entityScope().wireValue());
        m.put("inputSnapshotRef",v.inputSnapshotRef().value()); put(m,"candidateSnapshotRef",ref(v.candidateSnapshotRef()));
        put(m,"outputSnapshotRef",ref(v.outputSnapshotRef())); m.put("queryNormalizationVersion",v.queryNormalizationVersion().value());
        m.put("retrievalStrategyVersion",v.retrievalStrategyVersion().value()); m.put("rankingPolicyVersion",v.rankingPolicyVersion().value());
        m.put("featureDefinitionVersion",v.featureDefinitionVersion().value()); m.put("referenceTime",v.referenceTime().toString());
        m.put("startedAt",v.startedAt().toString()); m.put("completedAt",v.completedAt().toString());
        m.put("producerBuildId",v.producerBuildId().value()); m.put("replayClass",v.replayClass().wireValue());
        m.put("replayEvidence",replayMap(v.replayEvidence())); put(m,"fallbackCode",v.fallbackCode()==null?null:v.fallbackCode().wireValue());
        put(m,"failureCode",v.failureCode()==null?null:v.failureCode().wireValue()); return ContractJsonWireV1.stringify(m);
    }
    public static SearchRunV1 readRun(String json) {
        Map<String,Object> m=object(json); ReplayClass rc=ReplayClass.fromWire(req(m,"replayClass"));
        return new SearchRunV1(new ContractId(req(m,"contractVersion")),new RunRef(req(m,"runId")),
                IntelligenceRunStatus.fromWire(req(m,"status")),opt(m,"requestId"),opt(m,"correlationId"),
                subject(m,"subjectRef","identitySchemeId"),opt(m,"sessionRef"),SearchSurface.fromWire(req(m,"surface")),
                SearchEntityScope.fromWire(req(m,"entityScope")),new SnapshotRef(req(m,"inputSnapshotRef")),
                opt(m,"candidateSnapshotRef")==null?null:new SnapshotRef(opt(m,"candidateSnapshotRef")),
                opt(m,"outputSnapshotRef")==null?null:new SnapshotRef(opt(m,"outputSnapshotRef")),
                new SchemaVersion(req(m,"queryNormalizationVersion")),new SchemaVersion(req(m,"retrievalStrategyVersion")),
                new PolicyVersion(req(m,"rankingPolicyVersion")),new FeatureDefinitionVersion(req(m,"featureDefinitionVersion")),
                Instant.parse(req(m,"referenceTime")),Instant.parse(req(m,"startedAt")),Instant.parse(req(m,"completedAt")),
                new ProducerBuildId(req(m,"producerBuildId")),rc,replay(obj(m,"replayEvidence"),rc),
                opt(m,"fallbackCode")==null?null:SearchFallbackCode.fromWire(opt(m,"fallbackCode")),
                opt(m,"failureCode")==null?null:SearchFailureCode.fromWire(opt(m,"failureCode")));
    }

    public static String writeExplanation(SearchExplanationV1 v) {
        LinkedHashMap<String,Object> m=base(v.contractVersion().value()); m.put("explanationId",v.explanationId());
        m.put("runId",v.runId().value()); m.put("audience",v.audience().wireValue());
        m.put("reasonCodes",v.reasonCodes().stream().map(SearchExplanationReason::wireValue).toList());
        put(m,"message",v.message()); m.put("evidenceRefs",v.evidenceRefs()); m.put("attributes",v.attributes());
        m.put("privacyClass",v.privacyClass().wireValue()); m.put("createdAt",v.createdAt().toString());
        return ContractJsonWireV1.stringify(m);
    }

    private static LinkedHashMap<String,Object> queryMap(SearchQueryV1 v) { LinkedHashMap<String,Object> m=new LinkedHashMap<>();
        m.put("queryMode",v.queryMode().wireValue()); put(m,"originalQuery",v.originalQuery()); put(m,"normalizedQuery",v.normalizedQuery());
        m.put("queryFingerprint",v.queryFingerprint()); m.put("normalizationVersion",v.normalizationVersion().value());
        put(m,"languageHint",v.languageHint()); put(m,"localeHint",v.localeHint()); m.put("codePointLength",v.codePointLength());
        m.put("utf8SizeBytes",v.utf8SizeBytes()); return m; }
    private static SearchQueryV1 readQuery(Map<String,Object> m) { return new SearchQueryV1(SearchQueryMode.fromWire(req(m,"queryMode")),
        opt(m,"originalQuery"),opt(m,"normalizedQuery"),req(m,"queryFingerprint"),new SchemaVersion(req(m,"normalizationVersion")),
        opt(m,"languageHint"),opt(m,"localeHint"),intValue(m,"codePointLength"),intValue(m,"utf8SizeBytes")); }
    private static LinkedHashMap<String,Object> contextMap(SearchContextV1 v) { LinkedHashMap<String,Object> m=new LinkedHashMap<>();
        put(m,"subjectRef",v.subjectRef()==null?null:v.subjectRef().value()); put(m,"identitySchemeId",v.subjectRef()==null?null:v.subjectRef().schemeId().wireValue());
        put(m,"sessionRef",v.sessionRef()); m.put("surface",v.surface().wireValue()); m.put("entityScope",v.entityScope().wireValue());
        m.put("referenceTime",v.referenceTime().toString()); put(m,"languageHint",v.languageHint()); put(m,"localeHint",v.localeHint());
        put(m,"coarseRegionRef",v.coarseRegionRef()==null?null:v.coarseRegionRef().value()); put(m,"consentPrivacyContextRef",v.consentPrivacyContextRef()); return m; }
    private static SearchContextV1 readContext(Map<String,Object> m) { return new SearchContextV1(subject(m,"subjectRef","identitySchemeId"),
        opt(m,"sessionRef"),SearchSurface.fromWire(req(m,"surface")),SearchEntityScope.fromWire(req(m,"entityScope")),
        Instant.parse(req(m,"referenceTime")),opt(m,"languageHint"),opt(m,"localeHint"),
        opt(m,"coarseRegionRef")==null?null:new EntityRef(opt(m,"coarseRegionRef")),opt(m,"consentPrivacyContextRef")); }
    private static List<Object> filterMaps(List<SearchFilterV1> values) { List<Object> out=new ArrayList<>(); for(SearchFilterV1 v:values){
        LinkedHashMap<String,Object> m=new LinkedHashMap<>();m.put("filterType",v.filterType().wireValue());m.put("values",v.values());
        m.put("source",v.source().wireValue());m.put("schemaVersion",v.schemaVersion().value());out.add(m);}return out; }
    private static List<SearchFilterV1> readFilters(List<Object> values) { List<SearchFilterV1> out=new ArrayList<>(); for(Object o:values){Map<String,Object> m=castMap(o);
        out.add(new SearchFilterV1(SearchFilterType.fromWire(req(m,"filterType")),strings(m,"values"),
            SearchFilterSource.fromWire(req(m,"source")),new SchemaVersion(req(m,"schemaVersion"))));}return out; }
    private static LinkedHashMap<String,Object> sortMap(SearchSortV1 v){LinkedHashMap<String,Object>m=new LinkedHashMap<>();m.put("sortType",v.sortType().wireValue());m.put("sortPolicyVersion",v.sortPolicyVersion().value());return m;}
    private static SearchSortV1 readSort(Map<String,Object>m){return new SearchSortV1(SearchSortType.fromWire(req(m,"sortType")),new PolicyVersion(req(m,"sortPolicyVersion")));}
    private static LinkedHashMap<String,Object> pageMap(SearchPageRequestV1 v){LinkedHashMap<String,Object>m=new LinkedHashMap<>();m.put("pageSize",v.pageSize());if(v.cursor()!=null)m.put("cursor",castMap(StrictContractJsonParserV1.parse(writeCursor(v.cursor()))));return m;}
    private static SearchPageRequestV1 readPage(Map<String,Object>m){Object c=m.get("cursor");return new SearchPageRequestV1(intValue(m,"pageSize"),c==null?null:readCursor(ContractJsonWireV1.stringify(c)));}
    private static Map<String,Object> replayMap(ReplayEvidenceDescriptorV1 v){LinkedHashMap<String,Object>m=new LinkedHashMap<>();m.put("deterministicPath",v.deterministicPath());m.put("immutableInputBound",v.immutableInputBound());m.put("immutableOutputBound",v.immutableOutputBound());m.put("versionsBound",v.versionsBound());m.put("deterministicSeedBound",v.deterministicSeedBound());m.put("modelOrProviderInferenceUsed",v.modelOrProviderInferenceUsed());return m;}
    private static ReplayEvidenceDescriptorV1 replay(Map<String,Object>m,ReplayClass rc){return new ReplayEvidenceDescriptorV1(rc,bool(m,"deterministicPath"),bool(m,"immutableInputBound"),bool(m,"immutableOutputBound"),bool(m,"versionsBound"),bool(m,"deterministicSeedBound"),bool(m,"modelOrProviderInferenceUsed"));}
    private static SubjectRef subject(Map<String,Object>m,String valueKey,String schemeKey){String v=opt(m,valueKey);if(v==null)return null;return new SubjectRef(IdentitySchemeId.fromWire(req(m,schemeKey)),v);}
    private static String ref(SnapshotRef v){return v==null?null:v.value();}
    private static LinkedHashMap<String,Object> base(String v){LinkedHashMap<String,Object>m=new LinkedHashMap<>();m.put("contractVersion",v);return m;}
    private static void put(Map<String,Object>m,String k,Object v){if(v!=null)m.put(k,v);}
    private static Map<String,Object> object(String json){return castMap(StrictContractJsonParserV1.parse(json));}
    @SuppressWarnings("unchecked") private static Map<String,Object> castMap(Object value){if(!(value instanceof Map<?,?>))throw new IllegalArgumentException("JSON object required");return (Map<String,Object>)value;}
    private static Map<String,Object> obj(Map<String,Object>m,String k){return castMap(required(m,k));}
    @SuppressWarnings("unchecked") private static List<Object> list(Map<String,Object>m,String k){Object v=required(m,k);if(!(v instanceof List<?>))throw new IllegalArgumentException(k+" must be array");return (List<Object>)v;}
    private static List<String> strings(Map<String,Object>m,String k){List<Object>v=list(m,k);List<String>out=new ArrayList<>();for(Object o:v){if(!(o instanceof String s))throw new IllegalArgumentException(k+" must contain strings");out.add(s);}return out;}
    private static Object required(Map<String,Object>m,String k){if(!m.containsKey(k)||m.get(k)==null)throw new IllegalArgumentException("Missing field: "+k);return m.get(k);}
    private static String req(Map<String,Object>m,String k){Object v=required(m,k);if(!(v instanceof String s))throw new IllegalArgumentException(k+" must be string");return s;}
    private static String opt(Map<String,Object>m,String k){Object v=m.get(k);if(v==null)return null;if(!(v instanceof String s))throw new IllegalArgumentException(k+" must be string");return s;}
    private static boolean bool(Map<String,Object>m,String k){Object v=required(m,k);if(!(v instanceof Boolean b))throw new IllegalArgumentException(k+" must be boolean");return b.booleanValue();}
    private static int intValue(Map<String,Object>m,String k){Object v=required(m,k);if(!(v instanceof Long n)||n.longValue()<Integer.MIN_VALUE||n.longValue()>Integer.MAX_VALUE)throw new IllegalArgumentException(k+" must be integer");return n.intValue();}
    private static Integer integerOpt(Map<String,Object>m,String k){Object v=m.get(k);if(v==null)return null;if(!(v instanceof Long n)||n.longValue()<Integer.MIN_VALUE||n.longValue()>Integer.MAX_VALUE)throw new IllegalArgumentException(k+" must be integer");return Integer.valueOf(n.intValue());}
    private static Double doubleOpt(Map<String,Object>m,String k){Object v=m.get(k);if(v==null)return null;if(v instanceof Double d)return d;if(v instanceof Long n)return Double.valueOf(n.doubleValue());throw new IllegalArgumentException(k+" must be number");}
}
