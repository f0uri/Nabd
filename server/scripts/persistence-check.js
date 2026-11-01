const EP='http://localhost:4000/graphql';
async function gql(query, variables, token){
  const r=await fetch(EP,{method:'POST',headers:{'content-type':'application/json',...(token?{authorization:'Bearer '+token}:{})},body:JSON.stringify({query,variables})});
  const j=await r.json(); if(j.errors) throw new Error(j.errors[0].message); return j.data;
}
async function upload(query, variables, filePath, token){
  const fs=await import('node:fs');
  const fd=new FormData();
  fd.append('operations', JSON.stringify({query, variables:{...variables, file:null}}));
  fd.append('map', JSON.stringify({0:['variables.file']}));
  fd.append('0', new Blob([fs.readFileSync(filePath)],{type:'image/png'}), 'photo.png');
  const r=await fetch(EP,{method:'POST',body:fd,headers:{...(token?{authorization:'Bearer '+token}:{})}});
  const j=await r.json(); if(j.errors) throw new Error(j.errors[0].message); return j.data;
}
const stamp = process.argv[2] || 'run1';

/** كيتسجّل إلا ما كانش، وكيدخل إلا كان — باش الاختبار يتعاود بلا مسح القاعدة */
async function ensureUser(name, email) {
  try {
    return (await gql(`mutation($n:String!,$e:String!,$p:String!){register(name:$n,email:$e,password:$p){token user{id name avatarUrl}}}`, { n: name, e: email, p: 'nabd1234' })).register;
  } catch (error) {
    if (String(error.message) !== 'EMAIL_TAKEN') throw error;
    return (await gql('mutation($e:String!){login(email:$e,password:"nabd1234"){token user{id name avatarUrl}}}', { e: email })).login;
  }
}

(async()=>{
  if (stamp === 'check') {
    // بعد إعادة التشغيل: نتأكدو أن كلشي مازال موجود
    const l = await gql('mutation{login(email:"yassine@nabd.app",password:"nabd1234"){token user{name avatarUrl postsCount friendsCount}}}');
    console.log('PERSIST login →', JSON.stringify(l.login.user));
    const tok = l.login.token;
    const f = await gql('{feed(limit:10){posts{id text author{name} commentsCount reactions{like love}}} }', undefined, tok);
    console.log('PERSIST feed →', JSON.stringify(f.feed.posts));
    const fr = await gql('{friends{name} }', undefined, tok);
    console.log('PERSIST friends →', JSON.stringify(fr.friends));
    const n = await gql('{notifications{type text actor{name}} unreadNotificationsCount}', undefined, tok);
    console.log('PERSIST notifications →', JSON.stringify(n));
    return;
  }

  const a = { register: await ensureUser('ياسين المرابط', 'yassine@nabd.app') };
  const b = { register: await ensureUser('لبنى الفاسي', 'loubna@nabd.app') };
  console.log('REGISTER/LOGIN →', a.register.user.name, '/', b.register.user.name);
  const tok = a.register.token, tok2 = b.register.token;

  const up = await upload(`mutation($name:String,$bio:String,$file:File){updateProfile(name:$name,bio:$bio,file:$file){name bio avatarUrl}}`, {name:'ياسين المرابط', bio:'مطور ويب · أكادير'}, '/tmp/me.png', tok);
  console.log('AVATAR (بعد التسجيل) →', JSON.stringify(up.updateProfile));

  const post = await gql(`mutation($t:String!,$p:Privacy){createPost(text:$t,privacy:$p){id text author{name avatarUrl} privacy}}`,{t:'أول منشور محفوظ فـ PostgreSQL',p:'public'},tok);
  console.log('POST →', JSON.stringify(post.createPost));

  const post2 = await gql(`mutation($t:String!,$p:Privacy){createPost(text:$t,privacy:$p){id privacy}}`,{t:'منشور للأصدقاء فقط',p:'friends'},tok);
  console.log('POST2 (أصدقاء) →', JSON.stringify(post2.createPost));

  await gql(`mutation($id:ID!){follow(userId:$id){name isFriend}}`,{id:b.register.user.id},tok);
  console.log('FOLLOW →', JSON.stringify((await gql('{me{friendsCount}}',undefined,tok)).me));

  const c = await gql(`mutation($p:ID!,$t:String!){addComment(postId:$p,text:$t){id text author{name}}}`,{p:post.createPost.id,t:'تعليق محفوظ'},tok2);
  console.log('COMMENT →', JSON.stringify(c.addComment));
  const lk = await gql(`mutation($p:ID!,$c:ID!){likeComment(postId:$p,commentId:$c){likes likedByMe}}`,{p:post.createPost.id,c:c.addComment.id},tok);
  console.log('COMMENT LIKE →', JSON.stringify(lk.likeComment));
  const r = await gql(`mutation($p:ID!){react(postId:$p,reaction:love){reactions{like love} myReaction}}`,{p:post.createPost.id},tok2);
  console.log('REACT →', JSON.stringify(r.react));

  const feed = await gql('{feed(limit:10){posts{text author{name} commentsCount reactions{like love}} nextCursor}}',undefined,tok);
  console.log('FEED →', JSON.stringify(feed.feed.posts));

  const feed2 = await gql('{feed(limit:10){posts{text privacy}}}',undefined,tok2);
  console.log('FEED (لبنى — صديقة) →', JSON.stringify(feed2.feed.posts.map(p=>p.text)));
  const anon = await gql('{feed(limit:10){posts{text privacy}}}');
  console.log('FEED (بلا توكن — غير العام) →', JSON.stringify(anon.feed.posts));
  const n = await gql('{notifications{type text actor{name}} unreadNotificationsCount}',undefined,tok);
  console.log('NOTIFS →', JSON.stringify(n));
})().catch(e=>{console.error('FAIL', e.message); process.exit(1)});
