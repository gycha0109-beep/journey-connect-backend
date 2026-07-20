package com.jc.intelligence.wiring.search.v1.fixture;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SearchShadowWiringFixtureJsonCodecV1 {
    private SearchShadowWiringFixtureJsonCodecV1() { }
    public static SearchShadowWiringFixtureCaseV1 read(String json) {
        if (json == null) throw new IllegalArgumentException("json is required");
        return new SearchShadowWiringFixtureCaseV1(text(json,"scenario"), text(json,"mode"), nullableText(json,"activeProfile"),
                bool(json,"explicitAllow"), integer(json,"sampleBasisPoints"), text(json,"executorStatus"),
                text(json,"circuitState"), text(json,"expectedStatus"));
    }
    public static String write(SearchShadowWiringFixtureCaseV1 value) {
        return "{\n  \"scenario\": \""+escape(value.scenario())+"\",\n  \"mode\": \""+escape(value.mode())+"\",\n  \"activeProfile\": "+(value.activeProfile()==null?"null":"\""+escape(value.activeProfile())+"\"")+",\n  \"explicitAllow\": "+value.explicitAllow()+",\n  \"sampleBasisPoints\": "+value.sampleBasisPoints()+",\n  \"executorStatus\": \""+escape(value.executorStatus())+"\",\n  \"circuitState\": \""+escape(value.circuitState())+"\",\n  \"expectedStatus\": \""+escape(value.expectedStatus())+"\"\n}\n";
    }
    private static String text(String json,String key){ String value=nullableText(json,key); if(value==null)throw new IllegalArgumentException(key+" is required"); return value; }
    private static String nullableText(String json,String key){ Matcher m=Pattern.compile("\\\""+key+"\\\"\\s*:\\s*(null|\\\"((?:\\\\.|[^\\\"])*)\\\")").matcher(json); if(!m.find())throw new IllegalArgumentException(key+" missing"); return "null".equals(m.group(1))?null:unescape(m.group(2)); }
    private static boolean bool(String json,String key){ Matcher m=Pattern.compile("\\\""+key+"\\\"\\s*:\\s*(true|false)").matcher(json); if(!m.find())throw new IllegalArgumentException(key+" missing"); return Boolean.parseBoolean(m.group(1)); }
    private static int integer(String json,String key){ Matcher m=Pattern.compile("\\\""+key+"\\\"\\s*:\\s*(-?[0-9]+)").matcher(json); if(!m.find())throw new IllegalArgumentException(key+" missing"); return Integer.parseInt(m.group(1)); }
    private static String escape(String v){ return v.replace("\\","\\\\").replace("\"","\\\""); }
    private static String unescape(String v){ return v.replace("\\\"","\"").replace("\\\\","\\"); }
}
