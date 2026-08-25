#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, random, re
from dataclasses import dataclass, asdict
from datetime import datetime, timedelta
from pathlib import Path
from urllib.parse import quote

ANCHOR=datetime(2026,8,25,10,0,0)
PWD="$2a$10$3J4R9YEgnlb.NXmdGmCjleuPEsNmnTFYmAzsr0TDwPSGnxG1XZW7a"
DOMAIN="journey-connect.local"

@dataclass(frozen=True)
class D:
    code:str; cc:str; name:str; ko:str; lon:float; lat:float; w:int; photo:str; places:str; tags:str

RAW=[
("KR-SEOUL","KR","Seoul","서울",126.978,37.5665,18,"seoul,korea,city","성수|서울숲|연남|망원|을지로|익선동|한강|북촌","서울여행|카페투어|도보여행|감성여행|맛집"),
("KR-BUSAN","KR","Busan","부산",129.0756,35.1796,7,"busan,korea,sea","해운대|광안리|영도|흰여울|송정|남포동","부산여행|바다여행|먹방|야경|도보여행"),
("KR-JEJU","KR","Jeju","제주",126.5312,33.4996,8,"jeju,korea,island","월정리|성산|애월|협재|세화|오름","제주여행|드라이브|카페투어|바다|자연"),
("JP-TOKYO","JP","Tokyo","도쿄",139.6917,35.6895,10,"tokyo,japan,street","시부야|시모키타자와|긴자|아사쿠사|키치죠지|다이칸야마","도쿄여행|일본여행|쇼핑|카페|도보여행"),
("JP-OSAKA","JP","Osaka","오사카",135.5023,34.6937,7,"osaka,japan,food","도톤보리|우메다|신세카이|난바|나카자키초|오사카성","오사카여행|일본여행|먹방|쇼핑|야경"),
("JP-KYOTO","JP","Kyoto","교토",135.7681,35.0116,5,"kyoto,japan,temple","기온|아라시야마|후시미이나리|니시키시장|철학의길","교토여행|일본여행|사찰|산책|사진스팟"),
("JP-FUKUOKA","JP","Fukuoka","후쿠오카",130.4017,33.5904,5,"fukuoka,japan,food","텐진|하카타|오호리공원|나카스|다자이후","후쿠오카|일본여행|먹방|카페|주말여행"),
("US-NYC","US","New York","뉴욕",-74.006,40.7128,6,"new+york,manhattan,city","브루클린|소호|센트럴파크|첼시|덤보|웨스트빌리지","뉴욕여행|미국여행|도시여행|브런치|사진스팟"),
("US-LAX","US","Los Angeles","로스앤젤레스",-118.2437,34.0522,5,"los+angeles,california,travel","산타모니카|베니스비치|그리피스|실버레이크|다운타운LA","LA여행|미국여행|드라이브|바다|브런치"),
("US-SFO","US","San Francisco","샌프란시스코",-122.4194,37.7749,4,"san+francisco,california,travel","피셔맨스워프|미션|골든게이트|헤이즈밸리|팰리스오브파인아츠","샌프란시스코|미국여행|도보여행|카페|뷰포인트"),
("US-LAS","US","Las Vegas","라스베이거스",-115.1398,36.1699,2,"las+vegas,nevada,night","스트립|벨라지오|프리몬트|레드락캐니언","라스베이거스|미국여행|야경|호텔|로드트립"),
("US-HNL","US","Honolulu","하와이",-157.8583,21.3069,6,"hawaii,honolulu,beach","와이키키|다이아몬드헤드|카일루아|노스쇼어|알라모아나","하와이|휴양|바다|트레킹|드라이브"),
("GU-GUM","GU","Guam","괌",144.7937,13.4443,4,"guam,tropical,beach","투몬비치|사랑의절벽|리티디안|아가나|이나라한","괌여행|휴양|바다|렌터카|가족여행"),
("TH-BKK","TH","Bangkok","방콕",100.5018,13.7563,8,"bangkok,thailand,street","아리|짜뚜짝|차이나타운|왓아룬|통로|아이콘시암","방콕여행|태국여행|야시장|맛집|카페투어"),
("TW-TPE","TW","Taipei","타이베이",121.5654,25.033,7,"taipei,taiwan,street","시먼딩|중산|용캉제|스린|단수이|다다오청","대만여행|타이베이|야시장|먹방|카페"),
("TW-KHH","TW","Kaohsiung","가오슝",120.3014,22.6273,2,"kaohsiung,taiwan,harbor","보얼예술특구|치진|연지담|류허야시장","대만여행|가오슝|항구|야시장|도보여행"),
("FR-PAR","FR","Paris","파리",2.3522,48.8566,6,"paris,france,street","마레|몽마르트|생제르맹|루브르|에펠탑|카날생마르탱","파리여행|유럽여행|미술관|카페|산책"),
("GB-LON","GB","London","런던",-0.1276,51.5072,4,"london,england,street","쇼디치|소호|노팅힐|버러마켓|사우스뱅크","런던여행|유럽여행|마켓|미술관|도보여행"),
("CZ-PRG","CZ","Prague","프라하",14.4378,50.0755,2,"prague,czech,old+town","구시가지|카를교|말라스트라나|프라하성","프라하여행|유럽여행|야경|구시가지|산책"),
("ES-BCN","ES","Barcelona","바르셀로나",2.1734,41.3851,5,"barcelona,spain,architecture","고딕지구|사그라다파밀리아|그라시아|보케리아|바르셀로네타","스페인여행|바르셀로나|건축|시장|바다"),
("ES-MAD","ES","Madrid","마드리드",-3.7038,40.4168,3,"madrid,spain,street","말라사냐|레티로|라라티나|프라도|마요르광장","스페인여행|마드리드|미술관|타파스|산책"),
("IT-ROM","IT","Rome","로마",12.4964,41.9028,5,"rome,italy,architecture","트라스테베레|콜로세움|판테온|보르게세|나보나광장","이탈리아여행|로마|역사여행|맛집|도보여행"),
("IT-FLR","IT","Florence","피렌체",11.2558,43.7696,3,"florence,italy,renaissance","두오모|우피치|산타크로체|미켈란젤로광장","이탈리아여행|피렌체|미술관|노을|도보여행"),
("IT-VCE","IT","Venice","베네치아",12.3155,45.4408,3,"venice,italy,canal","산마르코|도르소두로|리알토|부라노|무라노","이탈리아여행|베네치아|운하|사진스팟|산책")]
DS=tuple(D(*r) for r in RAW)
MOODS="감성|로컬|먹방|카페|산책|야경|쇼핑|드라이브|사진|주말|혼자|친구랑".split("|")
COMMENTS="여기 저장해뒀다가 다음 여행에 꼭 가봐야겠어요!|동선이 깔끔해서 그대로 따라가도 좋겠네요.|사진 분위기 너무 좋습니다. 이 시간대가 제일 예쁜가요?|저도 여기 다녀왔는데 정말 좋았어요 🙌|이 코스 참고해서 일정 짜봐야겠네요.|맛집 정보까지 있어서 도움됐어요!|다음에 근처 가면 한 번 들러보고 싶어요.|걷기 좋아하는 사람한테 딱 맞는 코스네요.".split("|")

def q(s): return "'"+str(s).replace("'","''")+"'"
def pick(r): return r.choices(DS,weights=[d.w for d in DS],k=1)[0]
def img(d,lock,w=1200,h=800): return f"https://loremflickr.com/{w}/{h}/{d.photo}/all?lock={lock}"
def avatar(seed): return f"https://api.dicebear.com/10.x/notionists-neutral/svg?seed={quote(seed,safe='')}"
def tail(r,scale,cap): return min(cap,max(0,int((r.paretovariate(1.7)-1)*scale)))
def vals(rows): return ",\n".join("    ("+", ".join(x)+")" for x in rows)

def users(r,b,n):
    pre="여행하는|걷는|느긋한|기록하는|떠도는|주말의|골목찾는|카페찾는|사진찍는|맛집찾는".split("|"); suf="민지|서연|지우|하늘|도윤|준|유나|수현|예린|태오|지민|하루|로니|모모|루카|제이".split("|")
    out=[]
    for i in range(1,n+1):
        d=pick(r); out.append(dict(key=f"user-{i:04d}",email=f"synthetic.{b}.{i:04d}@{DOMAIN}",nickname=f"{r.choice(pre)}{r.choice(suf)}{i:03d}"[:40],bio=f"[synthetic:{b}] {d.ko}와 {r.choice(MOODS)} 여행을 좋아하는 테스트 프로필입니다.",profile_image_url=avatar(f"jc-{b}-{i}")))
    return out

def posts(r,b,us,n,anchor):
    out=[]; tt=["{p}에서 보낸 {m} 반나절 코스","{p} 처음 가면 이렇게 돌아보세요","{d} {m} 하루 루트 정리","{p} 중심으로 천천히 걷는 하루","다시 가고 싶은 {d} {m} 스팟","{p}부터 시작한 {d} 하루 여행"]; ct=["{p}부터 시작해서 근처 골목과 로컬 스팟을 천천히 둘러봤어요. 이동 거리가 무리 없고 중간에 쉬기 좋은 곳도 많아서 {m} 여행으로 추천합니다.","이번 {d} 일정은 유명한 곳만 빠르게 찍기보다 {p} 주변을 오래 걷는 식으로 잡았습니다. 생각보다 동선이 편했고 사진 남길 포인트도 많았어요.","아침부터 저녁까지 {p} 일대를 중심으로 움직였습니다. 카페와 식사, 산책을 섞으니 하루가 빡빡하지 않고 딱 좋았습니다."]
    for i in range(1,n+1):
        d=pick(r); ps=d.places.split("|"); p=r.choice(ps); m=r.choice(MOODS); a=r.choice(us); created=anchor-timedelta(minutes=r.randint(0,259200)); ic=r.choices([1,2,3,4,5],[18,18,20,34,10],k=1)[0]; ims=[img(d,10000+i*10+j) for j in range(ic)]; tags=list(dict.fromkeys([r.choice(d.tags.split("|")),r.choice(d.tags.split("|")),m,p,d.ko]))[:5]
        stops=[]
        if r.random()<.62:
            for j,sp in enumerate(r.sample(ps,r.randint(2,min(6,len(ps)))),1): stops.append(dict(order=j,place=sp,time=f"{(9+j*2)%24:02d}:{r.choice([0,10,20,30,40,50]):02d}",lat=round(d.lat+r.uniform(-.035,.035),6),lon=round(d.lon+r.uniform(-.035,.035),6)))
        views=10+tail(r,420,50000); likes=min(len(us)-1,int(views*r.uniform(.015,.12))); marks=min(len(us)-1,int(likes*r.uniform(.08,.42))); eligible=[u for u in us if u!=a]; r.shuffle(eligible); comments=min(20,int(likes*r.uniform(.02,.15)))
        out.append(dict(key=f"post-{i:04d}",author_email=a["email"],region_code=d.code,region_name=d.name,region_ko=d.ko,place=p,title=(r.choice(tt).format(p=p,m=m,d=d.ko)+f" · {i:04d}")[:120],content=r.choice(ct).format(p=p,m=m,d=d.ko),cover_image_url=ims[0],images=ims,tags=[x[:20] for x in tags],route_stops=stops,view_count=views,created_at=created.strftime("%Y-%m-%d %H:%M:%S"),like_user_emails=[u["email"] for u in eligible[:likes]],bookmark_user_emails=[u["email"] for u in eligible[likes:likes+marks]],commenters=[dict(email=eligible[(likes+marks+j)%len(eligible)]["email"],content=r.choice(COMMENTS)) for j in range(comments)]))
    return sorted(out,key=lambda x:x["created_at"],reverse=True)

def crews(r,b,us,n,anchor):
    out=[]; tt=["{p} {m} 같이 돌아볼 분","{d} 하루 동행 크루 모집","{p} 사진 산책 같이 하실 분","{d} 맛집 + 산책 소규모 크루"]
    for i in range(1,n+1):
        d=pick(r); p=r.choice(d.places.split("|")); m=r.choice("산책|사진|맛집|카페|야경|쇼핑".split("|")); o=r.choice(us); cap=r.randint(4,10); pool=[u for u in us if u!=o]; r.shuffle(pool); ac=r.randint(1,max(1,cap-2)); pc=r.randint(0,min(3,max(0,cap-1-ac))); mem=[dict(email=u["email"],status="APPROVED" if j<ac else "PENDING") for j,u in enumerate(pool[:ac+pc])]
        out.append(dict(key=f"crew-{i:03d}",owner_email=o["email"],region_code=d.code,region_name=d.name,title=(r.choice(tt).format(p=p,m=m,d=d.ko)+f" · {i:03d}")[:120],description=f"[synthetic:{b}] {d.ko} {p}를 중심으로 {m} 여행을 함께할 소규모 크루입니다.",travel_date=(anchor.date()+timedelta(days=r.randint(3,120))).isoformat(),capacity=cap,recruiting=r.random()<.86,approval_required=r.random()<.78,cover_image_url=img(d,90000+i,900,600),tags=list(dict.fromkeys([r.choice(d.tags.split("|")),m,d.ko]))[:3],members=mem))
    return out

def validate(us,ps,cs):
    assert len({u['email'] for u in us})==len(us)==len({u['nickname'] for u in us}); codes={d.code for d in DS}; assert all(p['region_code'] in codes and 1<=len(p['images'])<=5 and 1<=len(p['tags'])<=5 and len(p['title'])<=120 for p in ps); assert all(c['region_code'] in codes and 4<=c['capacity']<=10 for c in cs)

def render_sql(b,us,ps,cs,anchor):
    pat=q(f"synthetic.{b}.%@{DOMAIN}"); o=[f"-- generated synthetic batch={b} users={len(us)} posts={len(ps)} crews={len(cs)}","-- DEV/DEMO ONLY. Do not execute against production.","BEGIN;","SET LOCAL statement_timeout='120s';",f"DELETE FROM crew WHERE owner_id IN (SELECT id FROM user_account WHERE email LIKE {pat});",f"DELETE FROM journey_post WHERE author_id IN (SELECT id FROM user_account WHERE email LIKE {pat});",f"DELETE FROM user_account WHERE email LIKE {pat};"]
    o += ["INSERT INTO region(code,country_code,display_name,center) VALUES",vals([(q(d.code),q(d.cc),q(d.name),f"ST_SetSRID(ST_MakePoint({d.lon},{d.lat}),4326)") for d in DS]),"ON CONFLICT(code) DO UPDATE SET country_code=EXCLUDED.country_code,display_name=EXCLUDED.display_name,center=EXCLUDED.center;","INSERT INTO user_account(email,password_hash,nickname,bio,profile_image_url) VALUES",vals([(q(u['email']),q(PWD),q(u['nickname']),q(u['bio']),q(u['profile_image_url'])) for u in us]),"ON CONFLICT(email) DO NOTHING;"]
    tags=sorted({t for p in ps for t in p['tags']}|{t for c in cs for t in c['tags']}); o += ["INSERT INTO tag(name,normalized_name) VALUES",vals([(q(t),q(t.strip().lower())) for t in tags]),"ON CONFLICT(normalized_name) DO NOTHING;","CREATE TEMP TABLE _sp(k varchar(32) primary key,id bigint) ON COMMIT DROP;","CREATE TEMP TABLE _sc(k varchar(32) primary key,id bigint) ON COMMIT DROP;"]
    for p in ps:
        o += [f"WITH x AS (INSERT INTO journey_post(author_id,title,content,region_name,region_id,cover_image_url,view_count,published,created_at,updated_at) SELECT u.id,{q(p['title'])},{q(p['content'])},r.display_name,r.id,{q(p['cover_image_url'])},{p['view_count']},TRUE,{q(p['created_at'])}::timestamp,{q(p['created_at'])}::timestamp FROM user_account u JOIN region r ON r.code={q(p['region_code'])} WHERE u.email={q(p['author_email'])} RETURNING id) INSERT INTO _sp SELECT {q(p['key'])},id FROM x;","INSERT INTO post_image(post_id,image_url,sort_order,alt_text) VALUES",vals([(f"(SELECT id FROM _sp WHERE k={q(p['key'])})",q(url),str(j),q(f"{p['region_ko']} {p['place']} 여행 이미지 {j+1}")) for j,url in enumerate(p['images'])])+";"]
        for j,t in enumerate(p['tags']): o.append(f"INSERT INTO post_tag(post_id,tag_id,sort_order) SELECT s.id,t.id,{j} FROM _sp s JOIN tag t ON t.normalized_name={q(t.lower())} WHERE s.k={q(p['key'])} ON CONFLICT DO NOTHING;")
        if p['like_user_emails']: o.append(f"INSERT INTO post_like(post_id,user_id) SELECT s.id,u.id FROM _sp s JOIN user_account u ON u.email IN ({','.join(q(x) for x in p['like_user_emails'])}) WHERE s.k={q(p['key'])} ON CONFLICT DO NOTHING;")
        if p['bookmark_user_emails']: o.append(f"INSERT INTO bookmark(post_id,user_id) SELECT s.id,u.id FROM _sp s JOIN user_account u ON u.email IN ({','.join(q(x) for x in p['bookmark_user_emails'])}) WHERE s.k={q(p['key'])} ON CONFLICT DO NOTHING;")
        for j,c in enumerate(p['commenters']):
            ts=(datetime.fromisoformat(p['created_at'])+timedelta(minutes=15+j*23)).strftime("%Y-%m-%d %H:%M:%S"); o.append(f"INSERT INTO post_comment(post_id,author_id,content,created_at,updated_at) SELECT s.id,u.id,{q(c['content'])},{q(ts)}::timestamp,{q(ts)}::timestamp FROM _sp s JOIN user_account u ON u.email={q(c['email'])} WHERE s.k={q(p['key'])};")
    for c in cs:
        o.append(f"WITH x AS (INSERT INTO crew(owner_id,title,region_name,region_id,description,travel_date,capacity,recruiting,approval_required,cover_image_url) SELECT u.id,{q(c['title'])},r.display_name,r.id,{q(c['description'])},{q(c['travel_date'])}::date,{c['capacity']},{str(c['recruiting']).upper()},{str(c['approval_required']).upper()},{q(c['cover_image_url'])} FROM user_account u JOIN region r ON r.code={q(c['region_code'])} WHERE u.email={q(c['owner_email'])} RETURNING id) INSERT INTO _sc SELECT {q(c['key'])},id FROM x;")
        o.append(f"INSERT INTO crew_member(crew_id,user_id,status,reviewed_by,reviewed_at) SELECT s.id,u.id,'OWNER',u.id,CURRENT_TIMESTAMP FROM _sc s JOIN user_account u ON u.email={q(c['owner_email'])} WHERE s.k={q(c['key'])} ON CONFLICT(crew_id,user_id) DO NOTHING;")
        for m in c['members']:
            rev=f"(SELECT id FROM user_account WHERE email={q(c['owner_email'])})" if m['status']=='APPROVED' else 'NULL'; at='CURRENT_TIMESTAMP' if m['status']=='APPROVED' else 'NULL'; o.append(f"INSERT INTO crew_member(crew_id,user_id,status,reviewed_by,reviewed_at) SELECT s.id,u.id,{q(m['status'])},{rev},{at} FROM _sc s JOIN user_account u ON u.email={q(m['email'])} WHERE s.k={q(c['key'])} ON CONFLICT(crew_id,user_id) DO NOTHING;")
        for j,t in enumerate(c['tags']): o.append(f"INSERT INTO crew_tag(crew_id,tag_id,sort_order) SELECT s.id,t.id,{j} FROM _sc s JOIN tag t ON t.normalized_name={q(t.lower())} WHERE s.k={q(c['key'])} ON CONFLICT DO NOTHING;")
    o.append("COMMIT;"); return "\n".join(o)+"\n"

def purge(b):
    pat=q(f"synthetic.{b}.%@{DOMAIN}"); return f"BEGIN;\nDELETE FROM crew WHERE owner_id IN(SELECT id FROM user_account WHERE email LIKE {pat});\nDELETE FROM journey_post WHERE author_id IN(SELECT id FROM user_account WHERE email LIKE {pat});\nDELETE FROM user_account WHERE email LIKE {pat};\nCOMMIT;\n"

def main():
    a=argparse.ArgumentParser(); a.add_argument('--seed',type=int,default=20260825); a.add_argument('--batch-id',default='demo-v1'); a.add_argument('--users',type=int,default=180); a.add_argument('--posts',type=int,default=1800); a.add_argument('--crews',type=int,default=120); a.add_argument('--anchor',default=ANCHOR.isoformat()); a.add_argument('--output-dir',default='build/synthetic-corpus'); x=a.parse_args()
    if x.users<10 or x.posts<1 or x.crews<0: a.error('users>=10, posts>=1, crews>=0 required')
    if not re.fullmatch(r'[a-z0-9][a-z0-9-]{0,30}',x.batch_id): a.error('invalid batch-id')
    anchor=datetime.fromisoformat(x.anchor); r=random.Random(x.seed); us=users(r,x.batch_id,x.users); ps=posts(r,x.batch_id,us,x.posts,anchor); cs=crews(r,x.batch_id,us,x.crews,anchor); validate(us,ps,cs); out=Path(x.output_dir); out.mkdir(parents=True,exist_ok=True); (out/'seed.sql').write_text(render_sql(x.batch_id,us,ps,cs,anchor),encoding='utf-8'); (out/'purge.sql').write_text(purge(x.batch_id),encoding='utf-8'); manifest=dict(schemaVersion=1,batchId=x.batch_id,seed=x.seed,anchor=anchor.isoformat(),counts=dict(users=len(us),posts=len(ps),crews=len(cs)),destinations=[asdict(d) for d in DS],imageProviders=dict(postAndCrew='LoremFlickr CC placeholder',avatars='DiceBear 10.x'),users=us,posts=ps,crews=cs); (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8'); print(f"generated {manifest['counts']} -> {out}")
if __name__=='__main__': main()
