// اختبار دردشة شامل: تسجيل حسابين + محادثة + رسائل + إشعارات + أمان العضوية.
// ملاحظة: كيتعاود بلا مشكل — إلا كان الحساب كيدخل بيه بدل ما يعاود يسجّلو.
// التشغيل: npm run smoke:chat   (الخادم خاصو يكون خدام على http://localhost:4000)
const EP='http://localhost:4000/graphql';
const q=async(query,variables,token)=>{const r=await fetch(EP,{method:'POST',headers:{'content-type':'application/json',...(token?{authorization:'Bearer '+token}:{})},body:JSON.stringify({query,variables})});const j=await r.json();if(j.errors)throw new Error(j.errors[0].message);return j.data;}
// كيتسجّل إلا ما كانش، وكيدخل إلا كان (باش الاختبار يتعاود بلا ما نمسحو data/pg)
const ensureUser=async(name,email)=>{
  try{
    const r=await q('mutation($n:String!,$e:String!){register(name:$n,email:$e,password:"nabd1234"){token user{id}}}',{n:name,e:email});
    return r.register;
  }catch(error){
    if(String(error.message)!=='EMAIL_TAKEN') throw error;
    const r=await q('mutation($e:String!){login(email:$e,password:"nabd1234"){token user{id}}}',{e:email});
    return r.login;
  }
};
(async()=>{
  const a={register:await ensureUser('ياسين','y2@nabd.app')};
  const b={register:await ensureUser('لبنى','l2@nabd.app')};
  const ta=a.register.token, tb=b.register.token, idb=b.register.user.id;
  console.log('accounts →', a.register.user.id, idb);

  const cv=await q('mutation($u:ID!){startConversation(userId:$u){id other{name} unreadCount}}',{u:idb},ta);
  console.log('START →', JSON.stringify(cv.startConversation));
  const cvId=cv.startConversation.id;

  const m1=await q('mutation($c:ID!,$t:String!){sendMessage(conversationId:$c,text:$t){id text createdAt read}}',{c:cvId,t:'سلام لبنى، واش وصلاتك الرسالة؟'},ta);
  console.log('SEND 1 →', JSON.stringify(m1.sendMessage));
  const m2=await q('mutation($c:ID!,$t:String!){sendMessage(conversationId:$c,text:$t){id text}}',{c:cvId,t:'وصلات، مرحبا!'},tb);
  console.log('SEND 2 →', JSON.stringify(m2.sendMessage));

  const convs=await q('{conversations{id other{name} unreadCount lastMessage{text senderId}}}',undefined,tb);
  console.log('CONVS (لبنى) →', JSON.stringify(convs.conversations));
  const unread=await q('{unreadMessagesCount}',undefined,tb);
  console.log('UNREAD (لبنى) →', unread.unreadMessagesCount);

  const msgs=await q('{messages(conversationId:"'+cvId+'",limit:10){messages{text read} nextCursor}}',undefined,tb);
  console.log('MESSAGES →', JSON.stringify(msgs.messages));

  const rd=await q('mutation($c:ID!){markConversationRead(conversationId:$c)}',{c:cvId},tb);
  console.log('MARK READ →', JSON.stringify(rd), '| unread بعد:', (await q('{unreadMessagesCount}',undefined,tb)).unreadMessagesCount);

  const notif=await q('{notifications{type text actor{name} } }',undefined,tb);
  console.log('NOTIFS →', JSON.stringify(notif.notifications));
  // بحث
  const search=await q('{conversations(search:"ياسين"){other{name}}}',undefined,tb);
  console.log('SEARCH →', JSON.stringify(search.conversations));
  // محاولة قراءة محادثة ماشي ديالي
  const c={register:await ensureUser('غريب','g@nabd.app')};
  try{ await q('{messages(conversationId:"'+cvId+'"){messages{text}}}',undefined,c.register.token); console.log('SECURITY ✘ كيقدر يشوف'); }
  catch(e){ console.log('SECURITY ✓ مرفوض:', e.message); }
})().catch(e=>{console.error('FAIL',e.message);process.exit(1)});
