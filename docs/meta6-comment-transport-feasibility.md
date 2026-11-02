# Meta6: comments feasibility checkpoint

Reviewed: 2026-11-02. Scope: an authorized, low-volume experiment on a test
post owned/managed by the tester. Nabd, Meta6 UI, credentials, and releases are
unchanged. This is a source/documentation review, not a live carrier/API test.

## Result: documented capability; live API test blocked

Meta documents comment management through the Pages API, including
`GET /{object-id}/comments` and `POST /{object-id}/comments`. The official
Python Business SDK also generates `Post.get_comments` and `Post.create_comment`
with GET and POST on the `/comments` edge.

The official Pages documentation requires a Page access token and the
appropriate permissions. Relevant permissions include `pages_manage_engagement`
for comment management, `pages_read_engagement` for Page content, and
`pages_read_user_content` for user-generated content. Exact requirements depend
on the operation, content and account/app access; they must be checked against
the chosen supported Graph API version and actual token before a live test.
An existing Messenger token is not assumed to include comment permissions.

**This does not remove the tester's blocked developer-account/token step.**
A successful Page comment write also does not demonstrate independent identities
for arbitrary Nabd users. The documented Page token acts as the Page; sharing
that credential with application users is not an acceptable production design.

No live request was sent: no usable Page token, confirmed test post Graph ID,
or authenticated Meta session is available to the agent. No comment was
published, read, edited or deleted, and no account was accessed on the tester's
behalf. No new APK is warranted at this checkpoint.

## Optional phone-only manual diagnostic (no developer token)

This is a Facebook UI test, **not** proof that a third-party app can do it:

1. Use a harmless test post on a Page you control, with visibility appropriate
   for a non-private test. Do not use another person's post or private data.
2. With Wi-Fi off and only the intended *6 allowance in use, publish one
   non-sensitive comment manually in Facebook, e.g. `اختبار 1`.
3. Reload the post and check that the comment remains. If using a second
   consenting test participant, have them verify it and reply once; reload again.
4. Record the network conditions and observed result, without credentials,
   account recovery links or authentication codes. Merely seeing an optimistic
   local comment before a reload is insufficient evidence.

This can establish Facebook UI behavior for the reported connection. It does
not establish API access, free billing, delivery to Nabd, or private messaging.
No automatic posting, repeated retries, scraping, session-cookie collection,
or bypass of Meta's account verification is part of this diagnostic.

## Preconditions for any later API experiment

- An authorized Page token with the needed granted permissions, entered only
  on the tester's device; not shared in chat, GitHub or a distributed APK.
- A test post's actual Graph object ID and confirmation that the token is
  authorized for that Page/post. A personal profile URL is not proof of this.
- At most one explicitly initiated test comment, then read-back of its returned
  nonempty comment ID and matching harmless text. No scheduled comment spam.
- A second participant/reply check if testing two-way behavior. No automatic
  deletion or moderation of unrelated comments.
- Record live Meta errors as failures/unknown outcomes; do not interpret HTTP
  reachability, unit mocks or SDK method existence as delivery proof.

Even a successful API round-trip is only a Page-comment experiment. Comment
visibility, participant metadata, authorization boundaries and a viable official
use case must be resolved before considering a messaging product. Encryption of
text alone would not turn the post/comment system into private Nabd transport.

## Sources and limitations

- Official Pages API overview (authentication, permissions and comment management):
  https://developers.facebook.com/documentation/pages-api
- Official Pages API documentation:
  https://developers.facebook.com/docs/pages-api/
- Official generated SDK, pinned to the source revision inspected:
  https://github.com/facebook/facebook-python-business-sdk/blob/efd8423a2e595ea8d4c04eb824ce113f2f1d68cd/facebook_business/adobjects/post.py

Meta documentation was reviewed through search-extracted official results;
the SDK source was retrieved through GitHub. SDK inspection verifies method
shape only, not permission grants, current server acceptance, policy approval
for a chat use case, or operation under *6. No unsupported API version guarantee
or claim that personal Facebook identities can be freely automated is made.

## Assumptions — three lines
1. The tester controls the Page/post and uses only non-sensitive test text.
2. *6-only conditions and billing are user-reported, not verified by the agent.
3. No Nabd integration or new APK before live authorization and feasibility are established.
