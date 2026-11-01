const EP='http://localhost:4000/graphql';
const q=async(query,variables,token)=>{const r=await fetch(EP,{method:'POST',headers:{'content-type':'application/json',...(token?{authorization:'Bearer '+token}:{})},body:JSON.stringify({query,variables})});const j=await r.json();if(j.errors)throw new Error(j.errors[0].message);return j.data;}
(async()=>{
  const l=await q('mutation{login(email:"yassine@nabd.app",password:"nabd1234"){token}}');
  const t=l.login.token;
  const ops=[
   ['me','{me{name email bio avatarUrl friendsCount postsCount isFriend isMe}}'],
   ['users','{users(search:"لبنى"){name isFriend}}'],
   ['friends','{friends{name avatarUrl}}'],
   ['suggestions','{suggestions{name}}'],
   ['notifications','{notifications{type text actor{name}} unreadNotificationsCount}'],
   ['storage','{storage{provider configured}}'],
   ['feed','{feed(limit:2){nextCursor posts{id}}}'],
  ];
  for(const [name,qy] of ops){
    try{ const d=await q(qy,undefined,t); console.log('✔', name, '→', JSON.stringify(d).slice(0,140)); }
    catch(e){ console.log('✘', name, e.message); }
  }
  const uid = (await q('{me{id}}',undefined,t)).me.id;
  const up = await q('{userPosts(userId:"'+uid+'",limit:5){text commentsCount myReaction}}',undefined,t);
  console.log('✔ userPosts →', JSON.stringify(up.userPosts));
  const pid = (await q('{feed(limit:1){posts{id}}}',undefined,t)).feed.posts[0].id;
  const post = await q('{post(id:"'+pid+'"){text comments{text likes likedByMe author{name}} reactions{total}}}',undefined,t);
  console.log('✔ post →', JSON.stringify(post.post));
  const mr = await q('mutation{markNotificationsRead} ',undefined,t);
  console.log('✔ markNotificationsRead →', JSON.stringify(mr));
  const unread = await q('{unreadNotificationsCount}',undefined,t);
  console.log('✔ unread بعد القراءة →', unread.unreadNotificationsCount);
})().catch(e=>console.error('FAIL',e.message));
