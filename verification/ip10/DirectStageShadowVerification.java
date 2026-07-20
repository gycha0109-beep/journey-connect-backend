import com.jc.backend.common.PageResponse;
import com.jc.backend.post.PostDtos;
import com.jc.backend.search.shadow.*;
import com.jc.backend.search.shadow.stage.*;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.integration.search.v1.*;
import com.jc.intelligence.runtime.search.v1.*;
import com.jc.intelligence.runtime.search.v1.fixture.PassThroughSearchCandidateFilter;
import com.jc.intelligence.runtime.search.v1.port.SearchDependencyDecision;
import com.jc.intelligence.runtime.search.v1.ranking.NoOpSearchRerankingPort;
import com.jc.intelligence.wiring.search.v1.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class DirectStageShadowVerification {
  private static int checks;
  private static final Instant TIME = Instant.parse("2026-07-19T03:00:00Z");
  public static void main(String[] args) throws Exception {
    propertiesAndProductionGuard();
    activeStageExecutesRealRuntimeWithoutResponseImpact();
    zeroSampleSkipsEverything();
    providerFailureAndClosedExecutorFailOpen();
    boundedQueueAndTimeout();
    System.out.println("IP-10 direct test/stage assertions: " + checks + " PASS");
  }
  private static void propertiesAndProductionGuard() {
    check(StageSearchShadowProperties.from(env(new String[0], Map.of())) == null, "default disabled");
    check(StageSearchShadowProperties.from(env(new String[]{"search-shadow-test"}, Map.of())) == null, "missing allow disabled");
    check(StageSearchShadowProperties.from(env(new String[]{"prod","search-shadow-stage"}, activeProps(10000))) == null, "prod guard");
    check(StageSearchShadowProperties.from(env(new String[]{"production","search-shadow-stage"}, activeProps(10000))) == null, "production guard");
    StageSearchShadowProperties props = StageSearchShadowProperties.from(env(new String[]{"search-shadow-test"}, activeProps(10000)));
    check(props != null, "test active");
    check(props.sampleBasisPoints() == 10000, "sample parsed");
    check(props.timeout().equals(Duration.ofMillis(200)), "timeout parsed");
    check(props.queueCapacity() == 8 && props.maxConcurrency() == 2, "bounds parsed");
    Map<String,String> invalid = new HashMap<>(activeProps(10000)); invalid.put("search.shadow.stage.sample-basis-points", "10001");
    check(!StageSearchShadowProperties.activationAllowed(env(new String[]{"search-shadow-stage"}, invalid)), "invalid sample fail closed");
  }
  private static void activeStageExecutesRealRuntimeWithoutResponseImpact() {
    StageSearchShadowProperties props = StageSearchShadowProperties.from(env(new String[]{"search-shadow-test"}, activeProps(10000)));
    InMemoryStageSearchCatalog catalog = new InMemoryStageSearchCatalog();
    SearchRuntime runtime = new DefaultSearchRuntime(catalog, new PassThroughSearchCandidateFilter(),
      (request,candidate)->SearchDependencyDecision.ALLOW, (request,candidate)->SearchDependencyDecision.ALLOW,
      catalog, new NoOpSearchRerankingPort());
    try (StageBoundedSearchShadowExecutionPort runtimePort = new StageBoundedSearchShadowExecutionPort(2,8);
         StageSearchShadowTaskExecutor dispatch = new StageSearchShadowTaskExecutor(2,8)) {
      SearchShadowPolicyV1 policy = new SearchShadowPolicyV1(SearchShadowMode.TEST_ONLY,
        new PolicyVersion("search-shadow-policy-v1"), new PolicyVersion("search-shadow-comparison-policy-v1"),
        props.timeout(), props.topK(), new ProducerBuildId("ip10-test-stage-shadow"));
      SearchShadowIntegrationPort<PageResponse<PostDtos.Summary>> integration = new SearchShadowIntegrationBoundary<>(policy,runtime,runtimePort);
      InMemoryStageSearchShadowComparisonLogPort log = new InMemoryStageSearchShadowComparisonLogPort(10);
      StageExploreSearchShadowHook hook = new StageExploreSearchShadowHook(props.wiringConfig(), dispatch,
        new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.CLOSED,false), integration,
        new DefaultStageExploreSearchRuntimeInputProviderFactory(100), log);
      DefaultExploreSearchShadowBridge bridge = new DefaultExploreSearchShadowBridge(
        new DefaultExploreShadowHookRequestFactory(new StageExploreShadowRequestContextProvider(Clock.fixed(TIME, ZoneOffset.UTC))), hook);
      PageResponse<PostDtos.Summary> response = response();
      List<PostDtos.Summary> items = response.items();
      bridge.afterExplore("  서울   여행  ", "서울", pageable(0,20), response);
      check(dispatch.awaitIdle(Duration.ofSeconds(2)), "dispatch idle");
      check(response.items() == items, "items identity");
      check(response.page()==0 && response.size()==20 && response.totalElements()==1L, "page unchanged");
      check(catalog.retrievalInvocationCount()==1L, "runtime retrieval once");
      check(catalog.rankingInvocationCount()==1L, "runtime ranking once");
      check(runtimePort.invocationCount()==1L, "execution once");
      check(log.records().size()==1, "evidence once");
      String safe = log.records().getFirst().toString();
      check(!safe.contains("서울"), "raw query absent");
      check(!safe.contains("correlation:ip10-stage") && !safe.contains("session:ip10-stage"), "raw ids absent");
      check(dispatch.acceptedCount()==1L && dispatch.failedCount()==0L, "dispatch accepted");
    }
  }
  private static void zeroSampleSkipsEverything() {
    StageSearchShadowProperties props = StageSearchShadowProperties.from(env(new String[]{"search-shadow-stage"}, activeProps(0)));
    InMemoryStageSearchCatalog catalog = new InMemoryStageSearchCatalog();
    SearchRuntime runtime = new DefaultSearchRuntime(catalog, new PassThroughSearchCandidateFilter(),
      (request,candidate)->SearchDependencyDecision.ALLOW, (request,candidate)->SearchDependencyDecision.ALLOW,
      catalog, new NoOpSearchRerankingPort());
    try (StageBoundedSearchShadowExecutionPort runtimePort = new StageBoundedSearchShadowExecutionPort(2,8);
         StageSearchShadowTaskExecutor dispatch = new StageSearchShadowTaskExecutor(2,8)) {
      SearchShadowPolicyV1 policy = new SearchShadowPolicyV1(SearchShadowMode.TEST_ONLY,
        new PolicyVersion("search-shadow-policy-v1"), new PolicyVersion("search-shadow-comparison-policy-v1"),
        props.timeout(), props.topK(), new ProducerBuildId("ip10-test-stage-shadow"));
      InMemoryStageSearchShadowComparisonLogPort log = new InMemoryStageSearchShadowComparisonLogPort(10);
      StageExploreSearchShadowHook hook = new StageExploreSearchShadowHook(props.wiringConfig(), dispatch,
        new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.CLOSED,false),
        new SearchShadowIntegrationBoundary<>(policy,runtime,runtimePort),
        new DefaultStageExploreSearchRuntimeInputProviderFactory(100), log);
      PageResponse<PostDtos.Summary> response=response();
      SearchShadowDispatchReceiptV1<PageResponse<PostDtos.Summary>> receipt = hook.dispatch(hookRequest(response));
      check(receipt.status()==SearchShadowDispatchStatus.NOT_SAMPLED, "zero sample status");
      check(dispatch.acceptedCount()==0L, "zero dispatch");
      check(runtimePort.invocationCount()==0L, "zero runtime");
      check(log.attemptCount()==0L, "zero logging");
      check(receipt.legacyResponse()==response, "response authority");
    }
  }
  private static void providerFailureAndClosedExecutorFailOpen() {
    StageSearchShadowProperties props = StageSearchShadowProperties.from(env(new String[]{"search-shadow-test"}, activeProps(10000)));
    PageResponse<PostDtos.Summary> response=response();
    try (StageSearchShadowTaskExecutor dispatch = new StageSearchShadowTaskExecutor(2,8)) {
      StageExploreSearchShadowHook hook = new StageExploreSearchShadowHook(props.wiringConfig(),dispatch,
        new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.CLOSED,false),
        (legacy,compat,ctx,provider)->{ throw new AssertionError("not reached"); },
        (request,ctx)->{ throw new IllegalStateException("provider factory failed"); },
        new InMemoryStageSearchShadowComparisonLogPort(10));
      var receipt=hook.dispatch(hookRequest(response));
      check(receipt.status()==SearchShadowDispatchStatus.SUBMITTED,"provider failure submitted");
      check(dispatch.awaitIdle(Duration.ofSeconds(1)),"provider failure idle");
      check(dispatch.failedCount()==1L,"provider failure isolated");
      check(receipt.legacyResponse()==response,"provider failure response unchanged");
    }
    StageSearchShadowTaskExecutor closed=new StageSearchShadowTaskExecutor(1,1); closed.close();
    StageExploreSearchShadowHook unavailable=new StageExploreSearchShadowHook(props.wiringConfig(),closed,
      new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.CLOSED,false),
      (legacy,compat,ctx,provider)->{throw new AssertionError();},
      (request,ctx)->{throw new AssertionError();}, new InMemoryStageSearchShadowComparisonLogPort(10));
    check(unavailable.dispatch(hookRequest(response)).status()==SearchShadowDispatchStatus.DISABLED,"closed executor disables");
  }
  private static void boundedQueueAndTimeout() throws Exception {
    try (StageSearchShadowTaskExecutor exec=new StageSearchShadowTaskExecutor(1,1)) {
      CountDownLatch started=new CountDownLatch(1), release=new CountDownLatch(1);
      check(exec.submit(()->{started.countDown(); await(release);}).status()==StageSearchShadowSubmissionStatus.ACCEPTED,"first accepted");
      check(started.await(1,TimeUnit.SECONDS),"first started");
      check(exec.submit(()->await(release)).status()==StageSearchShadowSubmissionStatus.ACCEPTED,"queued accepted");
      check(exec.submit(()->{}).status()==StageSearchShadowSubmissionStatus.QUEUE_FULL,"queue full");
      release.countDown(); check(exec.awaitIdle(Duration.ofSeconds(2)),"queue drained");
    }
    try (StageBoundedSearchShadowExecutionPort port=new StageBoundedSearchShadowExecutionPort(1,1)) {
      SearchRuntimeExecutionRequestV1 request=new DefaultStageExploreSearchRuntimeInputProviderFactory(100)
        .create(new com.jc.intelligence.compat.search.explore.v1.LegacyExploreRequestView("서울",null,0,20,List.of(),Map.of()),
          new SearchShadowContextV1("request:ip10","correlation:ip10","session:ip10",TIME))
        .provide(new SearchShadowRuntimeInputContextV1("correlation:ip10",TIME,
          SearchShadowFingerprintV1.sha256("request"),SearchShadowFingerprintV1.sha256("response"))).executionRequest();
      SearchShadowExecutionOutcomeV1 outcome=port.execute(execution->{try{Thread.sleep(500);}catch(InterruptedException e){Thread.currentThread().interrupt();}return null;},request,new SearchShadowExecutionDeadlineV1(TIME,Duration.ofMillis(20)));
      check(outcome.status().wireValue().equals("timed_out"),"runtime timeout");
      check(port.cancellationCount()==1L,"runtime cancelled");
    }
  }
  private static SearchShadowHookRequestV1<PageResponse<PostDtos.Summary>> hookRequest(PageResponse<PostDtos.Summary> response){
    var legacyReq=new com.jc.intelligence.compat.search.explore.v1.LegacyExploreRequestView("서울","서울",0,20,List.of(),Map.of());
    var legacyPage=new com.jc.intelligence.compat.search.explore.v1.LegacyExplorePageView(List.of(),0,20,1L,1,true);
    var compatCtx=new com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityContext("request:ip10","correlation:ip10","session:ip10",TIME,TIME,new ProducerBuildId("ip10-test-stage-shadow"));
    return new SearchShadowHookRequestV1<>(response,legacyReq,legacyPage,compatCtx,new SearchShadowContextV1("request:ip10","correlation:ip10","session:ip10",TIME));
  }
  private static Map<String,String> activeProps(int sample){return Map.of(
    "search.shadow.stage.explicit-allow","true","search.shadow.stage.mode","test_only",
    "search.shadow.stage.sample-basis-points",Integer.toString(sample),"search.shadow.stage.timeout-millis","200",
    "search.shadow.stage.queue-capacity","8","search.shadow.stage.max-concurrency","2","search.shadow.stage.top-k","10");}
  private static Environment env(String[] profiles,Map<String,String> props){return new Environment(){
    public String[] getActiveProfiles(){return profiles.clone();}
    public String getProperty(String key){return props.get(key);}
    public String getProperty(String key,String defaultValue){return props.getOrDefault(key,defaultValue);}
    @SuppressWarnings("unchecked") public <T>T getProperty(String key,Class<T> type,T defaultValue){String value=props.get(key); if(value==null)return defaultValue; if(type==Boolean.class)return (T)Boolean.valueOf(value); throw new IllegalArgumentException("unsupported");}
  };}
  private static Pageable pageable(int page,int size){return new Pageable(){public int getPageNumber(){return page;}public int getPageSize(){return size;}public Sort getSort(){return new Sort(){public boolean isUnsorted(){return true;}public java.util.stream.Stream<Sort.Order> stream(){return java.util.stream.Stream.empty();}};}};}
  private static PageResponse<PostDtos.Summary> response(){return new PageResponse<>(List.of(new PostDtos.Summary(101L,"legacy-title","KR-11","서울",null,3L,2L,1L,new PostDtos.Author(7L,"legacy-author",null),Instant.parse("2026-07-19T00:00:00Z"))),0,20,1L,1,true);}
  private static void await(CountDownLatch latch){try{latch.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}
  private static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);checks++;}
}
