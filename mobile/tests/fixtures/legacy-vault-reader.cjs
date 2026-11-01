"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = legacyRead;
// Frozen validator from 6d8f025: proves optional account chat data remains
// readable by the pre-messaging v1 reader. Do not replace with the new validator.
function legacyRead(value, namespace, utf8, MAX_VAULT_BYTES) {
    const fail = (code) => { throw Error(code); };
    const plain = (v) => !!v && typeof v === 'object' && !Array.isArray(v) && !Object.keys(v).some(k => ['__proto__', 'constructor', 'prototype'].includes(k));
    function validate(value) {
        const invalid = () => fail('TELEGRAM_INVALID_VAULT');
        const uid = (v) => typeof v === 'string' && /^[a-f0-9]{32}$/.test(v);
        const count = (v) => Number.isSafeInteger(v) && v >= 0;
        const image = (v) => typeof v === 'string' &&
            v.length <= Math.ceil((128 * 1024) / 3) * 4 + 32 &&
            /^data:image\/(jpeg|png|webp);base64,[a-zA-Z0-9+/=]+$/.test(v);
        const date = (v) => typeof v === 'string' && Number.isFinite(Date.parse(v));
        if (!plain(value))
            return invalid();
        if (value.app !== 'nabd-direct-telegram' ||
            value.version !== 1 ||
            value.namespace !== namespace ||
            !count(value.revision) ||
            !uid(value.writeId) ||
            !(value.parent === null || (count(value.parent) && value.parent > 0)) ||
            !plain(value.users) ||
            !plain(value.posts) ||
            !Array.isArray(value.friendships))
            return invalid();
        if (utf8(JSON.stringify(value)).length > MAX_VAULT_BYTES)
            fail('TELEGRAM_VAULT_FULL');
        if (Object.keys(value).some((key) => ![
            'app',
            'version',
            'namespace',
            'revision',
            'writeId',
            'parent',
            'users',
            'posts',
            'friendships',
        ].includes(key)))
            return invalid();
        const handles = new Set();
        const emails = new Set();
        for (const [key, user] of Object.entries(value.users)) {
            if (!uid(key) ||
                !plain(user) ||
                !plain(user.profile) ||
                user.profile.id !== key ||
                typeof user.profile.name !== 'string' ||
                user.profile.name.length < 2 ||
                user.profile.name.length > 40 ||
                typeof user.profile.email !== 'string' ||
                user.profile.email.length > 254 ||
                !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(user.profile.email) ||
                user.profile.email !== user.profile.email.toLowerCase() ||
                typeof user.profile.username !== 'string' ||
                !/^[a-z][a-z0-9_]{2,39}$/.test(user.profile.username) ||
                !/^[a-f0-9]{32}$/.test(user.salt) ||
                !/^[a-f0-9]{64}$/.test(user.passwordHash) ||
                !plain(user.sessions))
                return invalid();
            if (typeof user.profile.color !== 'string' ||
                !/^#[a-fA-F0-9]{6}$/.test(user.profile.color) ||
                typeof user.profile.initial !== 'string' ||
                (user.profile.bio != null &&
                    (typeof user.profile.bio !== 'string' ||
                        user.profile.bio.length > 160)) ||
                (user.profile.avatarUrl != null && !image(user.profile.avatarUrl)))
                return invalid();
            if (Object.entries(user.sessions).some(([key, expiry]) => !/^[a-f0-9]{64}$/.test(key) || !count(expiry)))
                return invalid();
            if (handles.has(user.profile.username) || emails.has(user.profile.email))
                return invalid();
            handles.add(user.profile.username);
            emails.add(user.profile.email);
        }
        const exists = (key) => uid(key) && Object.prototype.hasOwnProperty.call(value.users, key);
        for (const pair of value.friendships)
            if (!Array.isArray(pair) ||
                pair.length !== 2 ||
                pair[0] === pair[1] ||
                pair.some((key) => !exists(key)))
                return invalid();
        const kinds = [
            'like',
            'love',
            'haha',
            'wow',
            'sad',
            'angry',
        ];
        for (const [pid, stored] of Object.entries(value.posts)) {
            if (!uid(pid) ||
                !plain(stored) ||
                !plain(stored.post) ||
                stored.post.id !== pid ||
                !exists(stored.post.author?.id) ||
                !plain(stored.votes) ||
                !plain(stored.commentLikes) ||
                !Array.isArray(stored.post.comments) ||
                typeof stored.post.text !== 'string' ||
                stored.post.text.length > 5000 ||
                stored.post.privacy !== 'public' ||
                !date(stored.post.createdAt) ||
                !plain(stored.post.reactions) ||
                !count(stored.post.shares) ||
                stored.post.commentsCount !== stored.post.comments.length)
                return invalid();
            if (Object.entries(stored.votes).some(([key, vote]) => !exists(key) || !kinds.includes(vote)) ||
                kinds.some((kind) => !count(stored.post.reactions[kind]) ||
                    stored.post.reactions[kind] !==
                        Object.values(stored.votes).filter((v) => v === kind).length))
                return invalid();
            const media = stored.post.media;
            if (media &&
                (!plain(media) ||
                    !uid(media.id) ||
                    media.kind !== 'image' ||
                    !image(media.uri) ||
                    !Number.isFinite(media.aspectRatio) ||
                    media.aspectRatio <= 0 ||
                    !count(media.width) ||
                    !count(media.height)))
                return invalid();
            const seen = new Set();
            for (const comment of stored.post.comments) {
                if (!plain(comment) ||
                    !uid(comment.id) ||
                    seen.has(comment.id) ||
                    !exists(comment.author?.id) ||
                    typeof comment.text !== 'string' ||
                    comment.text.length > 2000 ||
                    !date(comment.createdAt) ||
                    !count(comment.likes))
                    return invalid();
                seen.add(comment.id);
                const likes = stored.commentLikes[comment.id] ?? [];
                if (!Array.isArray(likes) ||
                    new Set(likes).size !== likes.length ||
                    likes.some((key) => !exists(key)) ||
                    likes.length !== comment.likes)
                    return invalid();
            }
            if (Object.keys(stored.commentLikes).some((key) => !seen.has(key)))
                return invalid();
        }
        return value;
    }
    return validate(value);
}
